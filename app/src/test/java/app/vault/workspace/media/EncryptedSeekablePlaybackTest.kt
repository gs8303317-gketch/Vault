package app.vault.workspace.media

import app.vault.workspace.crypto.KeyHierarchy
import app.vault.workspace.crypto.VaultCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

/**
 * Known-good faststart MP4 → encrypt once → chunked GCM random-access decrypt
 * matches plaintext at head / mid / near-end (EncryptedDataSource path).
 * No ExoPlayer on JVM — intentional.
 */
class EncryptedSeekablePlaybackTest {

    private fun tmpDir(): File =
        File(System.getProperty("java.io.tmpdir"), "vault-seekplay-${System.nanoTime()}")
            .also { it.mkdirs() }

    @Test
    fun knownGoodFaststartMp4_encryptAndRandomAccess() {
        val dir = tmpDir()
        try {
            val plainFile = File(dir, "faststart.mp4")
            FakeMp4.write(plainFile, moovBeforeMdat = true, withMoof = false)
            val plain = plainFile.readBytes()

            val vat = File(dir, "item.vat")
            val dek = KeyHierarchy.generateDek()
            VaultCrypto.encryptStream(
                plainFile.inputStream(),
                plain.size.toLong(),
                dek,
                vat,
                dir,
            )
            val header = VaultCrypto.readHeader(vat)
            assertEquals(plain.size.toLong(), header.plaintextSize)

            val mid = plain.size / 2
            val nearEnd = (plain.size - 16).coerceAtLeast(0)
            val windows = listOf(
                0 to minOf(64, plain.size),
                mid to minOf(32, plain.size - mid),
                nearEnd to (plain.size - nearEnd),
            )

            RandomAccessFile(vat, "r").use { raf ->
                for ((pos, len) in windows) {
                    if (len <= 0) continue
                    val out = ByteArray(len)
                    val n = VaultCrypto.decryptRange(raf, header, dek, pos.toLong(), len, out, 0)
                    assertEquals("decryptRange pos=$pos len=$len", len, n)
                    assertArrayEquals(
                        "decryptRange pos=$pos",
                        plain.copyOfRange(pos, pos + len),
                        out,
                    )
                }
            }

            val cache = ChunkCache(maxEntries = 8)
            RandomAccessFile(vat, "r").use { raf ->
                for ((pos, len) in windows) {
                    if (len <= 0) continue
                    val out = ByteArray(len)
                    val n = cache.decryptRange(raf, header, dek, pos.toLong(), len, out, 0)
                    assertEquals("ChunkCache pos=$pos len=$len", len, n)
                    assertArrayEquals(
                        "ChunkCache pos=$pos",
                        plain.copyOfRange(pos, pos + len),
                        out,
                    )
                }
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
