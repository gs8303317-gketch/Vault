package app.vault.workspace.ui.viewer

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfPageClampTest {
    @Test
    fun clampsWithinRange() {
        assertEquals(0, clampPdfPageIndex(1, 10))
        assertEquals(9, clampPdfPageIndex(10, 10))
        assertEquals(4, clampPdfPageIndex(5, 10))
    }

    @Test
    fun clampsOutOfRange() {
        assertEquals(0, clampPdfPageIndex(0, 10))
        assertEquals(0, clampPdfPageIndex(-3, 10))
        assertEquals(9, clampPdfPageIndex(99, 10))
    }

    @Test
    fun emptyDocument() {
        assertEquals(0, clampPdfPageIndex(1, 0))
        assertEquals(0, clampPdfPageIndex(5, -1))
    }
}
