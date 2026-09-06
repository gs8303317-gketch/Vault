package app.vault.workspace.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for EXIF strip helpers (no Robolectric device needed).
 * Tag list + mime gating — actual ExifInterface I/O needs Android runtime.
 */
class ImageExifStripTest {
    @Test
    fun jpegMimeDetection() {
        assertTrue(ImageExifStrip.isJpegMime("image/jpeg"))
        assertTrue(ImageExifStrip.isJpegMime("image/jpg"))
        assertTrue(ImageExifStrip.isJpegMime("IMAGE/JPEG"))
        assertFalse(ImageExifStrip.isJpegMime("image/png"))
        assertFalse(ImageExifStrip.isJpegMime("image/heic"))
        assertFalse(ImageExifStrip.isJpegMime(null))
    }

    @Test
    fun shouldStripOnlyJpeg() {
        assertTrue(ImageExifStrip.shouldStripOnExport("image/jpeg"))
        assertFalse(ImageExifStrip.shouldStripOnExport("image/png"))
        assertFalse(ImageExifStrip.shouldStripOnExport("video/mp4"))
        assertFalse(ImageExifStrip.shouldStripOnExport("application/pdf"))
    }

    @Test
    fun locationTagsIncludeCoreGps() {
        val tags = ImageExifStrip.LOCATION_TAGS
        assertTrue(tags.size >= 10)
        assertTrue(tags.contains(androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE))
        assertTrue(tags.contains(androidx.exifinterface.media.ExifInterface.TAG_GPS_LONGITUDE))
        assertTrue(tags.contains(androidx.exifinterface.media.ExifInterface.TAG_GPS_ALTITUDE))
        // Stable count for regression (document intentional set)
        assertEquals(31, tags.size)
    }
}
