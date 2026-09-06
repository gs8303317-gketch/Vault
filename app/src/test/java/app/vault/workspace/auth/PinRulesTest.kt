package app.vault.workspace.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinRulesTest {
    @Test
    fun acceptsStrongFourDigit() {
        assertNull(PinRules.validateNewPin("7391"))
        assertTrue(PinRules.isExactFourDigits("7391"))
    }

    @Test
    fun rejectsWeakList() {
        listOf("0000", "1111", "2222", "1234", "4321", "1212", "2580", "1122").forEach {
            assertTrue(PinRules.isWeak(it))
            assertNotNull(PinRules.validateNewPin(it))
        }
    }

    @Test
    fun rejectsWrongLength() {
        assertFalse(PinRules.isExactFourDigits("123"))
        assertFalse(PinRules.isExactFourDigits("12345"))
        assertNotNull(PinRules.validateNewPin("123"))
        assertNotNull(PinRules.validateNewPin("abcd"))
    }
}
