package app.vault.workspace.media

import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Container/index probe for progressive seek readiness.
 *
 * Encryption does NOT block seek (chunked AES-GCM + EncryptedDataSource). Unseekable
 * WEB-DL / fMP4 (no sidx/mfra) / MPEG-TS need Path B prepare — never seek-time remux.
 */
object MediaContainerProbe {
    private const val TAG = "VaultProbe"

    enum class Kind {
        /** Progressive MP4/MOV with moov before mdat (ExoPlayer can seek). */
        PROGRESSIVE_SEEKABLE_MP4,
        /** MP4 with moov after mdat — cheap faststart fix. */
        MP4_MOOV_AT_END,
        /** Fragmented MP4 (moof) — needs remux to progressive. */
        FRAGMENTED_MP4,
        /** MPEG-TS — needs remux to MP4. */
        MPEG_TS,
        /** Matroska/WebM or other — try remux; may already seek via cues. */
        OTHER,
        UNKNOWN,
    }

    data class ProbeResult(
        val kind: Kind,
        /** True → Path A only; set seekReady without rewrite. */
        val alreadySeekable: Boolean,
        val hasVideoTrack: Boolean = false,
        val durationUs: Long = 0L,
        val detail: String = "",
    )

    /**
     * Pure filesystem sniff (JVM-safe): magic + MP4 top-level boxes.
     * Prefer [probeFile] on device when MediaExtractor is available.
     */
    fun sniffFile(file: File): ProbeResult {
        if (!file.exists() || file.length() < 8L) {
            return ProbeResult(Kind.UNKNOWN, alreadySeekable = false, detail = "empty")
        }
        if (looksLikeMpegTs(file)) {
            return ProbeResult(Kind.MPEG_TS, alreadySeekable = false, detail = "mpeg-ts")
        }
        val layout = Mp4BoxParser.analyze(file)
        if (layout != null) {
            return when {
                layout.hasMoof -> ProbeResult(
                    Kind.FRAGMENTED_MP4,
                    alreadySeekable = false,
                    detail = "fmp4 moof",
                )
                layout.moovOffset >= 0L && layout.mdatOffset >= 0L &&
                    layout.moovOffset < layout.mdatOffset -> ProbeResult(
                    Kind.PROGRESSIVE_SEEKABLE_MP4,
                    alreadySeekable = true,
                    detail = "moov before mdat",
                )
                layout.moovOffset >= 0L && layout.mdatOffset >= 0L &&
                    layout.moovOffset > layout.mdatOffset -> ProbeResult(
                    Kind.MP4_MOOV_AT_END,
                    alreadySeekable = false,
                    detail = "moov after mdat",
                )
                layout.moovOffset >= 0L -> ProbeResult(
                    Kind.PROGRESSIVE_SEEKABLE_MP4,
                    alreadySeekable = true,
                    detail = "moov present",
                )
                else -> ProbeResult(Kind.OTHER, alreadySeekable = false, detail = "mp4-ish no moov")
            }
        }
        // Matroska EBML
        if (startsWith(file, byteArrayOf(0x1A, 0x45.toByte(), 0xDF.toByte(), 0xA3.toByte()))) {
            return ProbeResult(Kind.OTHER, alreadySeekable = false, detail = "matroska")
        }
        return ProbeResult(Kind.OTHER, alreadySeekable = false, detail = "unknown container")
    }

    /**
     * Device probe: sniff + MediaExtractor tracks/duration.
     * If sniff says progressive seekable AND extractor has a video/audio track with
     * duration → alreadySeekable. Fragmented / TS / moov-at-end stay not ready.
     */
    fun probeFile(file: File): ProbeResult {
        val sniff = sniffFile(file)
        val extractorInfo = runCatching { extractTrackInfo(file) }.getOrNull()
        val hasVideo = extractorInfo?.hasVideo == true
        val durationUs = extractorInfo?.durationUs ?: 0L
        return when (sniff.kind) {
            Kind.PROGRESSIVE_SEEKABLE_MP4 -> {
                // Trust moov-before-mdat progressive; require at least one A/V track if extractor works.
                val ok = extractorInfo == null || extractorInfo.hasAv
                ProbeResult(
                    kind = sniff.kind,
                    alreadySeekable = ok,
                    hasVideoTrack = hasVideo,
                    durationUs = durationUs,
                    detail = sniff.detail,
                )
            }
            else -> sniff.copy(
                hasVideoTrack = hasVideo,
                durationUs = durationUs,
            )
        }.also {
            Log.i(
                TAG,
                "probe ${file.name} kind=${it.kind} seekable=${it.alreadySeekable} " +
                    "video=$hasVideo durUs=$durationUs (${it.detail})",
            )
        }
    }

