package app.vault.workspace.ui.viewer

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageViewerClampTest {
    @Test
    fun clampAtIdentityZoomCentered() {
        val o = clampImageOffset(
            Offset(50f, 50f),
            scale = 1f,
            contentW = 200f,
            contentH = 200f,
            containerW = 400f,
            containerH = 400f,
        )
        assertEquals(Offset.Zero, o)
    }

    @Test
    fun clampWhenZoomed() {
        val o = clampImageOffset(
            Offset(500f, -500f),
            scale = 2.5f,
            contentW = 200f,
            contentH = 200f,
            containerW = 400f,
            containerH = 400f,
        )
        // scaled 500 vs container 400 → max pan (500-400)/2 = 50
        assertEquals(50f, o.x, 0.01f)
        assertEquals(-50f, o.y, 0.01f)
    }

    @Test
    fun fillModeAllowsPanAtScaleOne() {
        val maxX = maxImagePan(1f, contentSide = 800f, containerSide = 400f)
        assertEquals(200f, maxX, 0.01f)
        val o = clampImageOffset(
            Offset(300f, 0f),
            scale = 1f,
            contentW = 800f,
            contentH = 400f,
            containerW = 400f,
            containerH = 400f,
        )
        assertEquals(200f, o.x, 0.01f)
        assertEquals(0f, o.y, 0.01f)
    }

    @Test
    fun fitDisplaySizeContainsImage() {
        val (w, h) = imageDisplaySize(100f, 200f, 400f, 400f, ImageFitMode.FIT, 0)
        assertEquals(200f, w, 0.01f)
        assertEquals(400f, h, 0.01f)
    }

    @Test
    fun fillDisplaySizeCoversContainer() {
        val (w, h) = imageDisplaySize(100f, 200f, 400f, 400f, ImageFitMode.FILL, 0)
        assertTrue(w >= 400f - 0.01f)
        assertTrue(h >= 400f - 0.01f)
    }

    @Test
    fun widthDisplaySizeMatchesContainerWidth() {
        val (w, h) = imageDisplaySize(100f, 200f, 400f, 400f, ImageFitMode.WIDTH, 0)
        assertEquals(400f, w, 0.01f)
        assertEquals(800f, h, 0.01f)
    }

    @Test
    fun zoomTowardKeepsTapFixedFromIdentity() {
        val tapRel = Offset(40f, -20f)
        val off = zoomTowardOffset(Offset.Zero, 1f, 2.5f, tapRel)
        // offset2 = tapRel * (1 - 2.5) = -1.5 * tapRel
        assertEquals(-60f, off.x, 0.01f)
        assertEquals(30f, off.y, 0.01f)
    }
}
