package app.vault.workspace.ui.viewer

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageCropOverlayTest {
    @Test
    fun resolveImageRectFallsBackToFullOverlay() {
        val r = resolveImageRect(null, 200f, 100f)
        assertEquals(0f, r.left, 0.01f)
        assertEquals(0f, r.top, 0.01f)
        assertEquals(200f, r.right, 0.01f)
        assertEquals(100f, r.bottom, 0.01f)
    }

    @Test
    fun resolveImageRectKeepsLetterbox() {
        val r = resolveImageRect(Rect(10f, 20f, 110f, 80f), 200f, 100f)
        assertEquals(10f, r.left, 0.01f)
        assertEquals(20f, r.top, 0.01f)
        assertEquals(110f, r.right, 0.01f)
        assertEquals(80f, r.bottom, 0.01f)
    }
}
