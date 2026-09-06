package app.vault.workspace.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import app.vault.workspace.crypto.VaultCrypto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/**
 * Background remux of unseekable WEB-DL (and similar) into a seekable MPEG-4 under
 * [PlaybackPlaintextCache]'s playcache dir (`seek_<hash>.mp4`). Wiped on lock via
 * [PlaybackPlaintextCache.wipeAll].
 *
 * Pipeline (v0.4.10+): decrypt VAULT1 → temp `plain_<hash>.bin` via sequential
 * [VaultCrypto.decryptToStream], then [MediaExtractor.setDataSource] on that **file path**
 * + [MediaMuxer] MPEG-4. Avoids OEM-flaky [android.media.MediaDataSource] /
 * [VaultMediaDataSource] remux hangs on long WEB-DLs.
 *
 * Encryption/random-access play is unchanged ([EncryptedDataSource] chunked AES-GCM).
 * Remux only fixes unseekable containers so ExoPlayer gets a real SeekMap.
 *
 * On codec/muxer failure returns null — caller keeps streaming playback (seek limited).
 */
object SeekableRemuxCache {
    private const val TAG = "VaultRemux"
    private const val FILE_PREFIX = "seek_"
    private const val PLAIN_PREFIX = "plain_"
    /** Large enough for high-bitrate H.264/H.265 keyframes (2MB was too small). */
    private const val MAX_SAMPLE_BYTES = 16 * 1024 * 1024

    private val keyLocks = ConcurrentHashMap<String, Mutex>()

    /**
     * @param onProgress optional progress on the calling dispatcher (IO). Values are
     *   overall 0f..1f (decrypt ~0..0.45, mux ~0.45..1). Caller should hop to Main for UI.
     */
    suspend fun getOrRemux(
        context: Context,
        itemKey: String,
        vatFile: File,
        dek: ByteArray,
        @Suppress("UNUSED_PARAMETER") mimeType: String,
        onProgress: ((fraction: Float) -> Unit)? = null,
    ): File? = withContext(Dispatchers.IO) {
        require(itemKey.isNotBlank()) { "itemKey required" }
        val cacheDir = PlaybackPlaintextCache.dir(context)
        cacheDir.mkdirs()
        val dest = remuxFile(cacheDir, itemKey)
        val lock = keyLocks.getOrPut(itemKey) { Mutex() }
        lock.withLock {
            if (dest.exists() && dest.isFile && dest.length() > 0L && !isStale(dest, vatFile)) {
                onProgress?.invoke(1f)
                return@withContext dest
            }
            if (dest.exists()) {
                dest.delete()
            }
            val part = File(cacheDir, "${dest.name}.part")
            val plain = plainTempFile(cacheDir, itemKey)
            try {
                if (part.exists()) part.delete()
                if (plain.exists()) plain.delete()

                decryptVatToPlain(vatFile, dek, plain, onProgress)
                coroutineContext.ensureActive()

                remuxFilePathTo(part, plain, onProgress)
                coroutineContext.ensureActive()

                if (!part.exists() || part.length() <= 0L) {
                    part.delete()
                    return@withContext null
                }
                if (dest.exists()) dest.delete()
                if (!part.renameTo(dest)) {
                    part.copyTo(dest, overwrite = true)
                    part.delete()
                }
                writeStamp(dest, vatFile)
                onProgress?.invoke(1f)
                dest
            } catch (e: CancellationException) {
                try {
                    part.delete()
                } catch (_: Exception) {
                }
                try {
                    if (dest.exists()) dest.delete()
                } catch (_: Exception) {
                }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Remux failed for key=$itemKey: ${e.message}", e)
                try {
                    part.delete()
                } catch (_: Exception) {
                }
                try {
                    if (dest.exists()) dest.delete()
                } catch (_: Exception) {
                }
                null
            } finally {
                try {
                    if (plain.exists()) plain.delete()
                } catch (_: Exception) {
                }
            }
        }
    }

    internal fun remuxFile(cacheDir: File, itemKey: String): File {
        val hash = sha256Hex(itemKey).take(32)
        return File(cacheDir, "$FILE_PREFIX$hash.mp4")
    }

    internal fun plainTempFile(cacheDir: File, itemKey: String): File {
        val hash = sha256Hex(itemKey).take(32)
        return File(cacheDir, "$PLAIN_PREFIX$hash.bin")
    }

    private fun isStale(dest: File, vatFile: File): Boolean {
        val stamp = stampFile(dest)
        if (!stamp.exists()) return false
        return try {
            val expected = "${vatFile.length()}:${VaultCrypto.readHeader(vatFile).plaintextSize}"
            stamp.readText() != expected
        } catch (_: Exception) {
            false
        }
    }

    private fun writeStamp(dest: File, vatFile: File) {
        try {
            val expected = "${vatFile.length()}:${VaultCrypto.readHeader(vatFile).plaintextSize}"
            stampFile(dest).writeText(expected)
        } catch (_: Exception) {
        }
    }

