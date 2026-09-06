package app.vault.workspace.media

import android.media.MediaDataSource
import app.vault.workspace.crypto.VaultCrypto
import app.vault.workspace.crypto.VaultFormat
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Note: Path B prepare ([VideoSeekPrepare]) uses decrypt-to-file + MediaExtractor(path)
 * instead of this MediaDataSource (OEM hangs on long WEB-DLs). Kept unused for now.
 *
 * [MediaDataSource] over a VAULT1 blob — decrypts plaintext ranges on demand for
 * [android.media.MediaExtractor] remux (API 23+; minSdk 26).
 *
 * Does not own or wipe [dek]; caller retains lifetime.
 */
class VaultMediaDataSource(
    vatFile: File,
    private val dek: ByteArray,
    private val header: VaultFormat.Header,
    private val chunkCache: ChunkCache = ChunkCache(),
) : MediaDataSource() {

    private val raf: RandomAccessFile = RandomAccessFile(vatFile, "r")
    @Volatile
    private var closed = false

    override fun getSize(): Long = header.plaintextSize

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (closed) throw IOException("VaultMediaDataSource closed")
        if (size == 0) return 0
        if (position < 0) throw IOException("negative position $position")
        if (position >= header.plaintextSize) return -1
        return try {
            val n = chunkCache.decryptRange(
                raf,
                header,
                dek,
                position,
                size,
                buffer,
                offset,
            )
            if (n <= 0) -1 else n
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException(
                "VaultMediaDataSource readAt failed at pos=$position size=$size",
                e,
            )
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        try {
            raf.close()
        } catch (_: Exception) {
        }
        chunkCache.clear()
        // Do not wipe dek — owned by caller.
    }
}
