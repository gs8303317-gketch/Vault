package app.vault.workspace.media

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import app.vault.workspace.crypto.VaultCrypto
import app.vault.workspace.crypto.VaultFormat
import java.io.File
import java.io.RandomAccessFile

/**
 * Media3 DataSource that decrypts VAULT1 on demand for the requested byte range.
 */
class EncryptedDataSource(
    private val vatFile: File,
    private val dek: ByteArray,
) : BaseDataSource(/* isNetwork= */ false) {

    private var raf: RandomAccessFile? = null
    private var header: VaultFormat.Header? = null
    private var position: Long = 0
    private var bytesRemaining: Long = 0
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val localRaf = RandomAccessFile(vatFile, "r")
        raf = localRaf
        val hdr = VaultCrypto.readHeader(vatFile)
        header = hdr
        val start = dataSpec.position
        require(start >= 0)
        position = start
        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else {
            (hdr.plaintextSize - start).coerceAtLeast(0L)
        }
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val hdr = header ?: return C.RESULT_END_OF_INPUT
        val localRaf = raf ?: return C.RESULT_END_OF_INPUT
        val toRead = minOf(length.toLong(), bytesRemaining).toInt()
        val n = VaultCrypto.decryptRange(localRaf, hdr, dek, position, toRead, buffer, offset)
        if (n <= 0) return C.RESULT_END_OF_INPUT
        position += n
        bytesRemaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = if (opened) Uri.fromFile(vatFile) else null

    override fun close() {
        raf?.close()
        raf = null
        header = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }
}

class EncryptedDataSourceFactory(
    private val vatFile: File,
    private val dek: ByteArray,
) : androidx.media3.datasource.DataSource.Factory {
    override fun createDataSource(): androidx.media3.datasource.DataSource =
        EncryptedDataSource(vatFile, dek)
}
