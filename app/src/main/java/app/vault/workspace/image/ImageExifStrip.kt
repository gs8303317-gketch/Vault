package app.vault.workspace.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

/**
 * Strip location EXIF from JPEG exports (Phase 3).
 * Prefer AndroidX ExifInterface null-out + saveAttributes; fall back to
 * decode+JPEG recompress (drops all EXIF) if attribute save fails.
 *
 * HEIC is not stripped here (encode support varies by OEM) — callers skip it.
 */
object ImageExifStrip {
    /** GPS / location-related ExifInterface tags cleared on export. */
    val LOCATION_TAGS: List<String> = listOf(
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_PROCESSING_METHOD,
        ExifInterface.TAG_GPS_AREA_INFORMATION,
        ExifInterface.TAG_GPS_DEST_LATITUDE,
        ExifInterface.TAG_GPS_DEST_LATITUDE_REF,
        ExifInterface.TAG_GPS_DEST_LONGITUDE,
        ExifInterface.TAG_GPS_DEST_LONGITUDE_REF,
        ExifInterface.TAG_GPS_DEST_BEARING,
        ExifInterface.TAG_GPS_DEST_BEARING_REF,
        ExifInterface.TAG_GPS_DEST_DISTANCE,
        ExifInterface.TAG_GPS_DEST_DISTANCE_REF,
        ExifInterface.TAG_GPS_IMG_DIRECTION,
        ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
        ExifInterface.TAG_GPS_MAP_DATUM,
        ExifInterface.TAG_GPS_MEASURE_MODE,
        ExifInterface.TAG_GPS_SATELLITES,
        ExifInterface.TAG_GPS_SPEED,
        ExifInterface.TAG_GPS_SPEED_REF,
        ExifInterface.TAG_GPS_STATUS,
        ExifInterface.TAG_GPS_TRACK,
        ExifInterface.TAG_GPS_TRACK_REF,
        ExifInterface.TAG_GPS_VERSION_ID,
        ExifInterface.TAG_GPS_DIFFERENTIAL,
        ExifInterface.TAG_GPS_H_POSITIONING_ERROR,
    )

    fun isJpegMime(mime: String?): Boolean {
        val m = mime?.lowercase()?.trim().orEmpty()
        return m == "image/jpeg" || m == "image/jpg"
    }

    /** True when export should attempt location EXIF strip. */
    fun shouldStripOnExport(mime: String?): Boolean = isJpegMime(mime)

    /**
     * Copy [input] → [output] with location EXIF tags cleared.
     * Returns true if strip path succeeded (attributes or recompress).
     */
    fun stripJpegFile(input: File, output: File): Boolean {
        require(input.exists() && input.length() > 0L) { "Missing JPEG input" }
        // First try attribute null-out on a byte-identical copy (preserves image data).
        input.copyTo(output, overwrite = true)
        return try {
            val exif = ExifInterface(output.absolutePath)
            for (tag in LOCATION_TAGS) {
                exif.setAttribute(tag, null)
            }
            exif.saveAttributes()
            true
        } catch (_: Exception) {
            // Fallback: decode + JPEG recompress drops all EXIF.
            recompressWithoutExif(input, output)
        }
    }

    private fun recompressWithoutExif(input: File, output: File): Boolean {
        val bmp = BitmapFactory.decodeFile(input.absolutePath) ?: return false
        return try {
            FileOutputStream(output).use { fos ->
                bmp.compress(Bitmap.CompressFormat.JPEG, ImageCrop.JPEG_QUALITY, fos)
                fos.fd.sync()
            }
            true
        } catch (_: Exception) {
            false
        } finally {
            if (!bmp.isRecycled) bmp.recycle()
        }
    }
}
