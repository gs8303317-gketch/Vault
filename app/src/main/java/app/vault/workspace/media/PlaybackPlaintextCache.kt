package app.vault.workspace.media

import android.content.Context
import app.vault.workspace.crypto.VaultCrypto
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Legacy session plaintext play-cache (v0.4.6–0.4.7 video path). Playback no longer
 * writes these files — seek uses [SeekableFallbackExtractorsFactory] + streaming
 * [EncryptedDataSource]. [wipeAll] remains so leftover caches are purged on lock /
 * cold start via [app.vault.workspace.auth.SessionManager.wipeTmp].
 */
object PlaybackPlaintextCache {
    private const val DIR_NAME = "playcache"
    private const val FILE_PREFIX = "playcache_"
    /** Overwrite at most this many leading bytes before delete (same idea as PDF tmp wipe). */
    private const val WIPE_CAP_BYTES = 8L * 1024L * 1024L

    private val keyLocks = ConcurrentHashMap<String, Any>()

    fun dir(context: Context): File =
        File(context.cacheDir, DIR_NAME).also { it.mkdirs() }

    /**
     * Return a cached plaintext file for [itemKey], decrypting if missing or size-mismatched.
     * Thread-safe: one decrypt at a time per [itemKey].
     */
    fun getOrCreate(
        context: Context,
        itemKey: String,
        vatFile: File,
        dek: ByteArray,
        mimeType: String = "application/octet-stream",
    ): File = getOrCreate(dir(context), itemKey, vatFile, dek, mimeType)

    /**
     * File-dir overload for JVM unit tests (no Android Context required).
     */
    fun getOrCreate(
        cacheDir: File,
        itemKey: String,
        vatFile: File,
        dek: ByteArray,
        mimeType: String = "application/octet-stream",
    ): File {
        require(itemKey.isNotBlank()) { "itemKey required" }
        val lock = keyLocks.getOrPut(itemKey) { Any() }
        synchronized(lock) {
            cacheDir.mkdirs()
            val expectedSize = VaultCrypto.readHeader(vatFile).plaintextSize
            val dest = cacheFile(cacheDir, itemKey, mimeType)
            if (dest.exists() && dest.isFile && dest.length() == expectedSize) {
                return dest
            }
            if (dest.exists()) {
                wipeAndDelete(dest)
            }
            val part = File(cacheDir, "${dest.name}.part")
            try {
                if (part.exists()) wipeAndDelete(part)
                FileOutputStream(part).use { fos ->
                    VaultCrypto.decryptToStream(vatFile, dek, fos)
                    fos.fd.sync()
                }
                if (part.length() != expectedSize) {
                    wipeAndDelete(part)
                    throw IllegalStateException(
                        "Play-cache size mismatch: got ${part.length()}, expected $expectedSize",
                    )
                }
                if (!part.renameTo(dest)) {
                    part.copyTo(dest, overwrite = true)
                    wipeAndDelete(part)
                }
                return dest
            } catch (e: Exception) {
                wipeAndDelete(part)
                if (dest.exists() && dest.length() != expectedSize) {
                    wipeAndDelete(dest)
                }
                throw e
            }
        }
    }

    fun wipeAll(context: Context) {
        wipeAll(dir(context))
    }

    fun wipeAll(cacheDir: File) {
        if (!cacheDir.exists()) return
        cacheDir.listFiles()?.forEach { f ->
            try {
                if (f.isDirectory) {
                    f.deleteRecursively()
                } else {
                    wipeAndDelete(f)
                }
            } catch (_: Exception) {
            }
        }
        try {
            cacheDir.delete()
        } catch (_: Exception) {
        }
    }

    internal fun cacheFile(cacheDir: File, itemKey: String, mimeType: String): File {
        val hash = sha256Hex(itemKey).take(32)
        val ext = extensionForMime(mimeType)
        return File(cacheDir, "$FILE_PREFIX$hash$ext")
    }

    internal fun extensionForMime(mimeType: String): String {
        val m = mimeType.lowercase().substringBefore(';').trim()
        return when (m) {
            "video/mp4", "video/avc" -> ".mp4"
            "video/webm" -> ".webm"
            "video/x-matroska", "video/matroska" -> ".mkv"
            "video/quicktime" -> ".mov"
            "video/3gpp", "video/3gpp2" -> ".3gp"
            "video/mp2t" -> ".ts"
            "video/x-msvideo" -> ".avi"
            else -> ".bin"
        }
    }

    private fun sha256Hex(s: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { b -> "%02x".format(b) }
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
