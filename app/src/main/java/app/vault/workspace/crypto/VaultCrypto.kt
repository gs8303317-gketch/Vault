package app.vault.workspace.crypto

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Streaming encrypt / decrypt for VAULT1 files.
 * Write path: .part → fd.sync() → rename .vat
 */
object VaultCrypto {
    private val random = SecureRandom()

    class AuthFailedException(message: String, cause: Throwable? = null) :
        Exception(message, cause)

    fun encryptStream(
        plaintext: InputStream,
        plaintextSize: Long,
        dek: ByteArray,
        destVat: File,
        tmpDir: File,
    ): VaultFormat.Header {
        require(dek.size == KeyHierarchy.KEY_SIZE_BYTES)
        require(plaintextSize >= 0)
        tmpDir.mkdirs()
        destVat.parentFile?.mkdirs()

        val noncePrefix = ByteArray(VaultFormat.NONCE_PREFIX_SIZE).also { random.nextBytes(it) }
        val fileId = ByteArray(VaultFormat.FILE_ID_SIZE).also { random.nextBytes(it) }
        val header = VaultFormat.Header(
            version = VaultFormat.VERSION,
            flags = 0,
            chunkSize = VaultFormat.CHUNK_PLAINTEXT_SIZE,
            plaintextSize = plaintextSize,
            noncePrefix = noncePrefix,
            fileId = fileId,
        )

        val part = File(tmpDir, "${destVat.nameWithoutExtension}-${System.nanoTime()}.part")
        try {
            FileOutputStream(part).use { fos ->
                VaultFormat.writeHeader(fos, header)
                if (plaintextSize > 0L) {
                    val chunkBuf = ByteArray(VaultFormat.CHUNK_PLAINTEXT_SIZE)
                    var remaining = plaintextSize
                    var chunkIndex = 0
                    val key = SecretKeySpec(dek, "AES")
                    while (remaining > 0) {
                        val toRead = minOf(VaultFormat.CHUNK_PLAINTEXT_SIZE.toLong(), remaining).toInt()
                        var off = 0
                        while (off < toRead) {
                            val n = plaintext.read(chunkBuf, off, toRead - off)
                            if (n < 0) {
                                throw IllegalStateException("Unexpected EOF from plaintext stream")
                            }
                            off += n
                        }
                        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                        cipher.init(
                            Cipher.ENCRYPT_MODE,
                            key,
                            GCMParameterSpec(
                                KeyHierarchy.GCM_TAG_BITS,
                                VaultFormat.ivForChunk(header.noncePrefix, chunkIndex),
                            ),
                        )
                        cipher.updateAAD(VaultFormat.aadForChunk(header.fileId, chunkIndex))
                        fos.write(cipher.doFinal(chunkBuf, 0, toRead))
                        chunkIndex++
                        remaining -= toRead
                    }
                }
                fos.fd.sync()
            }
            if (destVat.exists() && !destVat.delete()) {
                throw IllegalStateException("Cannot replace existing vault file")
            }
            if (!part.renameTo(destVat)) {
                part.copyTo(destVat, overwrite = true)
                if (!part.delete()) {
                    part.deleteOnExit()
                }
            }
            return header
        } catch (e: Exception) {
            part.delete()
            throw e
        }
    }

    fun encryptBytes(
        plaintext: ByteArray,
        dek: ByteArray,
        destVat: File,
        tmpDir: File,
    ): VaultFormat.Header =
        encryptStream(plaintext.inputStream(), plaintext.size.toLong(), dek, destVat, tmpDir)

    fun decryptToStream(vat: File, dek: ByteArray, out: OutputStream) {
        FileInputStream(vat).use { fis ->
            val header = VaultFormat.readHeader(fis)
            decryptBody(fis, header, dek, out)
        }
    }

    fun decryptToBytes(vat: File, dek: ByteArray): ByteArray {
        val header = FileInputStream(vat).use { VaultFormat.readHeader(it) }
        require(header.plaintextSize <= Int.MAX_VALUE) { "File too large for byte array" }
        val out = java.io.ByteArrayOutputStream(header.plaintextSize.toInt().coerceAtLeast(0))
        decryptToStream(vat, dek, out)
        return out.toByteArray()
    }

    fun decryptBody(
        cipherIn: InputStream,
        header: VaultFormat.Header,
        dek: ByteArray,
        out: OutputStream,
    ) {
        require(dek.size == KeyHierarchy.KEY_SIZE_BYTES)
        if (header.plaintextSize == 0L) return
        val key = SecretKeySpec(dek, "AES")
        var remaining = header.plaintextSize
        var chunkIndex = 0
        while (remaining > 0) {
            val plainLen = minOf(header.chunkSize.toLong(), remaining).toInt()
            val cipherLen = VaultFormat.ciphertextChunkLength(plainLen)
            val ct = ByteArray(cipherLen)
            VaultFormat.readFully(cipherIn, ct)
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    key,
                    GCMParameterSpec(
                        KeyHierarchy.GCM_TAG_BITS,
                        VaultFormat.ivForChunk(header.noncePrefix, chunkIndex),
                    ),
                )
                cipher.updateAAD(VaultFormat.aadForChunk(header.fileId, chunkIndex))
                out.write(cipher.doFinal(ct))
            } catch (e: AEADBadTagException) {
                throw AuthFailedException("Chunk $chunkIndex authentication failed", e)
            }
            chunkIndex++
            remaining -= plainLen
        }
    }

    /**
     * Decrypt plaintext range [position, position+length) for Media3 seeking.
     * Whole overlapping chunks are authenticated and decrypted.
     */
    fun decryptRange(
        raf: RandomAccessFile,
        header: VaultFormat.Header,
        dek: ByteArray,
        position: Long,
        length: Int,
        out: ByteArray,
        outOffset: Int = 0,
    ): Int {
        require(position >= 0)
        require(length >= 0)
        if (length == 0 || position >= header.plaintextSize) return 0
        val end = minOf(position + length, header.plaintextSize)
        val toCopy = (end - position).toInt()
        val key = SecretKeySpec(dek, "AES")
        val chunkSize = header.chunkSize
        var dest = outOffset
        var pos = position
        while (pos < end) {
            val chunkIndex = (pos / chunkSize).toInt()
            val chunkPlainStart = chunkIndex.toLong() * chunkSize
            val chunkPlainLen =
                minOf(chunkSize.toLong(), header.plaintextSize - chunkPlainStart).toInt()
            val cipherOff = VaultFormat.ciphertextOffsetForChunk(chunkIndex, chunkSize)
            val cipherLen = VaultFormat.ciphertextChunkLength(chunkPlainLen)
            val ct = ByteArray(cipherLen)
            raf.seek(cipherOff)
            raf.readFully(ct)
            val pt = try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    key,
                    GCMParameterSpec(
                        KeyHierarchy.GCM_TAG_BITS,
                        VaultFormat.ivForChunk(header.noncePrefix, chunkIndex),
                    ),
                )
                cipher.updateAAD(VaultFormat.aadForChunk(header.fileId, chunkIndex))
                cipher.doFinal(ct)
            } catch (e: AEADBadTagException) {
                throw AuthFailedException("Chunk $chunkIndex authentication failed", e)
            }
            val localOff = (pos - chunkPlainStart).toInt()
            val n = minOf(pt.size - localOff, (end - pos).toInt())
            System.arraycopy(pt, localOff, out, dest, n)
            dest += n
            pos += n
        }
        return toCopy
    }

    fun readHeader(vat: File): VaultFormat.Header =
        FileInputStream(vat).use { VaultFormat.readHeader(it) }
}
