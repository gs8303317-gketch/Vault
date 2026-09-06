package app.vault.workspace.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class LockoutStoreTest {
    @Test
    fun delaySchedule() {
        assertEquals(0L, LockoutStore.delayForAttempt(1))
        assertEquals(0L, LockoutStore.delayForAttempt(2))
        assertEquals(30_000L, LockoutStore.delayForAttempt(3))
        assertEquals(60_000L, LockoutStore.delayForAttempt(4))
        assertEquals(5 * 60_000L, LockoutStore.delayForAttempt(5))
        assertEquals(15 * 60_000L, LockoutStore.delayForAttempt(6))
        assertEquals(60 * 60_000L, LockoutStore.delayForAttempt(7))
        assertEquals(60 * 60_000L, LockoutStore.delayForAttempt(20))
    }
}
