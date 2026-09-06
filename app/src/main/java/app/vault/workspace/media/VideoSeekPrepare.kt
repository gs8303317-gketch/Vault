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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/**
 * Path B — one-time background prepare for unseekable video containers.
 *
 * Trigger at **import** and/or **first play open**. Never on seek scrub.
 * Cheapest repair first: moov faststart → MediaExtractor/Muxer A/V remux →
 * re-encrypt progressive MP4 into the item's `.vat` (same DEK wrap / item id).
 * Wipe private temps (overwrite+delete). Caller sets `seekReady=true` on success.
 */
object VideoSeekPrepare {
    private const val TAG = "VaultSeekPrep"
    private const val DIR_NAME = "seekprep"
    private const val MAX_SAMPLE_BYTES = 16 * 1024 * 1024
    private const val WIPE_CAP_BYTES = 8L * 1024L * 1024L

    private val keyLocks = ConcurrentHashMap<String, Mutex>()
    /** In-flight progress 0f..1f for UI chips (not tied to scrub). */
    private val progressMap = ConcurrentHashMap<String, Float>()

    fun dir(context: Context): File =
        File(context.cacheDir, DIR_NAME).also { it.mkdirs() }

    fun currentProgress(itemId: String): Float? = progressMap[itemId]

    fun wipeAll(context: Context) {
        wipeDir(dir(context))
    }

    /**
     * Decrypt → probe → optional repair → re-encrypt into [vatFile] atomically.
     * If already seekable after decrypt/probe, returns [Outcome.AlreadyReady] without rewrite.
     */
    suspend fun prepare(
        context: Context,
        itemId: String,
        vatFile: File,
        dek: ByteArray,
        onProgress: ((Float) -> Unit)? = null,
    ): Outcome = withContext(Dispatchers.IO) {
        require(itemId.isNotBlank())
        val lock = keyLocks.getOrPut(itemId) { Mutex() }
        lock.withLock {
            val work = File(dir(context), itemId).also { it.mkdirs() }
            val plain = File(work, "plain.bin")
            val repaired = File(work, "repaired.mp4")
            val newVat = File(work, "prepared.vat")
            try {
                report(itemId, 0f, onProgress)
                decryptVatToPlain(vatFile, dek, plain) { f ->
                    report(itemId, f * 0.35f, onProgress)
                }
                coroutineContext.ensureActive()
                report(itemId, 0.35f, onProgress)

                val probe = MediaContainerProbe.probeFile(plain)
                if (probe.alreadySeekable) {
                    report(itemId, 1f, onProgress)
                    Log.i(TAG, "Item $itemId already seekable (${probe.detail})")
                    return@withLock Outcome.AlreadyReady
                }

                val prepared = repairToProgressiveMp4(plain, repaired, probe) { f ->
                    report(itemId, 0.35f + f * 0.45f, onProgress)
                }
                coroutineContext.ensureActive()
                if (prepared == null || !prepared.exists() || prepared.length() <= 0L) {
                    Log.e(TAG, "Prepare repair failed for $itemId kind=${probe.kind}")
                    return@withLock Outcome.Failed("repair failed (${probe.kind})")
                }

                // Confirm repaired container is progressive seekable.
                val after = MediaContainerProbe.sniffFile(prepared)
                if (!after.alreadySeekable && after.kind != MediaContainerProbe.Kind.PROGRESSIVE_SEEKABLE_MP4) {
                    // Remux output is MPEG-4 from MediaMuxer — treat as seekable progressive.
                    val remuxLayout = Mp4BoxParser.analyze(prepared)
                    if (remuxLayout == null || remuxLayout.hasMoof) {
                        Log.e(TAG, "Repaired file still unseekable for $itemId")
                        return@withLock Outcome.Failed("repaired still unseekable")
                    }
                }
                report(itemId, 0.80f, onProgress)

                // Re-encrypt with same DEK into temp, then atomic rename over blob.
                FileInputStream(prepared).use { input ->
                    VaultCrypto.encryptStream(
                        plaintext = input,
                        plaintextSize = prepared.length(),
                        dek = dek,
                        destVat = newVat,
                        tmpDir = work,
                    )
                }
                coroutineContext.ensureActive()
                report(itemId, 0.95f, onProgress)

                // Atomic replace of vault blob (encryptStream already renamed onto newVat).
                if (!newVat.exists() || newVat.length() <= 0L) {
                    return@withLock Outcome.Failed("re-encrypt empty")
                }
                val bak = File(work, "old.vat.bak")
                try {
                    if (vatFile.exists()) {
                        vatFile.copyTo(bak, overwrite = true)
                    }
                    if (vatFile.exists() && !vatFile.delete()) {
                        throw IllegalStateException("Cannot replace vault blob")
                    }
                    if (!newVat.renameTo(vatFile)) {
                        newVat.copyTo(vatFile, overwrite = true)
                        wipeAndDelete(newVat)
                    }
                    wipeAndDelete(bak)
                } catch (e: Exception) {
                    // Best-effort restore
                    if (bak.exists() && (!vatFile.exists() || vatFile.length() <= 0L)) {
                        bak.copyTo(vatFile, overwrite = true)
                    }
                    throw e
                }

                report(itemId, 1f, onProgress)
                Log.i(TAG, "Prepared seekable blob for $itemId (${prepared.length()} bytes plaintext)")
                Outcome.Rewritten
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Prepare failed for $itemId: ${e.message}", e)
                Outcome.Failed(e.message ?: "prepare failed")
            } finally {
                progressMap.remove(itemId)
                wipeDir(work)
            }
        }
    }

