package app.vault.workspace.ui.library

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists library view mode and sort across exit/login (mirrors [app.vault.workspace.media.PlaybackPositionStore]).
 */
class LibraryPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getViewMode(): LibraryViewMode {
        val raw = prefs.getString(KEY_VIEW_MODE, LibraryViewMode.GRID.name) ?: LibraryViewMode.GRID.name
        return runCatching { LibraryViewMode.valueOf(raw) }.getOrDefault(LibraryViewMode.GRID)
    }

    fun setViewMode(mode: LibraryViewMode) {
        prefs.edit().putString(KEY_VIEW_MODE, mode.name).apply()
    }

    fun getSort(): LibrarySort {
        val raw = prefs.getString(KEY_SORT, LibrarySort.NEWEST.name) ?: LibrarySort.NEWEST.name
        return runCatching { LibrarySort.valueOf(raw) }.getOrDefault(LibrarySort.NEWEST)
    }

    fun setSort(sort: LibrarySort) {
        prefs.edit().putString(KEY_SORT, sort.name).apply()
    }

    companion object {
        private const val PREFS = "vault_library_prefs"
        private const val KEY_VIEW_MODE = "view_mode"
        private const val KEY_SORT = "sort"
    }
}
