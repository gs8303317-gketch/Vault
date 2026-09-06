package app.vault.workspace.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LockRulesTest {
    @Test
    fun acceptsStrongPinFourToSix() {
        assertNull(LockRules.validateNewPin("7391"))
        assertNull(LockRules.validateNewPin("73915"))
        assertNull(LockRules.validateNewPin("739152"))
        assertTrue(LockRules.isValidPinFormat("7391"))
        assertTrue(LockRules.isValidPinFormat("739152"))
    }

    @Test
    fun rejectsWeakPins() {
        listOf("0000", "1111", "1234", "4321", "1212", "12345", "654321", "121212").forEach {
            assertTrue("expected weak: $it", LockRules.isWeakPin(it))
            assertNotNull(LockRules.validateNewPin(it))
        }
    }

    @Test
    fun rejectsWrongPinLength() {
        assertFalse(LockRules.isValidPinFormat("123"))
        assertFalse(LockRules.isValidPinFormat("1234567"))
        assertNotNull(LockRules.validateNewPin("123"))
        assertNotNull(LockRules.validateNewPin("abcdef"))
    }

    @Test
    fun passwordRules() {
        assertNull(LockRules.validateNewPassword("s3cret!"))
        assertNotNull(LockRules.validateNewPassword("short"))
        assertNotNull(LockRules.validateNewPassword("waytoolong1"))
        assertNotNull(LockRules.validateNewPassword("aaaaaa"))
        assertTrue(LockRules.isValidPasswordFormat("abcdef"))
    }

    @Test
    fun patternRules() {
        val ok = LockRules.encodePattern(listOf(0, 1, 2, 5))
        assertNull(LockRules.validateNewPattern(ok))
        assertTrue(LockRules.isValidPatternFormat(ok))
        assertNotNull(LockRules.validateNewPattern(LockRules.encodePattern(listOf(0, 1, 2))))
        assertNull(LockRules.decodePattern("0120")) // repeat invalid
        assertNotNull(LockRules.validateNewPattern("01ab"))
    }

    @Test
    fun validateNewDispatches() {
        assertNull(LockRules.validateNew(LockType.PIN, "7391"))
        assertNull(LockRules.validateNew(LockType.PASSWORD, "hunter2x"))
        assertNull(LockRules.validateNew(LockType.PATTERN, "01258"))
    }
}
