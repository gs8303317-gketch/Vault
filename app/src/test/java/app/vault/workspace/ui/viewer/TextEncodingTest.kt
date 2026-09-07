package app.vault.workspace.ui.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class TextEncodingTest {
    @Test
    fun decodeUtf8Plain() {
        val r = TextEncoding.decode("hello vault".toByteArray(StandardCharsets.UTF_8))
        assertEquals("hello vault", r.text)
        assertEquals("UTF-8", r.encodingLabel)
        assertFalse(r.bytesTruncated)
    }

    @Test
    fun decodeUtf8Bom() {
        val body = "café".toByteArray(StandardCharsets.UTF_8)
        val withBom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + body
        val r = TextEncoding.decode(withBom)
        assertEquals("café", r.text)
        assertEquals("UTF-8", r.encodingLabel)
    }

    @Test
    fun decodeUtf16LeBom() {
        val body = "Hi".toByteArray(StandardCharsets.UTF_16LE)
        val withBom = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + body
        val r = TextEncoding.decode(withBom)
        assertEquals("Hi", r.text)
        assertEquals("UTF-16LE", r.encodingLabel)
    }

    @Test
    fun decodeLatin1Fallback() {
        // 0xE9 is é in Latin-1 but invalid as standalone UTF-8 continuation → U+FFFD
        val bytes = byteArrayOf('c'.code.toByte(), 0xE9.toByte(), 'e'.code.toByte())
        val r = TextEncoding.decode(bytes)
        assertEquals("ISO-8859-1", r.encodingLabel)
        assertEquals("cée", r.text)
    }

    @Test
    fun visibleLimitAndLoadMore() {
        assertEquals(100, TextEncoding.visibleLimit(100, 0))
        assertEquals(TextEncoding.INITIAL_CHARS, TextEncoding.visibleLimit(5_000_000, 0))
        val afterOne = TextEncoding.visibleLimit(5_000_000, 1)
        assertEquals(TextEncoding.INITIAL_CHARS + TextEncoding.LOAD_MORE_CHARS, afterOne)
        assertTrue(TextEncoding.canLoadMore(5_000_000, TextEncoding.INITIAL_CHARS))
        assertFalse(TextEncoding.canLoadMore(100, 100))
        val atCap = TextEncoding.visibleLimit(5_000_000, 100)
        assertEquals(TextEncoding.HARD_MAX_CHARS, atCap)
        assertFalse(TextEncoding.canLoadMore(5_000_000, TextEncoding.HARD_MAX_CHARS))
    }

    @Test
    fun softCapText() {
        val big = "x".repeat(TextEncoding.HARD_MAX_CHARS + 50)
        assertEquals(TextEncoding.HARD_MAX_CHARS, TextEncoding.softCapText(big).length)
    }

    @Test
    fun isPlainTextDocumentMime() {
        assertTrue(TextEncoding.isPlainTextDocumentMime("text/plain"))
        assertTrue(TextEncoding.isPlainTextDocumentMime("application/json"))
        assertTrue(TextEncoding.isPlainTextDocumentMime("application/xml"))
        assertFalse(TextEncoding.isPlainTextDocumentMime("TEXT/HTML"))
        assertFalse(TextEncoding.isPlainTextDocumentMime("text/markdown"))
        assertFalse(TextEncoding.isPlainTextDocumentMime("text/csv"))
        assertFalse(TextEncoding.isPlainTextDocumentMime("application/pdf"))
        assertFalse(TextEncoding.isPlainTextDocumentMime("image/png"))
        // Deprecated alias tracks plain-text routing.
        assertTrue(TextEncoding.isTextDocumentMime("text/plain"))
        assertFalse(TextEncoding.isTextDocumentMime("text/html"))
    }
}
