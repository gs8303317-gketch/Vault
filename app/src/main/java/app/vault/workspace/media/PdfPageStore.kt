package app.vault.workspace.media

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists last-read PDF page index (0-based) per vault item id for resume on reopen.
 */
class PdfPageStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getPageIndex(itemId: String): Int =
        prefs.getInt(key(itemId), 0).coerceAtLeast(0)

    fun savePageIndex(itemId: String, pageIndex: Int, pageCount: Int) {
        if (pageCount <= 0) {
            clear(itemId)
            return
        }
        val index = pageIndex.coerceIn(0, pageCount - 1)
        // No need to persist page 0 as default
        if (index <= 0) {
            clear(itemId)
            return
        }
        prefs.edit().putInt(key(itemId), index).apply()
    }

    fun clear(itemId: String) {
        prefs.edit().remove(key(itemId)).apply()
    }

    companion object {
        private const val PREFS = "vault_pdf_pages"

        fun key(itemId: String): String = "page_$itemId"

        /** Clamp a stored 0-based index into [0, pageCount). */
        fun resumePageIndex(savedIndex: Int, pageCount: Int): Int {
            if (pageCount <= 0) return 0
            return savedIndex.coerceIn(0, pageCount - 1)
        }
    }
}
