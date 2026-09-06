package app.vault.workspace.media

import app.vault.workspace.crypto.KeyHierarchy
import app.vault.workspace.crypto.VaultCrypto
import app.vault.workspace.crypto.VaultFormat
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

class ChunkCacheTest {

    private fun tmpDir(): File =
        File(System.getProperty("java.io.tmpdir"), "vault-chunkcache-${System.nanoTime()}")
            .also { it.mkdirs() }

    @Test
    fun chunkCache_randomAccess_matchesPlaintext() {
        val dir = tmpDir()
        try {
            val vat = File(dir, "cached.vat")
            val key = KeyHierarchy.generateDek()
            val size = VaultFormat.CHUNK_PLAINTEXT_SIZE + 4096
            val plain = ByteArray(size) { (it * 31 % 251).toByte() }
            VaultCrypto.encryptBytes(plain, key, vat, dir)
            val header = VaultCrypto.readHeader(vat)
            val cache = ChunkCache(maxEntries = 4)

            RandomAccessFile(vat, "r").use { raf ->
                val windows = listOf(
                    0L to 1024,
                    (VaultFormat.CHUNK_PLAINTEXT_SIZE - 200).toLong() to 400,
                    50L to 100,
                    (size - 512).toLong() to 512,
                    VaultFormat.CHUNK_PLAINTEXT_SIZE.toLong() to 2048,
                )
                for ((pos, len) in windows) {
                    val out = ByteArray(len)
                    val n = cache.decryptRange(raf, header, key, pos, len, out, 0)
                    assertEquals(len, n)
                    assertArrayEquals(
                        plain.copyOfRange(pos.toInt(), pos.toInt() + len),
                        out,
                    )
                }
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
