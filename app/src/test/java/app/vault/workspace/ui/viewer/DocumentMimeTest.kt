package app.vault.workspace.ui.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentMimeTest {
    @Test
    fun routesMarkdownCsvHtmlOffice() {
        assertEquals(
            DocumentMime.ViewerKind.MARKDOWN,
            DocumentMime.viewerKind("text/markdown", "notes.md"),
        )
        assertEquals(
            DocumentMime.ViewerKind.MARKDOWN,
            DocumentMime.viewerKind("application/octet-stream", "readme.md"),
        )
        assertEquals(
            DocumentMime.ViewerKind.CSV,
            DocumentMime.viewerKind("text/csv", "data.csv"),
        )
        assertEquals(
            DocumentMime.ViewerKind.CSV,
            DocumentMime.viewerKind("text/plain", "sheet.tsv"),
        )
        assertEquals(
            DocumentMime.ViewerKind.HTML,
            DocumentMime.viewerKind("text/html", "page.html"),
        )
        assertEquals(
            DocumentMime.ViewerKind.OFFICE_TEXT,
            DocumentMime.viewerKind(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "a.docx",
            ),
        )
        assertEquals(
            DocumentMime.ViewerKind.OFFICE_TEXT,
            DocumentMime.viewerKind("application/zip", "deck.pptx"),
        )
        assertEquals(
            DocumentMime.ViewerKind.PLAIN_TEXT,
            DocumentMime.viewerKind("text/plain", "a.txt"),
        )
        assertEquals(
            DocumentMime.ViewerKind.OTHER,
            DocumentMime.viewerKind("application/msword", "legacy.doc"),
        )
        assertEquals(
            DocumentMime.ViewerKind.OTHER,
            DocumentMime.viewerKind("application/epub+zip", "book.epub"),
        )
        assertEquals(
            DocumentMime.ViewerKind.PDF,
            DocumentMime.viewerKind("application/pdf", "x.pdf"),
        )
    }

    @Test
    fun resolveImportMimeFromExtension() {
        assertEquals(
            "text/markdown",
            DocumentMime.resolveImportMime("application/octet-stream", "notes.md"),
        )
        assertEquals(
            "text/csv",
            DocumentMime.resolveImportMime(null, "data.csv"),
        )
        assertEquals(
            "image/png",
            DocumentMime.resolveImportMime("image/png", "ignored.md"),
        )
        assertEquals(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            DocumentMime.resolveImportMime("application/zip", "letter.docx"),
        )
    }

    @Test
    fun looksLikeTextHeuristic() {
        assertTrue(DocumentMime.looksLikeText("hello world\nline 2".toByteArray()))
        assertFalse(DocumentMime.looksLikeText(byteArrayOf(0, 1, 2, 3, 4, 5, 0, 0, 0)))
        assertTrue(DocumentMime.looksLikeText(ByteArray(0)))
    }

    @Test
    fun categoryLabels() {
        assertTrue(DocumentMime.categoryLabel("application/epub+zip", "a.epub").contains("EPUB"))
        assertTrue(DocumentMime.categoryLabel("text/markdown", "a.md").contains("Markdown"))
    }
}