    sealed class Outcome {
        data object AlreadyReady : Outcome()
        data object Rewritten : Outcome()
        data class Failed(val reason: String) : Outcome()
    }

    private fun report(itemId: String, fraction: Float, cb: ((Float) -> Unit)?) {
        val f = fraction.coerceIn(0f, 1f)
        progressMap[itemId] = f
        cb?.invoke(f)
    }

    private suspend fun decryptVatToPlain(
        vatFile: File,
        dek: ByteArray,
        plain: File,
        onProgress: (Float) -> Unit,
    ) {
        val header = VaultCrypto.readHeader(vatFile)
        val total = header.plaintextSize.coerceAtLeast(1L)
        if (plain.exists()) wipeAndDelete(plain)
        FileOutputStream(plain).use { fos ->
            val counting = object : OutputStream() {
                private var written = 0L
                private var lastPct = -1
                override fun write(b: Int) {
                    fos.write(b)
                    written++
                    maybe()
                }
                override fun write(b: ByteArray, off: Int, len: Int) {
                    fos.write(b, off, len)
                    written += len
                    maybe()
                }
                override fun flush() { fos.flush() }
                private fun maybe() {
                    val pct = ((written * 100L) / total).toInt().coerceIn(0, 100)
                    if (pct != lastPct) {
                        lastPct = pct
                        onProgress(pct / 100f)
                    }
                }
            }
            VaultCrypto.decryptToStream(vatFile, dek, counting)
            fos.fd.sync()
        }
        if (plain.length() <= 0L) throw IllegalStateException("Decrypt empty")
    }

