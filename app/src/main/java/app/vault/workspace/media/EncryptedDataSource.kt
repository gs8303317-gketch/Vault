package app.vault.workspace.media

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import app.vault.workspace.crypto.VaultCrypto
import app.vault.workspace.crypto.VaultFormat
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap

/**
 * Media3 DataSource that decrypts VAULT1 on demand for the requested byte range.
 * Supports random access so ProgressiveMediaSource can build a SeekMap and seek.
 */
class EncryptedDataSource(
    private val vatFile: File,
    private val dek: ByteArray,
    private val cachedHeader: VaultFormat.Header,
    private val chunkCache: ChunkCache,
) : BaseDataSource(/* isNetwork= */ false) {

    private var raf: RandomAccessFile? = null
    private var position: Long = 0
    private var bytesRemaining: Long = 0
    private var opened = false
    private var uri: Uri? = null

    override fun open(dataSpec: DataSpec): Long {
        // Always reset — Media3 re-opens on every seek.
        close()
        transferInitializing(dataSpec)
        val localRaf = RandomAccessFile(vatFile, "r")
        raf = localRaf
        val start = dataSpec.position.coerceAtLeast(0L)
        position = start
        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else {
            (cachedHeader.plaintextSize - start).coerceAtLeast(0L)
        }
        uri = dataSpec.uri
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val localRaf = raf ?: return C.RESULT_END_OF_INPUT
        val toRead = minOf(length.toLong(), bytesRemaining).toInt()
        val n = chunkCache.decryptRange(
            localRaf,
            cachedHeader,
            dek,
            position,
            toRead,
            buffer,
            offset,
        )
        if (n <= 0) return C.RESULT_END_OF_INPUT
        position += n
        bytesRemaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = if (opened) uri else null

    override fun close() {
        try {
            raf?.close()
        } catch (_: Exception) {
        }
        raf = null
        if (opened) {
            opened = false
            transferEnded()
        }
        uri = null
    }
}

/**
 * Small plaintext-chunk cache shared by all DataSources for one playback session.
 * Speeds sequential reads and nearby seeks without holding the whole file.
 */
class ChunkCache(private val maxEntries: Int = 8) {
    private data class Entry(val index: Int, val plain: ByteArray)

    private val map = ConcurrentHashMap<Int, Entry>()
    private val order = ArrayDeque<Int>()

    @Synchronized
    fun decryptRange(
        raf: RandomAccessFile,
        header: VaultFormat.Header,
        dek: ByteArray,
        position: Long,
        length: Int,
        out: ByteArray,
        outOffset: Int,
    ): Int {
        if (length == 0 || position >= header.plaintextSize) return 0
        val end = minOf(position + length, header.plaintextSize)
        val toCopy = (end - position).toInt()
        var dest = outOffset
        var pos = position
        val chunkSize = header.chunkSize
        while (pos < end) {
            val chunkIndex = (pos / chunkSize).toInt()
            val plain = plaintextChunk(raf, header, dek, chunkIndex)
            val chunkPlainStart = chunkIndex.toLong() * chunkSize
            val localOff = (pos - chunkPlainStart).toInt()
            val n = minOf(plain.size - localOff, (end - pos).toInt())
            System.arraycopy(plain, localOff, out, dest, n)
            dest += n
            pos += n
        }
        return toCopy
    }

    @Synchronized
    private fun plaintextChunk(
        raf: RandomAccessFile,
        header: VaultFormat.Header,
        dek: ByteArray,
        chunkIndex: Int,
    ): ByteArray {
        map[chunkIndex]?.let { return it.plain }
        val chunkSize = header.chunkSize
        val chunkPlainStart = chunkIndex.toLong() * chunkSize
        val chunkPlainLen =
            minOf(chunkSize.toLong(), header.plaintextSize - chunkPlainStart).toInt()
        // Decrypt just this chunk via VaultCrypto helper (single-chunk range).
        val buf = ByteArray(chunkPlainLen)
        val n = VaultCrypto.decryptRange(
            raf,
            header,
            dek,
            chunkPlainStart,
            chunkPlainLen,
            buf,
            0,
        )
        val plain = if (n == chunkPlainLen) buf else buf.copyOf(n)
        map[chunkIndex] = Entry(chunkIndex, plain)
        order.addLast(chunkIndex)
        while (order.size > maxEntries) {
            val evict = order.removeFirst()
            map.remove(evict)
        }
        return plain
    }
}

class EncryptedDataSourceFactory(
    private val vatFile: File,
    private val dek: ByteArray,
) : androidx.media3.datasource.DataSource.Factory {
    private val header: VaultFormat.Header by lazy { VaultCrypto.readHeader(vatFile) }
    private val chunkCache = ChunkCache()

    override fun createDataSource(): androidx.media3.datasource.DataSource =
        EncryptedDataSource(vatFile, dek, header, chunkCache)
}
