package app.vault.workspace.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekReadyStampTest {

    @Test
    fun matches_equalNonNegative() {
        assertTrue(SeekReadyStamp.matches(0L, 0L))
        assertTrue(SeekReadyStamp.matches(1024L, 1024L))
    }

    @Test
    fun matches_mismatchOrNegative() {
        assertFalse(SeekReadyStamp.matches(100L, 99L))
        assertFalse(SeekReadyStamp.matches(-1L, -1L))
        assertFalse(SeekReadyStamp.matches(-1L, 10L))
    }
}
