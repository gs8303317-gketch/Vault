package app.vault.workspace.media

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import android.system.Os
import android.system.OsConstants
import app.vault.workspace.crypto.KeyHierarchy
import app.vault.workspace.crypto.VaultCrypto
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Seekable read-only [ParcelFileDescriptor] over a VAULT1 blob without a durable
 * plaintext cache when possible (proxy FD / memfd). Last resort: session tmp file
 * deleted+wiped on [EncryptedSeekableHandle.releaseResources].
 *
 * Used by PDF ([EncryptedPdfOpener]). Media playback uses streaming
 * [EncryptedDataSource] + optional [SeekableRemuxCache] (not this opener).
 */
class EncryptedSeekableHandle(
    val pfd: ParcelFileDescriptor,
    /** Declared plaintext length (proxy onGetSize / memfd / tmp). */
    val plaintextSize: Long,
    private val onReleased: () -> Unit,
) {
    private val released = AtomicBoolean(false)

    /**
     * Call after the consumer finishes with [pfd] (closes it, or after player.release).
     * Wipes DEK / deletes any last-resort temp file. Idempotent with proxy onRelease.
     */
    fun releaseResources() {
        if (!released.compareAndSet(false, true)) return
        try {
            onReleased()
        } catch (_: Exception) {
        }
    }
}

object EncryptedSeekableOpener {
    /** Skip memfd for large media — whole-file RAM decrypt is only for small blobs. */
    const val MEMFD_MAX_BYTES: Long = 32L * 1024L * 1024L

    /**
     * @param dek caller-owned; this method copies what it needs and does not wipe [dek].
     * @param allowTempFile when false, skips wiped-tmp fallback (prefer EncryptedDataSource).
     */
    fun open(
        context: Context,
        vatFile: File,
        dek: ByteArray,
        tmpDir: File,
        tempFileName: String,
        threadName: String = "vault-seekable-proxy",
        allowTempFile: Boolean = true,
        memfdMaxBytes: Long = MEMFD_MAX_BYTES,
    ): EncryptedSeekableHandle {
        openProxy(context, vatFile, dek, threadName)?.let { return it }
        openMemfd(vatFile, dek, memfdMaxBytes)?.let { return it }
        if (!allowTempFile) {
            throw IllegalStateException("Proxy/memfd unavailable and temp file disabled")
        }
        return openTempFile(vatFile, dek, tmpDir, tempFileName)
    }

    /** Proxy-only attempt (primary path for video seek). */
    fun openProxyOrNull(
        context: Context,
        vatFile: File,
        dek: ByteArray,
        threadName: String = "vault-media-proxy",
    ): EncryptedSeekableHandle? = openProxy(context, vatFile, dek, threadName)

    fun openMemfdOrNull(
        vatFile: File,
        dek: ByteArray,
        memfdMaxBytes: Long = MEMFD_MAX_BYTES,
    ): EncryptedSeekableHandle? = openMemfd(vatFile, dek, memfdMaxBytes)

    private fun openProxy(
        context: Context,
        vatFile: File,
        dek: ByteArray,
        threadName: String,
    ): EncryptedSeekableHandle? {
        return try {
            val header = VaultCrypto.readHeader(vatFile)
            val dekCopy = dek.copyOf()
            val raf = RandomAccessFile(vatFile, "r")
            val thread = HandlerThread(threadName).also { it.start() }
            val handler = Handler(thread.looper)
            val sm = context.getSystemService(StorageManager::class.java)
                ?: throw IllegalStateException("No StorageManager")
            val released = AtomicBoolean(false)
            fun cleanup() {
                if (!released.compareAndSet(false, true)) return
                KeyHierarchy.wipe(dekCopy)
                try {
                    raf.close()
                } catch (_: Exception) {
                }
                thread.quitSafely()
            }
            val callback = object : ProxyFileDescriptorCallback() {
                override fun onGetSize(): Long = header.plaintextSize

                override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
                    if (size <= 0) return 0
                    return VaultCrypto.decryptRange(
                        raf,
                        header,
                        dekCopy,
                        offset,
                        size,
                        data,
                        0,
                    )
                }

                override fun onRelease() {
                    cleanup()
                }
            }
            val pfd = sm.openProxyFileDescriptor(
                ParcelFileDescriptor.MODE_READ_ONLY,
                callback,
                handler,
            )
            EncryptedSeekableHandle(
                pfd = pfd,
                plaintextSize = header.plaintextSize,
                onReleased = { cleanup() },
            )
        } catch (_: Exception) {
            null
        }
    }

    /** API 30+ anonymous memfd — plaintext only in RAM-backed fd, no durable path. */
    private fun openMemfd(
        vatFile: File,
        dek: ByteArray,
        memfdMaxBytes: Long,
    ): EncryptedSeekableHandle? {
        if (Build.VERSION.SDK_INT < 30) return null
        return try {
            val header = VaultCrypto.readHeader(vatFile)
            if (header.plaintextSize <= 0L || header.plaintextSize > memfdMaxBytes) {
                return null
            }
            val plaintext = VaultCrypto.decryptToBytes(vatFile, dek)
            try {
                val rawFd = Os.memfd_create("vault-seekable", 0)
                var written = 0
                while (written < plaintext.size) {
                    val n = Os.write(rawFd, plaintext, written, plaintext.size - written)
                    if (n <= 0) throw IllegalStateException("memfd write failed")
                    written += n
                }
                Os.lseek(rawFd, 0, OsConstants.SEEK_SET)
                val pfd = ParcelFileDescriptor.dup(rawFd)
                try {
                    Os.close(rawFd)
                } catch (_: Exception) {
                }
                EncryptedSeekableHandle(
                    pfd = pfd,
                    plaintextSize = plaintext.size.toLong(),
                    onReleased = {},
                )
            } finally {
                KeyHierarchy.wipe(plaintext)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun openTempFile(
        vatFile: File,
        dek: ByteArray,
        tmpDir: File,
        tempFileName: String,
    ): EncryptedSeekableHandle {
        tmpDir.mkdirs()
        val out = File(tmpDir, tempFileName)
        FileOutputStream(out).use { fos ->
            VaultCrypto.decryptToStream(vatFile, dek, fos)
            fos.fd.sync()
        }
        val pfd = ParcelFileDescriptor.open(out, ParcelFileDescriptor.MODE_READ_ONLY)
        return EncryptedSeekableHandle(
            pfd = pfd,
            plaintextSize = out.length(),
            onReleased = { wipeAndDelete(out) },
        )
    }

    private fun wipeAndDelete(file: File) {
        try {
            if (file.exists() && file.isFile) {
                val len = file.length().coerceAtMost(8L * 1024L * 1024L)
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
