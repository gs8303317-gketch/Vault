package app.vault.workspace.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlaybackPlaintextCacheTest {

    private fun tmpDir(): File =
        File(System.getProperty("java.io.tmpdir"), "vault-playcache-${System.nanoTime()}")
            .also { it.mkdirs() }

    @Test
    fun wipeAll_clearsFilesAndDirectory() {
        val dir = tmpDir()
        val cacheDir = File(dir, "playcache").also { it.mkdirs() }
        try {
            val leftover = File(cacheDir, "playcache_deadbeef.bin")
            leftover.writeBytes(ByteArray(64) { 9 })
            assertTrue(leftover.exists())

            PlaybackPlaintextCache.wipeAll(cacheDir)
            assertFalse(leftover.exists())
            assertTrue(!cacheDir.exists() || cacheDir.listFiles().isNullOrEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun wipeAll_clearsNestedSeekprepStyleDirs() {
        val dir = tmpDir()
        val seekprep = File(dir, "seekprep").also { it.mkdirs() }
        try {
            val nested = File(seekprep, "item-1").also { it.mkdirs() }
            File(nested, "plain.bin").writeBytes(ByteArray(32) { 1 })
            PlaybackPlaintextCache.wipeAll(seekprep)
            assertTrue(!seekprep.exists() || seekprep.listFiles().isNullOrEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }
}