    private data class ExtractorInfo(
        val hasVideo: Boolean,
        val hasAudio: Boolean,
        val durationUs: Long,
    ) {
        val hasAv: Boolean get() = hasVideo || hasAudio
    }

    private fun extractTrackInfo(file: File): ExtractorInfo {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            var hasVideo = false
            var hasAudio = false
            var durationUs = 0L
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)?.lowercase() ?: continue
                when {
                    mime.startsWith("video/") -> hasVideo = true
                    mime.startsWith("audio/") -> hasAudio = true
                }
                if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    durationUs = maxOf(durationUs, format.getLong(MediaFormat.KEY_DURATION))
                }
            }
            return ExtractorInfo(hasVideo, hasAudio, durationUs)
        } finally {
            try {
                extractor.release()
            } catch (_: Exception) {
            }
        }
    }

    /** MPEG-TS: 0x47 sync every 188 bytes for several packets. */
    fun looksLikeMpegTs(file: File): Boolean {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                if (raf.length() < 188L * 3) return false
                val buf = ByteArray(188 * 5)
                raf.seek(0)
                val n = raf.read(buf)
                if (n < 188 * 3) return false
                var hits = 0
                var i = 0
                while (i + 188 <= n) {
                    if (buf[i] == 0x47.toByte()) hits++ else return false
                    i += 188
                }
                hits >= 3
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun startsWith(file: File, magic: ByteArray): Boolean {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val buf = ByteArray(magic.size)
                raf.readFully(buf)
                buf.contentEquals(magic)
            }
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * Minimal ISO BMFF top-level box walker (JVM-safe).
 */
object Mp4BoxParser {
    data class Layout(
        val moovOffset: Long,
        val moovSize: Long,
        val mdatOffset: Long,
        val mdatSize: Long,
        val hasMoof: Boolean,
        val ftypOffset: Long,
        val ftypSize: Long,
    )

    data class Box(
        val offset: Long,
        val size: Long,
        val type: String,
    )

    fun analyze(file: File): Layout? {
        val boxes = readTopLevelBoxes(file) ?: return null
        if (boxes.isEmpty()) return null
        // Require an ftyp or moov to treat as MP4/MOV family.
        val hasFtyp = boxes.any { it.type == "ftyp" }
        val moov = boxes.firstOrNull { it.type == "moov" }
        val mdat = boxes.firstOrNull { it.type == "mdat" }
        if (!hasFtyp && moov == null) return null
        val ftyp = boxes.firstOrNull { it.type == "ftyp" }
        return Layout(
            moovOffset = moov?.offset ?: -1L,
            moovSize = moov?.size ?: 0L,
            mdatOffset = mdat?.offset ?: -1L,
            mdatSize = mdat?.size ?: 0L,
            hasMoof = boxes.any { it.type == "moof" },
            ftypOffset = ftyp?.offset ?: -1L,
            ftypSize = ftyp?.size ?: 0L,
        )
    }

    fun readTopLevelBoxes(file: File, maxBoxes: Int = 64): List<Box>? {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val len = raf.length()
                val out = mutableListOf<Box>()
                var pos = 0L
                while (pos + 8 <= len && out.size < maxBoxes) {
                    raf.seek(pos)
                    val hdr = ByteArray(8)
                    raf.readFully(hdr)
                    val bb = ByteBuffer.wrap(hdr).order(ByteOrder.BIG_ENDIAN)
                    var size = bb.int.toLong() and 0xFFFFFFFFL
                    val typeBytes = ByteArray(4)
                    System.arraycopy(hdr, 4, typeBytes, 0, 4)
                    val type = typeBytes.toString(Charsets.US_ASCII)
                    var headerLen = 8L
                    if (size == 1L) {
                        if (pos + 16 > len) break
                        val ext = ByteArray(8)
                        raf.readFully(ext)
                        size = ByteBuffer.wrap(ext).order(ByteOrder.BIG_ENDIAN).long
                        headerLen = 16L
                    } else if (size == 0L) {
                        size = len - pos
                    }
                    if (size < headerLen || pos + size > len) break
                    // Skip non-ascii garbage (not an MP4)
                    if (!type.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == ' ' }) {
                        return if (out.isEmpty()) null else out
                    }
                    out.add(Box(pos, size, type.trim()))
                    pos += size
                }
                out
            }
        } catch (_: Exception) {
            null
        }
    }
}
