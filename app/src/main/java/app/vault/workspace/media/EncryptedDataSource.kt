package app.vault.workspace.media

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import app.vault.workspace.crypto.VaultCrypto
import app.vault.workspace.crypto.VaultFormat
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap

/**
 * Media3 DataSource that decrypts VAULT1 on demand for the requested byte range.
 * Supports random access so ProgressiveMediaSource / Mp4Extractor / MatroskaExtractor
 * can build a SeekMap (including moov/cues at end) and seek.
 *
 * [dataSpec.position] is a plaintext byte offset. Each [open] starts a fresh read
 * window; Media3 closes and re-opens on every seek.
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
        // Media3 normally close()s before re-open; if not, finish the prior transfer
        // so BaseDataSource transferStarted state stays consistent.
        if (opened) {
            close()
        } else {
            releaseRaf()
        }
        transferInitializing(dataSpec)
        try {
            val localRaf = RandomAccessFile(vatFile, "r")
            raf = localRaf
            val start = dataSpec.position.coerceAtLeast(0L)
            if (start > cachedHeader.plaintextSize) {
                throw IOException(
                    "DataSpec position $start past plaintext ${cachedHeader.plaintextSize}",
                )
            }
            position = start
            bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                dataSpec.length.coerceAtMost(cachedHeader.plaintextSize - start)
            } else {
                (cachedHeader.plaintextSize - start).coerceAtLeast(0L)
            }
            uri = dataSpec.uri
            opened = true
            transferStarted(dataSpec)
            return bytesRemaining
        } catch (e: IOException) {
            releaseRaf()
            throw e
        } catch (e: Exception) {
            releaseRaf()
            throw IOException("EncryptedDataSource open failed at pos=${dataSpec.position}", e)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val localRaf = raf ?: return C.RESULT_END_OF_INPUT
        val toRead = minOf(length.toLong(), bytesRemaining).toInt()
        return try {
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
            n
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException(
                "EncryptedDataSource read failed at pos=$position len=$toRead",
                e,
            )
        }
    }

    override fun getUri(): Uri? = if (opened) uri else null

    override fun close() {
        releaseRaf()
        if (opened) {
            opened = false
            transferEnded()
        }
        uri = null
        position = 0
        bytesRemaining = 0
    }

    private fun releaseRaf() {
        try {
            raf?.close()
        } catch (_: Exception) {
        }
        raf = null
    }
}

/**
 * Small plaintext-chunk cache shared by all DataSources for one playback session.
 * Speeds sequential reads and nearby seeks (incl. moov/cues at end + current window)
 * without holding the whole file.
 */
class ChunkCache(private val maxEntries: Int = 48) {
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
            require(localOff in 0 until plain.size) {
                "chunk $chunkIndex localOff=$localOff size=${plain.size}"
            }
            val n = minOf(plain.size - localOff, (end - pos).toInt())
            require(n > 0) { "decryptRange made no progress at pos=$pos" }
            System.arraycopy(plain, localOff, out, dest, n)
            dest += n
            pos += n
        }
        return toCopy
    }

    @Synchronized
    fun clear() {
        map.clear()
        order.clear()
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
        require(chunkPlainLen > 0) { "empty chunk $chunkIndex" }
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
            // Do not evict the chunk we just inserted (can happen if maxEntries==0).
            if (evict == chunkIndex && order.isNotEmpty()) {
                order.addLast(evict)
                val other = order.removeFirst()
                if (other != chunkIndex) map.remove(other)
            } else if (evict != chunkIndex) {
                map.remove(evict)
            }
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
