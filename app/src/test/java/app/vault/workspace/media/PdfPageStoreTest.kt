package app.vault.workspace.media

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfPageStoreTest {
    @Test
    fun resumeClampsWithinRange() {
        assertEquals(0, PdfPageStore.resumePageIndex(0, 10))
        assertEquals(4, PdfPageStore.resumePageIndex(4, 10))
        assertEquals(9, PdfPageStore.resumePageIndex(9, 10))
    }

    @Test
    fun resumeClampsOutOfRange() {
        assertEquals(0, PdfPageStore.resumePageIndex(-3, 10))
        assertEquals(9, PdfPageStore.resumePageIndex(99, 10))
    }

    @Test
    fun resumeEmptyDocument() {
        assertEquals(0, PdfPageStore.resumePageIndex(5, 0))
        assertEquals(0, PdfPageStore.resumePageIndex(5, -1))
    }
}
