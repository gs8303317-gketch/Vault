package app.vault.workspace.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekReadyDefaultsTest {

    @Test
    fun entity_defaultSeekReadyTrue() {
        val e = VaultItemEntity(
            id = "a",
            nameCipher = byteArrayOf(1),
            mimeType = "image/jpeg",
            sizeBytes = 10,
            category = "IMAGE",
            createdAt = 1L,
            dekWrap = byteArrayOf(2),
        )
        assertTrue(e.seekReady)
    }

    @Test
    fun videoImport_startsSeekReadyFalse() {
        val mime = "video/mp4"
        val isVideo = mime.startsWith("video/", ignoreCase = true)
        val seekReady = !isVideo
        assertFalse(seekReady)
        assertEquals(VaultCategory.VIDEO, VaultCategory.fromMime(mime))
    }

    @Test
    fun nonVideo_seekReadyTrue() {
        for (mime in listOf("audio/mpeg", "image/png", "application/pdf", "text/plain")) {
            val seekReady = !mime.startsWith("video/", ignoreCase = true)
            assertTrue(mime, seekReady)
        }
    }

    @Test
    fun vaultItem_defaultSeekReadyTrue() {
        val item = VaultItem(
            id = "x",
            displayName = "n",
            mimeType = "audio/mp4",
            sizeBytes = 1,
            category = VaultCategory.AUDIO,
            createdAt = 0,
            hasThumb = false,
        )
        assertTrue(item.seekReady)
    }

    @Test
    fun equals_includesSeekReady() {
        val a = VaultItemEntity(
            id = "a",
            nameCipher = byteArrayOf(1),
            mimeType = "video/mp4",
            sizeBytes = 10,
            category = "VIDEO",
            createdAt = 1L,
            dekWrap = byteArrayOf(2),
            seekReady = false,
        )
        val b = a.copy(seekReady = true)
        assertFalse(a == b)
    }
}
