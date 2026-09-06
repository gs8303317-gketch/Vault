package app.vault.workspace.image

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageCropTest {
    @Test
    fun clampNormRectOrdersAndEnforcesMinSize() {
        val n = ImageCrop.clampNormRect(0.8f, 0.9f, 0.2f, 0.1f)
        assertTrue(n.left < n.right)
        assertTrue(n.top < n.bottom)
        assertTrue(n.width >= 0.05f - 1e-4f)
        assertTrue(n.height >= 0.05f - 1e-4f)
        assertTrue(n.left >= 0f && n.right <= 1f)
    }

    @Test
    fun pixelRectMapsFullImage() {
        val r = ImageCrop.pixelRect(ImageCrop.NormRect(0f, 0f, 1f, 1f), 100, 50)
        assertEquals(0, r.left)
        assertEquals(0, r.top)
        assertEquals(100, r.right)
        assertEquals(50, r.bottom)
    }

    @Test
    fun pixelRectCenterCrop() {
        val r = ImageCrop.pixelRect(ImageCrop.NormRect(0.25f, 0.25f, 0.75f, 0.75f), 200, 200)
        assertEquals(50, r.left)
        assertEquals(50, r.top)
        assertEquals(150, r.right)
        assertEquals(150, r.bottom)
    }

    @Test
    fun outputFormatJpegPngWebp() {
        assertEquals(Bitmap.CompressFormat.JPEG, ImageCrop.outputFormat("image/jpeg").first)
        assertEquals("image/jpeg", ImageCrop.outputFormat("image/jpg").second)
        assertEquals(Bitmap.CompressFormat.PNG, ImageCrop.outputFormat("image/png").first)
        assertEquals("image/png", ImageCrop.outputFormat("image/webp").second)
        assertEquals(Bitmap.CompressFormat.JPEG, ImageCrop.outputFormat("image/heic").first)
    }

    @Test
    fun isGifMime() {
        assertTrue(ImageCrop.isGifMime("image/gif"))
        assertTrue(ImageCrop.isGifMime("Image/GIF"))
        assertFalse(ImageCrop.isGifMime("image/jpeg"))
    }

    @Test
    fun moveNormRectStaysInBounds() {
        val base = ImageCrop.NormRect(0.1f, 0.1f, 0.5f, 0.5f)
        val moved = ImageCrop.moveNormRect(base, 0.8f, 0.8f)
        assertEquals(0.4f, moved.width, 1e-4f)
        assertEquals(0.4f, moved.height, 1e-4f)
        assertEquals(0.6f, moved.left, 1e-4f)
        assertEquals(0.6f, moved.top, 1e-4f)
    }

    @Test
    fun resizeFromCornerKeepsMin() {
        val base = ImageCrop.defaultNormRect()
        val shrunk = ImageCrop.resizeFromCorner(
            base,
            ImageCrop.Corner.BOTTOM_RIGHT,
            -0.9f,
            -0.9f,
            minFraction = 0.05f,
        )
        assertTrue(shrunk.width >= 0.05f - 1e-4f)
        assertTrue(shrunk.height >= 0.05f - 1e-4f)
    }
}