    private fun stampFile(dest: File): File =
        File(dest.parentFile, "${dest.name}.stamp")

    private suspend fun decryptVatToPlain(
        vatFile: File,
        dek: ByteArray,
        plain: File,
        onProgress: ((Float) -> Unit)?,
    ) {
        val header = VaultCrypto.readHeader(vatFile)
        val total = header.plaintextSize.coerceAtLeast(1L)
        FileOutputStream(plain).use { fos ->
            val counting = object : OutputStream() {
                private var written = 0L
                private var lastReported = -1

                override fun write(b: Int) {
                    fos.write(b)
                    written++
                    report()
                }

                override fun write(b: ByteArray, off: Int, len: Int) {
                    fos.write(b, off, len)
                    written += len
                    report()
                }

                override fun flush() {
                    fos.flush()
                }

                private fun report() {
                    val pct = ((written * 100L) / total).toInt().coerceIn(0, 100)
                    if (pct != lastReported) {
                        lastReported = pct
                        // Decrypt is ~0..0.45 of overall remux work.
                        onProgress?.invoke((pct / 100f) * 0.45f)
                    }
                }
            }
            VaultCrypto.decryptToStream(vatFile, dek, counting)
            fos.fd.sync()
        }
        if (plain.length() <= 0L) {
            throw IllegalStateException("Decrypt produced empty plaintext")
        }
        onProgress?.invoke(0.45f)
    }

    /**
     * Remux a plaintext media file (path-based MediaExtractor — reliable on OEMs)
     * into MPEG-4. Only muxes video/ and audio/ MIME tracks.
     */
    private suspend fun remuxFilePathTo(
        outFile: File,
        plainFile: File,
        onProgress: ((Float) -> Unit)?,
    ) {
        val extractor = MediaExtractor()
        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            extractor.setDataSource(plainFile.absolutePath)
            val trackCount = extractor.trackCount
            if (trackCount <= 0) {
                throw IllegalStateException("No tracks in source")
            }
            val indexMap = IntArray(trackCount) { -1 }
            var added = 0
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)?.lowercase() ?: continue
                if (!mime.startsWith("video/") && !mime.startsWith("audio/")) {
                    Log.i(TAG, "Skipping non A/V track $i mime=$mime")
                    continue
                }
                try {
                    indexMap[i] = muxer.addTrack(format)
                    extractor.selectTrack(i)
                    added++
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping track $i mime=$mime: ${e.message}")
                    indexMap[i] = -1
                }
            }
            if (added <= 0) {
                throw IllegalStateException("No video/audio tracks could be muxed")
            }
            muxer.start()

            val buffer = ByteBuffer.allocateDirect(MAX_SAMPLE_BYTES)
            val info = MediaCodec.BufferInfo()
            extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            var durationUs = 0L
            for (i in 0 until trackCount) {
                if (indexMap[i] < 0) continue
                val format = extractor.getTrackFormat(i)
                if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    durationUs = maxOf(durationUs, format.getLong(MediaFormat.KEY_DURATION))
                }
            }
            var lastMuxPct = -1
            var sampleCount = 0L

            while (true) {
                coroutineContext.ensureActive()
                buffer.clear()
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                if (sampleSize > MAX_SAMPLE_BYTES) {
                    throw IllegalStateException("Sample $sampleSize exceeds buffer $MAX_SAMPLE_BYTES")
                }
                val trackIndex = extractor.sampleTrackIndex
                if (trackIndex < 0 || trackIndex >= indexMap.size || indexMap[trackIndex] < 0) {
                    extractor.advance()
                    continue
                }
                info.offset = 0
                info.size = sampleSize
                info.presentationTimeUs = extractor.sampleTime.coerceAtLeast(0L)
                info.flags = extractor.sampleFlags
                buffer.position(0)
                buffer.limit(sampleSize)
                muxer.writeSampleData(indexMap[trackIndex], buffer, info)
                extractor.advance()
                sampleCount++

                if (onProgress != null) {
                    val muxFraction = if (durationUs > 0L) {
                        (info.presentationTimeUs.toDouble() / durationUs.toDouble())
                            .coerceIn(0.0, 1.0)
                    } else {
                        // Unknown duration: gentle asymptotic progress from sample count.
                        (1.0 - 1.0 / (1.0 + sampleCount / 500.0)).coerceIn(0.0, 0.99)
                    }
                    val overall = (0.45 + muxFraction * 0.55).toFloat()
                    val pct = (overall * 100).toInt().coerceIn(45, 99)
                    if (pct != lastMuxPct) {
                        lastMuxPct = pct
                        onProgress(overall)
                    }
                }
            }
            onProgress?.invoke(0.99f)
            Log.i(TAG, "Remux wrote $sampleCount samples → ${outFile.name}")
        } finally {
            try {
                muxer.stop()
            } catch (_: Exception) {
            }
            try {
                muxer.release()
            } catch (_: Exception) {
            }
            try {
                extractor.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun sha256Hex(s: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { b -> "%02x".format(b) }
    }
}
