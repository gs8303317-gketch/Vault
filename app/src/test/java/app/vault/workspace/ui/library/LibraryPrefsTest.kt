package app.vault.workspace.ui.library

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LibraryPrefsTest {
    @Test
    fun persistsViewModeAndSort() {
        val ctx = RuntimeEnvironment.getApplication()
        val prefs = LibraryPrefs(ctx)
        assertEquals(LibraryViewMode.GRID, prefs.getViewMode())
        assertEquals(LibrarySort.NEWEST, prefs.getSort())

        prefs.setViewMode(LibraryViewMode.LIST)
        prefs.setSort(LibrarySort.NAME_AZ)

        val again = LibraryPrefs(ctx)
        assertEquals(LibraryViewMode.LIST, again.getViewMode())
        assertEquals(LibrarySort.NAME_AZ, again.getSort())
    }
}
