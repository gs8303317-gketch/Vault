package app.vault.workspace.ui.viewer

import org.junit.Assert.assertEquals
import org.junit.Test

class SlideshowIntervalTest {
    @Test
    fun defaultIsThreeSeconds() {
        assertEquals(3_000L, SlideshowInterval.Default.ms)
        assertEquals("3s", SlideshowInterval.Default.label)
    }

    @Test
    fun cyclesThroughAllOptions() {
        var cur = SlideshowInterval.S2
        val seen = mutableListOf<String>()
        repeat(4) {
            seen += cur.label
            cur = cur.next()
        }
        assertEquals(listOf("2s", "3s", "5s", "10s"), seen)
        assertEquals(SlideshowInterval.S2, cur)
    }

    @Test
    fun fromMsFallsBackToDefault() {
        assertEquals(SlideshowInterval.S5, SlideshowInterval.fromMs(5_000L))
        assertEquals(SlideshowInterval.Default, SlideshowInterval.fromMs(999L))
    }
}
