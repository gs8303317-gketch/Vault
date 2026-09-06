package app.vault.workspace.ui.viewer

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSpeedFitTest {
    @Test
    fun formatsPlaybackSpeed() {
        assertEquals("1×", formatPlaybackSpeed(1f))
        assertEquals("2×", formatPlaybackSpeed(2f))
        assertEquals("0.5×", formatPlaybackSpeed(0.5f))
        assertEquals("1.25×", formatPlaybackSpeed(1.25f))
        assertEquals("1.5×", formatPlaybackSpeed(1.5f))
    }

    @Test
    fun cyclesFitModes() {
        assertEquals(VideoFitMode.FILL, nextVideoFitMode(VideoFitMode.FIT))
        assertEquals(VideoFitMode.STRETCH, nextVideoFitMode(VideoFitMode.FILL))
        assertEquals(VideoFitMode.ZOOM, nextVideoFitMode(VideoFitMode.STRETCH))
        assertEquals(VideoFitMode.FIT, nextVideoFitMode(VideoFitMode.ZOOM))
    }
}
