package app.vault.workspace.ui.nav

import app.vault.workspace.data.VaultCategory
import app.vault.workspace.data.VaultItem
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaQueueTest {

    private fun item(id: String, category: VaultCategory) = VaultItem(
        id = id,
        displayName = id,
        mimeType = "application/octet-stream",
        sizeBytes = 1L,
        category = category,
        createdAt = 0L,
        hasThumb = false,
    )

    @Test
    fun videoQueueExcludesAudio() {
        val items = listOf(
            item("v1", VaultCategory.VIDEO),
            item("a1", VaultCategory.AUDIO),
            item("v2", VaultCategory.VIDEO),
            item("i1", VaultCategory.IMAGE),
        )
        val q = mediaQueueFor(VaultCategory.VIDEO, items)
        assertEquals(listOf("v1", "v2"), q.map { it.id })
    }

    @Test
    fun audioQueueExcludesVideo() {
        val items = listOf(
            item("v1", VaultCategory.VIDEO),
            item("a1", VaultCategory.AUDIO),
            item("a2", VaultCategory.AUDIO),
        )
        val q = mediaQueueFor(VaultCategory.AUDIO, items)
        assertEquals(listOf("a1", "a2"), q.map { it.id })
    }

    @Test
    fun imageQueueOnlyImages() {
        val items = listOf(
            item("i1", VaultCategory.IMAGE),
            item("v1", VaultCategory.VIDEO),
            item("i2", VaultCategory.IMAGE),
        )
        val q = mediaQueueFor(VaultCategory.IMAGE, items)
        assertEquals(listOf("i1", "i2"), q.map { it.id })
    }
}
