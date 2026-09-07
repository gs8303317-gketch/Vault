package app.vault.workspace.ui.viewer

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import kotlin.math.min

/**
 * Encoding detection + soft truncate helpers for the premium text reader.
 * Pure JVM — unit-tested without Compose.
 */
object TextEncoding {
    /** Initial chars shown before "Load more". */
    const val INITIAL_CHARS = 512_000

    /** Each "Load more" extends the visible window by this many chars. */
    const val LOAD_MORE_CHARS = 512_000

    /** Hard cap on decoded chars held in memory (avoid OOM). */
    const val HARD_MAX_CHARS = 2_000_000

    /** Hard cap on ciphertext→plaintext bytes retained for decode. */
    const val HARD_MAX_BYTES = 2_500_000

    data class DecodeResult(
        val text: String,
        val encodingLabel: String,
        val bytesTruncated: Boolean,
        val originalByteLength: Int,
    )

    /**
     * Decode [bytes] trying UTF-8 (incl. BOM), UTF-16 LE/BE (BOM or heuristic),
     * then ISO-8859-1. May truncate input to [HARD_MAX_BYTES] before decode.
     */
    fun decode(bytes: ByteArray): DecodeResult {
        val originalLen = bytes.size
        val slice = if (bytes.size > HARD_MAX_BYTES) {
            bytes.copyOf(HARD_MAX_BYTES)
        } else {
            bytes
        }
        val bytesTruncated = originalLen > HARD_MAX_BYTES

        val bom = detectBom(slice)
        if (bom != null) {
            val body = slice.copyOfRange(bom.skip, slice.size)
            val decoded = String(body, bom.charset)
            return DecodeResult(
                text = softCapText(decoded),
                encodingLabel = bom.label,
                bytesTruncated = bytesTruncated,
                originalByteLength = originalLen,
            )
        }

        // Prefer UTF-8 when valid (or only sparse replacement chars).
        val utf8 = String(slice, StandardCharsets.UTF_8)
        if (isAcceptableUtf8(slice, utf8)) {
            return DecodeResult(
                text = softCapText(utf8),
                encodingLabel = "UTF-8",
                bytesTruncated = bytesTruncated,
                originalByteLength = originalLen,
            )
        }

        // UTF-16 without BOM: even length + many NULs in alternate bytes.
        if (slice.size >= 4 && slice.size % 2 == 0) {
            val leScore = utf16NulScore(slice, littleEndian = true)
            val beScore = utf16NulScore(slice, littleEndian = false)
            if (leScore > 0.3f || beScore > 0.3f) {
                val le = leScore >= beScore
                val cs = if (le) StandardCharsets.UTF_16LE else StandardCharsets.UTF_16BE
                val label = if (le) "UTF-16LE" else "UTF-16BE"
                return DecodeResult(
                    text = softCapText(String(slice, cs)),
                    encodingLabel = label,
                    bytesTruncated = bytesTruncated,
                    originalByteLength = originalLen,
                )
            }
        }

        // Latin-1 never fails; last resort for common Western text.
        return DecodeResult(
            text = softCapText(String(slice, StandardCharsets.ISO_8859_1)),
            encodingLabel = "ISO-8859-1",
            bytesTruncated = bytesTruncated,
            originalByteLength = originalLen,
        )
    }

    fun softCapText(text: String): String =
        if (text.length > HARD_MAX_CHARS) text.take(HARD_MAX_CHARS) else text

    /** Visible window length after [loadMoreCount] taps (0 = initial). */
    fun visibleLimit(fullLength: Int, loadMoreCount: Int): Int {
        val raw = INITIAL_CHARS + loadMoreCount.toLong() * LOAD_MORE_CHARS
        val capped = min(raw, HARD_MAX_CHARS.toLong()).toInt()
        return min(fullLength, capped)
    }

    fun canLoadMore(fullLength: Int, visibleLength: Int): Boolean =
        visibleLength < fullLength && visibleLength < HARD_MAX_CHARS

    /**
     * Mime types handled by the plain [TextFileViewer] (not Markdown / CSV / HTML).
     * Markdown, CSV/TSV, and HTML have dedicated Phase 3 viewers.
     */
    fun isPlainTextDocumentMime(mime: String): Boolean {
        val m = mime.lowercase()
        if (m == "text/markdown" ||
            m == "text/x-markdown" ||
            m == "text/csv" ||
            m == "text/comma-separated-values" ||
            m == "text/tab-separated-values" ||
            m == "text/html" ||
            m == "application/xhtml+xml" ||
            m == "application/csv"
        ) {
            return false
        }
        return m.startsWith("text/") ||
            m == "application/json" ||
            m == "application/xml" ||
            m == "application/javascript" ||
            m == "application/x-javascript" ||
            m == "application/rss+xml" ||
            m == "application/atom+xml"
    }

    /** @deprecated Prefer [isPlainTextDocumentMime] / [DocumentMime.viewerKind]. */
    fun isTextDocumentMime(mime: String): Boolean = isPlainTextDocumentMime(mime)

    private data class Bom(val charset: Charset, val label: String, val skip: Int)

    private fun detectBom(bytes: ByteArray): Bom? {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return Bom(StandardCharsets.UTF_8, "UTF-8", 3)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return Bom(StandardCharsets.UTF_16BE, "UTF-16BE", 2)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return Bom(StandardCharsets.UTF_16LE, "UTF-16LE", 2)
        }
        return null
    }

    private fun isAcceptableUtf8(bytes: ByteArray, decoded: String): Boolean {
        if (bytes.isEmpty()) return true
        // Structurally invalid sequences → U+FFFD. Allow a few (binary noise) but not many.
        val replacements = decoded.count { it == '\uFFFD' }
        if (replacements == 0) return true
        val ratio = replacements.toFloat() / decoded.length.coerceAtLeast(1)
        return ratio < 0.01f && replacements < 8
    }

    /** Fraction of 16-bit units whose high/low byte is NUL (ASCII-in-UTF16 heuristic). */
    private fun utf16NulScore(bytes: ByteArray, littleEndian: Boolean): Float {
        val units = bytes.size / 2
        if (units == 0) return 0f
        var nuls = 0
        var i = 0
        while (i + 1 < bytes.size) {
            val hi = if (littleEndian) bytes[i + 1] else bytes[i]
            if (hi == 0.toByte()) nuls++
            i += 2
        }
        return nuls.toFloat() / units
    }
}
