package app.vault.workspace.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure helpers for in-vault still-image crop (Phase 3).
 * Normalized crop rect is left/top/right/bottom in 0..1 relative to bitmap.
 */
object ImageCrop {
    const val JPEG_QUALITY = 92

    data class NormRect(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) {
        val width: Float get() = (right - left).coerceAtLeast(0f)
        val height: Float get() = (bottom - top).coerceAtLeast(0f)
    }

    data class EncodedCrop(
        val bytes: ByteArray,
        val mimeType: String,
        val width: Int,
        val height: Int,
    )

    /** Inclusive-left/top, exclusive-right/bottom pixel crop (JVM-safe; no android.graphics.Rect). */
    data class IntRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        fun width(): Int = (right - left).coerceAtLeast(0)
        fun height(): Int = (bottom - top).coerceAtLeast(0)
    }

    /** Clamp and order a normalized crop so left<right, top<bottom, inside 0..1. */
    fun clampNormRect(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        minFraction: Float = 0.05f,
    ): NormRect {
        var l = left.coerceIn(0f, 1f)
        var t = top.coerceIn(0f, 1f)
        var r = right.coerceIn(0f, 1f)
        var b = bottom.coerceIn(0f, 1f)
        if (r < l) {
            val tmp = l
            l = r
            r = tmp
        }
        if (b < t) {
            val tmp = t
            t = b
            b = tmp
        }
        if (r - l < minFraction) {
            val mid = ((l + r) / 2f).coerceIn(minFraction / 2f, 1f - minFraction / 2f)
            l = (mid - minFraction / 2f).coerceAtLeast(0f)
            r = (l + minFraction).coerceAtMost(1f)
            l = (r - minFraction).coerceAtLeast(0f)
        }
        if (b - t < minFraction) {
            val mid = ((t + b) / 2f).coerceIn(minFraction / 2f, 1f - minFraction / 2f)
            t = (mid - minFraction / 2f).coerceAtLeast(0f)
            b = (t + minFraction).coerceAtMost(1f)
            t = (b - minFraction).coerceAtLeast(0f)
        }
        return NormRect(l, t, r, b)
    }

    /** Map normalized rect to pixel crop (right/bottom exclusive). */
    fun pixelRect(norm: NormRect, bitmapW: Int, bitmapH: Int): IntRect {
        require(bitmapW > 0 && bitmapH > 0)
        val n = clampNormRect(norm.left, norm.top, norm.right, norm.bottom)
        var left = (n.left * bitmapW).roundToInt().coerceIn(0, bitmapW - 1)
        var top = (n.top * bitmapH).roundToInt().coerceIn(0, bitmapH - 1)
        var right = (n.right * bitmapW).roundToInt().coerceIn(left + 1, bitmapW)
        var bottom = (n.bottom * bitmapH).roundToInt().coerceIn(top + 1, bitmapH)
        if (right <= left) right = min(bitmapW, left + 1)
        if (bottom <= top) bottom = min(bitmapH, top + 1)
        return IntRect(left, top, right, bottom)
    }

    /**
     * Choose compress format / mime for cropped output.
     * JPEG stays JPEG (~92); PNG / WebP → PNG; else JPEG.
     */
    fun outputFormat(sourceMime: String?): Pair<Bitmap.CompressFormat, String> {
        val m = sourceMime?.lowercase()?.trim().orEmpty()
        return when {
            m == "image/png" || m == "image/webp" ->
                Bitmap.CompressFormat.PNG to "image/png"
            m == "image/jpeg" || m == "image/jpg" ->
                Bitmap.CompressFormat.JPEG to "image/jpeg"
            else ->
                Bitmap.CompressFormat.JPEG to "image/jpeg"
        }
    }

    fun isGifMime(mime: String?): Boolean =
        mime.equals("image/gif", ignoreCase = true)

    /**
     * Decode [plaintext], crop by [norm], compress. Caller wipes returned bytes when done.
     * Does not recycle bitmaps from caller; recycles its own intermediates.
     */
    fun cropAndEncode(
        plaintext: ByteArray,
        sourceMime: String?,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        maxDecodeSide: Int = 8192,
    ): EncodedCrop {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(plaintext, 0, plaintext.size, bounds)
        val srcW = bounds.outWidth
        val srcH = bounds.outHeight
        require(srcW > 0 && srcH > 0) { "Cannot decode image bounds" }

        var sample = 1
        while (srcW / sample > maxDecodeSide || srcH / sample > maxDecodeSide) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeByteArray(plaintext, 0, plaintext.size, opts)
            ?: error("Cannot decode image")
        try {
            // Map norm rect onto the (possibly subsampled) bitmap.
            val norm = clampNormRect(left, top, right, bottom)
            val rect = pixelRect(norm, decoded.width, decoded.height)
            val w = rect.width().coerceAtLeast(1)
            val h = rect.height().coerceAtLeast(1)
            val cropped = Bitmap.createBitmap(decoded, rect.left, rect.top, w, h)
            try {
                val (format, mime) = outputFormat(sourceMime)
                val baos = ByteArrayOutputStream()
                val quality = if (format == Bitmap.CompressFormat.JPEG) JPEG_QUALITY else 100
                check(cropped.compress(format, quality, baos)) { "Compress failed" }
                return EncodedCrop(
                    bytes = baos.toByteArray(),
                    mimeType = mime,
                    width = cropped.width,
                    height = cropped.height,
                )
            } finally {
                if (!cropped.isRecycled) cropped.recycle()
            }
        } finally {
            if (!decoded.isRecycled) decoded.recycle()
        }
    }

    /** Default centered ~80% crop box. */
    fun defaultNormRect(): NormRect = NormRect(0.1f, 0.1f, 0.9f, 0.9f)

    fun moveNormRect(norm: NormRect, dx: Float, dy: Float): NormRect {
        val w = norm.width
        val h = norm.height
        var l = norm.left + dx
        var t = norm.top + dy
        l = l.coerceIn(0f, 1f - w)
        t = t.coerceIn(0f, 1f - h)
        return NormRect(l, t, l + w, t + h)
    }

    fun resizeFromCorner(
        norm: NormRect,
        corner: Corner,
        dx: Float,
        dy: Float,
        minFraction: Float = 0.05f,
    ): NormRect {
        var l = norm.left
        var t = norm.top
        var r = norm.right
        var b = norm.bottom
        when (corner) {
            Corner.TOP_LEFT -> {
                l = (l + dx).coerceIn(0f, r - minFraction)
                t = (t + dy).coerceIn(0f, b - minFraction)
            }
            Corner.TOP_RIGHT -> {
                r = (r + dx).coerceIn(l + minFraction, 1f)
                t = (t + dy).coerceIn(0f, b - minFraction)
            }
            Corner.BOTTOM_LEFT -> {
                l = (l + dx).coerceIn(0f, r - minFraction)
                b = (b + dy).coerceIn(t + minFraction, 1f)
            }
            Corner.BOTTOM_RIGHT -> {
                r = (r + dx).coerceIn(l + minFraction, 1f)
                b = (b + dy).coerceIn(t + minFraction, 1f)
            }
        }
        return clampNormRect(l, t, r, b, minFraction)
    }

    enum class Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
}