    /**
     * Cheapest repair first: faststart → MediaExtractor+Muxer A/V remux.
     */
    private suspend fun repairToProgressiveMp4(
        plain: File,
        outMp4: File,
        probe: MediaContainerProbe.ProbeResult,
        onProgress: (Float) -> Unit,
    ): File? {
        if (outMp4.exists()) wipeAndDelete(outMp4)

        if (probe.kind == MediaContainerProbe.Kind.MP4_MOOV_AT_END) {
            onProgress(0.1f)
            val ok = Mp4Faststart.moveMoovToStart(plain, outMp4)
            if (ok && outMp4.exists() && outMp4.length() > 0L) {
                val check = MediaContainerProbe.sniffFile(outMp4)
                if (check.alreadySeekable ||
                    (Mp4BoxParser.analyze(outMp4)?.let { it.moovOffset >= 0 && it.moovOffset < it.mdatOffset && !it.hasMoof } == true)
                ) {
                    onProgress(1f)
                    Log.i(TAG, "Faststart succeeded")
                    return outMp4
                }
            }
            wipeAndDelete(outMp4)
            Log.w(TAG, "Faststart failed; falling back to remux")
        }

        // Remux A/V to progressive MPEG-4 (fMP4, TS, matroska, failed faststart, …)
        return try {
            remuxAvToMp4(plain, outMp4, onProgress)
            if (outMp4.exists() && outMp4.length() > 0L) outMp4 else null
        } catch (e: CancellationException) {
            wipeAndDelete(outMp4)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Remux failed: ${e.message}", e)
            wipeAndDelete(outMp4)
            null
        }
    }

    private suspend fun remuxAvToMp4(
        plainFile: File,
        outFile: File,
        onProgress: (Float) -> Unit,
    ) {
        val extractor = MediaExtractor()
        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            extractor.setDataSource(plainFile.absolutePath)
            val trackCount = extractor.trackCount
            if (trackCount <= 0) throw IllegalStateException("No tracks")
            val indexMap = IntArray(trackCount) { -1 }
            var added = 0
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)?.lowercase() ?: continue
                if (!mime.startsWith("video/") && !mime.startsWith("audio/")) continue
                try {
                    indexMap[i] = muxer.addTrack(format)
                    extractor.selectTrack(i)
                    added++
                } catch (e: Exception) {
                    Log.w(TAG, "Skip track $i mime=$mime: ${e.message}")
                    indexMap[i] = -1
                }
            }
            if (added <= 0) throw IllegalStateException("No A/V tracks")
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
            var lastPct = -1
            var sampleCount = 0L
            while (true) {
                coroutineContext.ensureActive()
                buffer.clear()
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                if (sampleSize > MAX_SAMPLE_BYTES) {
                    throw IllegalStateException("Sample $sampleSize > buffer")
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
                val frac = if (durationUs > 0L) {
                    (info.presentationTimeUs.toDouble() / durationUs.toDouble()).coerceIn(0.0, 1.0)
                } else {
                    (1.0 - 1.0 / (1.0 + sampleCount / 500.0)).coerceIn(0.0, 0.99)
                }
                val pct = (frac * 100).toInt()
                if (pct != lastPct) {
                    lastPct = pct
                    onProgress(frac.toFloat())
                }
            }
            onProgress(1f)
            Log.i(TAG, "Remux wrote $sampleCount samples → ${outFile.name}")
        } finally {
            try { muxer.stop() } catch (_: Exception) {}
            try { muxer.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    private fun wipeDir(dir: File) {
        if (!dir.exists()) return
        dir.listFiles()?.forEach { f ->
            try {
                if (f.isDirectory) {
                    wipeDir(f)
                    f.delete()
                } else {
                    wipeAndDelete(f)
                }
            } catch (_: Exception) {
            }
        }
        try {
            dir.delete()
        } catch (_: Exception) {
        }
    }

    private fun wipeAndDelete(file: File) {
        try {
            if (file.exists() && file.isFile) {
                val len = file.length().coerceAtMost(WIPE_CAP_BYTES)
                if (len > 0) {
                    RandomAccessFile(file, "rw").use { raf ->
                        val buf = ByteArray(minOf(len, 64L * 1024L).toInt())
                        var left = len
                        raf.seek(0)
                        while (left > 0) {
                            val n = minOf(buf.size.toLong(), left).toInt()
                            raf.write(buf, 0, n)
                            left -= n
                        }
                        raf.fd.sync()
                    }
                }
            }
        } catch (_: Exception) {
        }
        try {
            file.delete()
        } catch (_: Exception) {
        }
    }
}
