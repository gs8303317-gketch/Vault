package app.vault.workspace.auth

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistent PIN lockout.
 * Attempts 1–2: free; 3=30s; 4=1m; 5=5m; 6=15m; 7+=1h
 * Survives process death via SharedPreferences.
 */
class LockoutStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var failedAttempts: Int
        get() = prefs.getInt(KEY_ATTEMPTS, 0)
        private set(value) = prefs.edit().putInt(KEY_ATTEMPTS, value).apply()

    var lockUntilEpochMs: Long
        get() = prefs.getLong(KEY_UNTIL, 0L)
        private set(value) = prefs.edit().putLong(KEY_UNTIL, value).apply()

    fun isLocked(nowMs: Long = System.currentTimeMillis()): Boolean =
        nowMs < lockUntilEpochMs

    fun remainingLockMs(nowMs: Long = System.currentTimeMillis()): Long =
        (lockUntilEpochMs - nowMs).coerceAtLeast(0L)

    fun recordFailure(nowMs: Long = System.currentTimeMillis()) {
        val next = failedAttempts + 1
        failedAttempts = next
        val delay = delayForAttempt(next)
        if (delay > 0L) {
            lockUntilEpochMs = nowMs + delay
        }
    }

    fun recordSuccess() {
        failedAttempts = 0
        lockUntilEpochMs = 0L
    }

    companion object {
        private const val PREFS = "vault_lockout"
        private const val KEY_ATTEMPTS = "failed_attempts"
        private const val KEY_UNTIL = "lock_until_epoch"

        fun delayForAttempt(attempt: Int): Long = when {
            attempt <= 2 -> 0L
            attempt == 3 -> 30_000L
            attempt == 4 -> 60_000L
            attempt == 5 -> 5 * 60_000L
            attempt == 6 -> 15 * 60_000L
            else -> 60 * 60_000L // 7+
        }
    }
}
