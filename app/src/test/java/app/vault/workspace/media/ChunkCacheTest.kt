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

    @Test
    fun chunkCache_moovAtEnd_style_tailThenHead() {
        val dir = tmpDir()
        try {
            val vat = File(dir, "moov.vat")
            val key = KeyHierarchy.generateDek()
            // Multi-chunk blob: extractor often reads head, then near-EOF (moov/cues), then mid.
            val size = VaultFormat.CHUNK_PLAINTEXT_SIZE * 3 + 8192
            val plain = ByteArray(size) { (it * 17 % 251).toByte() }
            VaultCrypto.encryptBytes(plain, key, vat, dir)
            val header = VaultCrypto.readHeader(vat)
            val cache = ChunkCache(maxEntries = 8)

            RandomAccessFile(vat, "r").use { raf ->
                val windows = listOf(
                    0L to 64, // ftyp-ish
                    (size - 4096).toLong() to 4096, // moov/cues at end
                    (VaultFormat.CHUNK_PLAINTEXT_SIZE + 100).toLong() to 2048, // mid mdat
                    (size - 512).toLong() to 512,
                    0L to 1024,
                )
                for ((pos, len) in windows) {
                    val out = ByteArray(len)
                    val n = cache.decryptRange(raf, header, key, pos, len, out, 0)
                    assertEquals("pos=$pos len=$len", len, n)
                    assertArrayEquals(
                        "pos=$pos len=$len",
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
