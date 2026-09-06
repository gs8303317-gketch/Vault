package app.vault.workspace.media

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists last playback position per vault item id (offline resume).
 * Positions near the start or end are treated as "finished" / not worth resuming.
 */
class PlaybackPositionStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getPositionMs(itemId: String): Long =
        prefs.getLong(key(itemId), 0L).coerceAtLeast(0L)

    fun savePositionMs(itemId: String, positionMs: Long, durationMs: Long) {
        val pos = positionMs.coerceAtLeast(0L)
        val dur = durationMs.coerceAtLeast(0L)
        if (!shouldPersist(pos, dur)) {
            clear(itemId)
            return
        }
        prefs.edit().putLong(key(itemId), pos).apply()
    }

    fun clear(itemId: String) {
        prefs.edit().remove(key(itemId)).apply()
    }

    companion object {
        private const val PREFS = "vault_playback_positions"
        private const val MIN_RESUME_MS = 5_000L
        private const val END_MARGIN_MS = 5_000L

        fun key(itemId: String): String = "pos_$itemId"

        /** Resume only if we've watched a bit and aren't essentially finished. */
        fun shouldPersist(positionMs: Long, durationMs: Long): Boolean {
            if (positionMs < MIN_RESUME_MS) return false
            if (durationMs > 0L && positionMs >= durationMs - END_MARGIN_MS) return false
            return true
        }

        fun resumePosition(savedMs: Long, durationMs: Long): Long {
            if (!shouldPersist(savedMs, durationMs) && savedMs < MIN_RESUME_MS) return 0L
            if (durationMs > 0L && savedMs >= durationMs - END_MARGIN_MS) return 0L
            return savedMs.coerceAtLeast(0L)
        }
    }
}
