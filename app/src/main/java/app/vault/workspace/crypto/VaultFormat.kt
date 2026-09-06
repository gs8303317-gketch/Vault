package app.vault.workspace.crypto

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * VAULT1 container format — exact 64-byte big-endian header.
 *
 * Layout:
 * 0:6   ASCII "VAULT1"
 * 6:1   version = 1
 * 7:1   flags = 0
 * 8:4   chunkSize = 262144
 * 12:8  plaintext size
 * 20:8  noncePrefix
 * 28:16 fileId
 * 44:20 reserved zero
 *
 * Chunks follow: ciphertext || 16-byte GCM tag.
 * Empty file = header only.
 */
object VaultFormat {
    const val MAGIC = "VAULT1"
    const val VERSION: Byte = 1
    const val HEADER_SIZE = 64
    const val CHUNK_PLAINTEXT_SIZE = 262_144
    const val GCM_TAG_SIZE = 16
    const val NONCE_PREFIX_SIZE = 8
    const val FILE_ID_SIZE = 16
    const val IV_SIZE = 12
    const val RESERVED_SIZE = 20

    data class Header(
        val version: Byte,
        val flags: Byte,
        val chunkSize: Int,
        val plaintextSize: Long,
        val noncePrefix: ByteArray,
        val fileId: ByteArray,
    ) {
        init {
            require(noncePrefix.size == NONCE_PREFIX_SIZE)
            require(fileId.size == FILE_ID_SIZE)
            require(chunkSize > 0)
            require(plaintextSize >= 0)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Header) return false
            return version == other.version &&
                flags == other.flags &&
                chunkSize == other.chunkSize &&
                plaintextSize == other.plaintextSize &&
                noncePrefix.contentEquals(other.noncePrefix) &&
                fileId.contentEquals(other.fileId)
        }

        override fun hashCode(): Int {
            var result = version.toInt()
            result = 31 * result + flags
            result = 31 * result + chunkSize
            result = 31 * result + plaintextSize.hashCode()
            result = 31 * result + noncePrefix.contentHashCode()
            result = 31 * result + fileId.contentHashCode()
            return result
        }
    }

    fun writeHeader(out: OutputStream, header: Header) {
        val buf = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.BIG_ENDIAN)
        buf.put(MAGIC.toByteArray(Charsets.US_ASCII))
        buf.put(header.version)
        buf.put(header.flags)
        buf.putInt(header.chunkSize)
        buf.putLong(header.plaintextSize)
        buf.put(header.noncePrefix)
        buf.put(header.fileId)
        buf.put(ByteArray(RESERVED_SIZE))
        require(buf.position() == HEADER_SIZE)
        out.write(buf.array())
    }

    fun readHeader(input: InputStream): Header {
        val raw = ByteArray(HEADER_SIZE)
        DataInputStream(input).readFully(raw)
        return parseHeader(raw)
    }

    fun parseHeader(raw: ByteArray): Header {
        require(raw.size >= HEADER_SIZE) { "Header too short" }
        val buf = ByteBuffer.wrap(raw, 0, HEADER_SIZE).order(ByteOrder.BIG_ENDIAN)
        val magicBytes = ByteArray(6)
        buf.get(magicBytes)
        val magic = String(magicBytes, Charsets.US_ASCII)
        require(magic == MAGIC) { "Bad magic: $magic" }
        val version = buf.get()
        require(version == VERSION) { "Unsupported version: $version" }
        val flags = buf.get()
        val chunkSize = buf.int
        require(chunkSize == CHUNK_PLAINTEXT_SIZE) { "Unexpected chunk size: $chunkSize" }
        val plaintextSize = buf.long
        require(plaintextSize >= 0) { "Negative size" }
        val noncePrefix = ByteArray(NONCE_PREFIX_SIZE)
        buf.get(noncePrefix)
        val fileId = ByteArray(FILE_ID_SIZE)
        buf.get(fileId)
        // reserved ignored
        return Header(version, flags, chunkSize, plaintextSize, noncePrefix, fileId)
    }

    /** 12-byte IV = noncePrefix(8) || uint32BE(chunkIndex) */
    fun ivForChunk(noncePrefix: ByteArray, chunkIndex: Int): ByteArray {
        require(noncePrefix.size == NONCE_PREFIX_SIZE)
        val iv = ByteArray(IV_SIZE)
        System.arraycopy(noncePrefix, 0, iv, 0, NONCE_PREFIX_SIZE)
        iv[8] = ((chunkIndex ushr 24) and 0xff).toByte()
        iv[9] = ((chunkIndex ushr 16) and 0xff).toByte()
        iv[10] = ((chunkIndex ushr 8) and 0xff).toByte()
        iv[11] = (chunkIndex and 0xff).toByte()
        return iv
    }

    /** AAD = fileId(16) || uint32BE(chunkIndex) */
    fun aadForChunk(fileId: ByteArray, chunkIndex: Int): ByteArray {
        require(fileId.size == FILE_ID_SIZE)
        val aad = ByteArray(FILE_ID_SIZE + 4)
        System.arraycopy(fileId, 0, aad, 0, FILE_ID_SIZE)
        aad[16] = ((chunkIndex ushr 24) and 0xff).toByte()
        aad[17] = ((chunkIndex ushr 16) and 0xff).toByte()
        aad[18] = ((chunkIndex ushr 8) and 0xff).toByte()
        aad[19] = (chunkIndex and 0xff).toByte()
        return aad
    }

    fun chunkCount(plaintextSize: Long, chunkSize: Int = CHUNK_PLAINTEXT_SIZE): Int {
        if (plaintextSize == 0L) return 0
        return ((plaintextSize + chunkSize - 1) / chunkSize).toInt()
    }

    fun ciphertextChunkLength(plaintextLen: Int): Int = plaintextLen + GCM_TAG_SIZE

    /**
     * Byte offset in the file where ciphertext for [chunkIndex] begins
     * (after the 64-byte header). All prior chunks are full-size.
     */
    fun ciphertextOffsetForChunk(chunkIndex: Int, chunkSize: Int = CHUNK_PLAINTEXT_SIZE): Long {
        require(chunkIndex >= 0)
        val fullCipher = ciphertextChunkLength(chunkSize).toLong()
        return HEADER_SIZE + chunkIndex * fullCipher
    }

    fun readFullyOrEof(input: InputStream, buf: ByteArray, off: Int, len: Int): Int {
        var read = 0
        while (read < len) {
            val n = input.read(buf, off + read, len - read)
            if (n < 0) break
            read += n
        }
        return read
    }

    fun readFully(input: InputStream, buf: ByteArray) {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n < 0) throw EOFException("Unexpected EOF")
            off += n
        }
    }
}
