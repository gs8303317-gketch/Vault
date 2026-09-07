package app.vault.workspace.ui.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TextReaderPrefsTest {
    @Test
    fun persistsReaderChromePrefs() {
        val ctx = RuntimeEnvironment.getApplication()
        val prefs = TextReaderPrefs(ctx)
        assertEquals(TextReaderPrefs.DEFAULT_FONT_SP, prefs.getFontSp(), 0.01f)
        assertEquals(TextReaderTheme.DARK, prefs.getTheme())
        assertTrue(prefs.getWrap())
        assertTrue(prefs.getMonospace())
        assertFalse(prefs.getLineNumbers())

        prefs.setFontSp(18f)
        prefs.setTheme(TextReaderTheme.SEPIA)
        prefs.setWrap(false)
        prefs.setMonospace(false)
        prefs.setLineNumbers(true)

        val again = TextReaderPrefs(ctx)
        assertEquals(18f, again.getFontSp(), 0.01f)
        assertEquals(TextReaderTheme.SEPIA, again.getTheme())
        assertFalse(again.getWrap())
        assertFalse(again.getMonospace())
        assertTrue(again.getLineNumbers())
    }

    @Test
    fun clampsFontSize() {
        val prefs = TextReaderPrefs(RuntimeEnvironment.getApplication())
        prefs.setFontSp(3f)
        assertEquals(TextReaderPrefs.MIN_FONT_SP, prefs.getFontSp(), 0.01f)
        prefs.setFontSp(99f)
        assertEquals(TextReaderPrefs.MAX_FONT_SP, prefs.getFontSp(), 0.01f)
    }
}
