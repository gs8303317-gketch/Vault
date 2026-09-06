package app.vault.workspace.ui.viewer

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatPlayerTimeTest {
    @Test
    fun formatsUnderOneHour() {
        assertEquals("0:00", formatPlayerTime(0))
        assertEquals("0:05", formatPlayerTime(5_000))
        assertEquals("1:01", formatPlayerTime(61_000))
        assertEquals("38:12", formatPlayerTime(38 * 60_000L + 12_000L))
    }

    @Test
    fun formatsWithHours() {
        assertEquals("1:00:00", formatPlayerTime(3_600_000))
        assertEquals("1:02:03", formatPlayerTime(3_600_000 + 2 * 60_000L + 3_000L))
    }
}
