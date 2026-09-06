package app.vault.workspace.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackPositionStoreTest {
    @Test
    fun shouldPersistRejectsNearStartOrEnd() {
        assertFalse(PlaybackPositionStore.shouldPersist(1_000L, 60_000L))
        assertFalse(PlaybackPositionStore.shouldPersist(58_000L, 60_000L))
        assertTrue(PlaybackPositionStore.shouldPersist(20_000L, 60_000L))
    }

    @Test
    fun resumePositionClearsFinished() {
        assertEquals(0L, PlaybackPositionStore.resumePosition(59_000L, 60_000L))
        assertEquals(20_000L, PlaybackPositionStore.resumePosition(20_000L, 60_000L))
        assertEquals(0L, PlaybackPositionStore.resumePosition(2_000L, 60_000L))
    }
}
