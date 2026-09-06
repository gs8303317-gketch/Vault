package app.vault.workspace.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure validation rules used by Change PIN (before SessionManager re-wrap).
 */
class ChangePinValidationTest {
    @Test
    fun newPinMustPassWeakRules() {
        assertNotNull(PinRules.validateNewPin("1234"))
        assertNull(PinRules.validateNewPin("7391"))
    }

    @Test
    fun newPinMustDifferFromCurrent() {
        val current = "7391"
        val same = "7391"
        val different = "8402"
        assertEquals(true, current == same)
        assertEquals(false, current == different)
        assertNull(PinRules.validateNewPin(different))
    }

    @Test
    fun currentMustBeExactFourDigits() {
        assertEquals(true, PinRules.isExactFourDigits("7391"))
        assertEquals(false, PinRules.isExactFourDigits("739"))
        assertEquals(false, PinRules.isExactFourDigits("abcd"))
    }
}
