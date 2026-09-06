package app.vault.workspace.media

import app.vault.workspace.crypto.KeyHierarchy
import app.vault.workspace.crypto.VaultCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlaybackPlaintextCacheTest {

    private fun tmpDir(): File =
        File(System.getProperty("java.io.tmpdir"), "vault-playcache-${System.nanoTime()}")
            .also { it.mkdirs() }

    @Test
    fun getOrCreate_reusesMatchingCache_andWipeAllClears() {
        val dir = tmpDir()
        val cacheDir = File(dir, "playcache").also { it.mkdirs() }
        try {
            val vat = File(dir, "clip.vat")
            val key = KeyHierarchy.generateDek()
            val plain = ByteArray(4096) { (it * 13 % 251).toByte() }
            VaultCrypto.encryptBytes(plain, key, vat, dir)

            val first = PlaybackPlaintextCache.getOrCreate(
                cacheDir = cacheDir,
                itemKey = "item-abc",
                vatFile = vat,
                dek = key,
                mimeType = "video/mp4",
            )
            assertTrue(first.exists())
            assertEquals(plain.size.toLong(), first.length())
            assertTrue(first.name.startsWith("playcache_"))
            assertTrue(first.name.endsWith(".mp4"))
            assertTrue(first.readBytes().contentEquals(plain))

            val mtime = first.lastModified()
            Thread.sleep(5)
            val second = PlaybackPlaintextCache.getOrCreate(
                cacheDir = cacheDir,
                itemKey = "item-abc",
                vatFile = vat,
                dek = key,
                mimeType = "video/mp4",
            )
            assertEquals(first.absolutePath, second.absolutePath)
            assertEquals(mtime, second.lastModified())

            PlaybackPlaintextCache.wipeAll(cacheDir)
            assertFalse(first.exists())
            assertTrue(!cacheDir.exists() || cacheDir.listFiles().isNullOrEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun getOrCreate_rewritesWhenSizeMismatch() {
        val dir = tmpDir()
        val cacheDir = File(dir, "playcache").also { it.mkdirs() }
        try {
            val vat = File(dir, "clip.vat")
            val key = KeyHierarchy.generateDek()
            val plain = ByteArray(2048) { it.toByte() }
            VaultCrypto.encryptBytes(plain, key, vat, dir)

            val stale = PlaybackPlaintextCache.cacheFile(cacheDir, "item-x", "video/webm")
            stale.writeBytes(ByteArray(16) { 7 })
            assertTrue(stale.exists())

            val fresh = PlaybackPlaintextCache.getOrCreate(
                cacheDir = cacheDir,
                itemKey = "item-x",
                vatFile = vat,
                dek = key,
                mimeType = "video/webm",
            )
            assertEquals(stale.absolutePath, fresh.absolutePath)
            assertEquals(plain.size.toLong(), fresh.length())
            assertTrue(fresh.readBytes().contentEquals(plain))
            assertTrue(fresh.name.endsWith(".webm"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun extensionForMime_mapsCommonVideoTypes() {
        assertEquals(".mp4", PlaybackPlaintextCache.extensionForMime("video/mp4"))
        assertEquals(".webm", PlaybackPlaintextCache.extensionForMime("video/webm"))
        assertEquals(".mkv", PlaybackPlaintextCache.extensionForMime("video/x-matroska"))
        assertEquals(".bin", PlaybackPlaintextCache.extensionForMime("video/unknown"))
    }
}
