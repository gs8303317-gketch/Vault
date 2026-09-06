package app.vault.workspace.auth

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists lock UX metadata (type + PIN length). Secrets live only as KEK material
 * derived at unlock time — never stored here.
 */
class LockPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var lockType: LockType
        get() {
            val raw = prefs.getString(KEY_TYPE, LockType.PIN.name) ?: LockType.PIN.name
            return runCatching { LockType.valueOf(raw) }.getOrDefault(LockType.PIN)
        }
        set(value) {
            prefs.edit().putString(KEY_TYPE, value.name).apply()
        }

    /**
     * Chosen PIN length for auto-submit on unlock (4–6). Default 4 for vaults
     * created before variable-length PIN.
     */
    var pinLength: Int
        get() = prefs.getInt(KEY_PIN_LENGTH, LockRules.PIN_MIN)
            .coerceIn(LockRules.PIN_MIN, LockRules.PIN_MAX)
        set(value) {
            prefs.edit()
                .putInt(KEY_PIN_LENGTH, value.coerceIn(LockRules.PIN_MIN, LockRules.PIN_MAX))
                .apply()
        }

    fun setLock(type: LockType, pinLength: Int? = null) {
        val editor = prefs.edit().putString(KEY_TYPE, type.name)
        if (type == LockType.PIN) {
            val len = (pinLength ?: this.pinLength).coerceIn(LockRules.PIN_MIN, LockRules.PIN_MAX)
            editor.putInt(KEY_PIN_LENGTH, len)
        }
        editor.apply()
    }

    companion object {
        private const val PREFS = "vault_lock_prefs"
        private const val KEY_TYPE = "lock_type"
        private const val KEY_PIN_LENGTH = "pin_length"
    }
}
