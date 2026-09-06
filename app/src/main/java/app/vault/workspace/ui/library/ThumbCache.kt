package app.vault.workspace.ui.library

import android.graphics.Bitmap
import java.util.LinkedHashMap

/**
 * Simple in-memory LRU cache for decrypted library thumbnails.
 * Cleared on session lock so bitmaps do not outlive the unlocked vault.
 */
object ThumbCache {
    private const val MAX_ENTRIES = 64

    private val lock = Any()
    private val cache = object : LinkedHashMap<String, Bitmap>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean =
            size > MAX_ENTRIES
    }

    /** Returns a cached bitmap without loading. */
    fun peek(id: String): Bitmap? = synchronized(lock) { cache[id] }

    /**
     * Returns a cached thumbnail or loads via [loader] and stores the result.
     * Loader runs outside the lock so decrypt work does not block other lookups.
     */
    suspend fun get(id: String, loader: suspend () -> Bitmap?): Bitmap? {
        synchronized(lock) {
            cache[id]?.let { return it }
        }
        val bitmap = loader() ?: return null
        synchronized(lock) {
            cache[id]?.let { return it }
            cache[id] = bitmap
        }
        return bitmap
    }

    fun clear() {
        synchronized(lock) {
            cache.clear()
        }
    }
}
