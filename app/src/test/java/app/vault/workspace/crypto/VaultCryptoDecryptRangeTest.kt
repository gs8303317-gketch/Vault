package app.vault.workspace.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

/**
 * Random-access decrypt must match full decrypt at arbitrary offsets —
 * required for Media3 ProgressiveMediaSource SeekMap / seek.
 */
class VaultCryptoDecryptRangeTest {

    private fun tmpDir(): File =
        File(System.getProperty("java.io.tmpdir"), "vault-range-${System.nanoTime()}")
            .also { it.mkdirs() }

    @Test
    fun decryptRange_matchesFullPlaintext_acrossChunkBoundaries() {
        val dir = tmpDir()
        try {
            val vat = File(dir, "multi.vat")
            val key = KeyHierarchy.generateDek()
            // 2 full chunks + 100 bytes — forces multi-chunk seeks
            val size = VaultFormat.CHUNK_PLAINTEXT_SIZE * 2 + 100
            val plain = ByteArray(size) { (it % 251).toByte() }
            VaultCrypto.encryptBytes(plain, key, vat, dir)
            val header = VaultCrypto.readHeader(vat)

            RandomAccessFile(vat, "r").use { raf ->
                val cases = listOf(
                    0 to 64,
                    100 to 200,
                    VaultFormat.CHUNK_PLAINTEXT_SIZE - 50 to 100, // crosses chunk 0→1
                    VaultFormat.CHUNK_PLAINTEXT_SIZE to 128,
                    VaultFormat.CHUNK_PLAINTEXT_SIZE * 2 - 10 to 50, // crosses chunk 1→2
                    size - 40 to 40,
                    size - 1 to 1,
                )
                for ((pos, len) in cases) {
                    val out = ByteArray(len)
                    val n = VaultCrypto.decryptRange(raf, header, key, pos.toLong(), len, out, 0)
                    assertEquals("pos=$pos len=$len", len, n)
                    assertArrayEquals(
                        "pos=$pos len=$len",
                        plain.copyOfRange(pos, pos + len),
                        out,
                    )
                }
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun decryptRange_pastEnd_clamps() {
        val dir = tmpDir()
        try {
            val vat = File(dir, "small.vat")
            val key = KeyHierarchy.generateDek()
            val plain = ByteArray(100) { it.toByte() }
            VaultCrypto.encryptBytes(plain, key, vat, dir)
            val header = VaultCrypto.readHeader(vat)
            RandomAccessFile(vat, "r").use { raf ->
                val out = ByteArray(50)
                val n = VaultCrypto.decryptRange(raf, header, key, 80, 50, out, 0)
                assertEquals(20, n)
                assertArrayEquals(plain.copyOfRange(80, 100), out.copyOf(20))
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
