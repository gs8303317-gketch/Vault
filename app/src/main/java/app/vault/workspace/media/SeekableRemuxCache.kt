package app.vault.workspace.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.util.Log
import app.vault.workspace.crypto.VaultCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Background remux of unseekable WEB-DL (and similar) into a seekable MPEG-4 under
 * [PlaybackPlaintextCache]'s playcache dir (`seek_<key>.mp4`). Wiped on lock via
 * [PlaybackPlaintextCache.wipeAll].
 *
 * On codec/muxer failure returns null — caller keeps streaming playback (seek limited).
 */
object SeekableRemuxCache {
    private const val TAG = "VaultRemux"
    private const val FILE_PREFIX = "seek_"
    private const val MAX_SAMPLE_BYTES = 2 * 1024 * 1024

    private val keyLocks = ConcurrentHashMap<String, Mutex>()

    suspend fun getOrRemux(
        context: Context,
        itemKey: String,
        vatFile: File,
        dek: ByteArray,
        @Suppress("UNUSED_PARAMETER") mimeType: String,
    ): File? = withContext(Dispatchers.IO) {
        require(itemKey.isNotBlank()) { "itemKey required" }
        val cacheDir = PlaybackPlaintextCache.dir(context)
        cacheDir.mkdirs()
        val dest = remuxFile(cacheDir, itemKey)
        val lock = keyLocks.getOrPut(itemKey) { Mutex() }
        lock.withLock {
            if (dest.exists() && dest.isFile && dest.length() > 0L && !isStale(dest, vatFile)) {
                return@withContext dest
            }
            if (dest.exists()) {
                dest.delete()
            }
            val part = File(cacheDir, "${dest.name}.part")
            try {
                if (part.exists()) part.delete()
                remuxTo(part, vatFile, dek)
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
                dest
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
            }
        }
    }

    internal fun remuxFile(cacheDir: File, itemKey: String): File {
        val hash = sha256Hex(itemKey).take(32)
        return File(cacheDir, "$FILE_PREFIX$hash.mp4")
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

    private fun remuxTo(outFile: File, vatFile: File, dek: ByteArray) {
        val header = VaultCrypto.readHeader(vatFile)
        val dataSource = VaultMediaDataSource(vatFile, dek, header)
        val extractor = MediaExtractor()
        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            extractor.setDataSource(dataSource)
            val trackCount = extractor.trackCount
            if (trackCount <= 0) {
                throw IllegalStateException("No tracks in source")
            }
            val indexMap = IntArray(trackCount) { -1 }
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                extractor.selectTrack(i)
                indexMap[i] = muxer.addTrack(format)
            }
            muxer.start()

            val buffer = ByteBuffer.allocateDirect(MAX_SAMPLE_BYTES)
            val info = MediaCodec.BufferInfo()
            extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            while (true) {
                buffer.clear()
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
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
            }
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
            try {
                dataSource.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun sha256Hex(s: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { b -> "%02x".format(b) }
    }
}
