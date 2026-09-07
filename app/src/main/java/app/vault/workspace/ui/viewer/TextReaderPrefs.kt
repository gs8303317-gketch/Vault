package app.vault.workspace.ui.viewer

import android.content.Context
import android.content.SharedPreferences

enum class TextReaderTheme {
    DARK,
    SEPIA,
    LIGHT,
}

/**
 * Persists text-reader chrome prefs (font size, theme, wrap, mono, line numbers).
 * Global across files — mirrors [app.vault.workspace.ui.library.LibraryPrefs].
 */
class TextReaderPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getFontSp(): Float =
        prefs.getFloat(KEY_FONT_SP, DEFAULT_FONT_SP).coerceIn(MIN_FONT_SP, MAX_FONT_SP)

    fun setFontSp(sp: Float) {
        prefs.edit().putFloat(KEY_FONT_SP, sp.coerceIn(MIN_FONT_SP, MAX_FONT_SP)).apply()
    }

    fun getTheme(): TextReaderTheme {
        val raw = prefs.getString(KEY_THEME, TextReaderTheme.DARK.name) ?: TextReaderTheme.DARK.name
        return runCatching { TextReaderTheme.valueOf(raw) }.getOrDefault(TextReaderTheme.DARK)
    }

    fun setTheme(theme: TextReaderTheme) {
        prefs.edit().putString(KEY_THEME, theme.name).apply()
    }

    fun getWrap(): Boolean = prefs.getBoolean(KEY_WRAP, true)

    fun setWrap(wrap: Boolean) {
        prefs.edit().putBoolean(KEY_WRAP, wrap).apply()
    }

    fun getMonospace(): Boolean = prefs.getBoolean(KEY_MONO, true)

    fun setMonospace(mono: Boolean) {
        prefs.edit().putBoolean(KEY_MONO, mono).apply()
    }

    fun getLineNumbers(): Boolean = prefs.getBoolean(KEY_LINE_NUMBERS, false)

    fun setLineNumbers(show: Boolean) {
        prefs.edit().putBoolean(KEY_LINE_NUMBERS, show).apply()
    }

    companion object {
        private const val PREFS = "vault_text_reader_prefs"
        private const val KEY_FONT_SP = "font_sp"
        private const val KEY_THEME = "theme"
        private const val KEY_WRAP = "wrap"
        private const val KEY_MONO = "monospace"
        private const val KEY_LINE_NUMBERS = "line_numbers"

        const val DEFAULT_FONT_SP = 15f
        const val MIN_FONT_SP = 10f
        const val MAX_FONT_SP = 28f
        const val FONT_STEP_SP = 1f
    }
}
