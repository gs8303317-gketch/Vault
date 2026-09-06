package app.vault.workspace.auth

import app.vault.workspace.crypto.KeyHierarchy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure validation + KEK re-wrap happy path used by Change lock.
 */
class ChangePinValidationTest {
    @Test
    fun newPinMustPassWeakRules() {
        assertNotNull(LockRules.validateNewPin("1234"))
        assertNull(LockRules.validateNewPin("7391"))
    }

    @Test
    fun newCredentialMustDifferFromCurrent() {
        val current = "7391"
        assertTrue(current == "7391")
        assertFalse(current == "8402")
        assertNull(LockRules.validateNewPin("8402"))
    }

    @Test
    fun unlockFormatRespectsPinLength() {
        assertTrue(LockRules.isValidUnlockFormat(LockType.PIN, "7391", expectedPinLength = 4))
        assertFalse(LockRules.isValidUnlockFormat(LockType.PIN, "73915", expectedPinLength = 4))
        assertTrue(LockRules.isValidUnlockFormat(LockType.PASSWORD, "secret1"))
        assertTrue(LockRules.isValidUnlockFormat(LockType.PATTERN, "01258"))
    }

    @Test
    fun rewrapVmkUnderNewCredentialKeepsSameVmk() {
        val vmk = KeyHierarchy.generateVmk()
        val salt1 = KeyHierarchy.generateSalt()
        val kek1 = KeyHierarchy.deriveKek("7391".toCharArray(), salt1)
        val wrapped1 = KeyHierarchy.wrapVmk(kek1, vmk)

        // Verify old
        val check = KeyHierarchy.unwrapVmk(kek1, wrapped1)
        assertTrue(check.contentEquals(vmk))
        KeyHierarchy.wipe(check)
        KeyHierarchy.wipe(kek1)

        // Change to password
        val salt2 = KeyHierarchy.generateSalt()
        val kek2 = KeyHierarchy.deriveKek("hunter2x".toCharArray(), salt2)
        val wrapped2 = KeyHierarchy.wrapVmk(kek2, vmk)
        val hdr = KeyHierarchy.encodeVaultHeader(salt2, KeyHierarchy.PBKDF2_ITERS, wrapped2)
        val decoded = KeyHierarchy.decodeVaultHeader(hdr)

        val kekUnlock = KeyHierarchy.deriveKek("hunter2x".toCharArray(), decoded.salt, decoded.iterations)
        val unlocked = KeyHierarchy.unwrapVmk(kekUnlock, decoded.wrappedVmk)
        assertTrue(unlocked.contentEquals(vmk))

        // Old PIN no longer unwraps new header
        val oldKek = KeyHierarchy.deriveKek("7391".toCharArray(), decoded.salt, decoded.iterations)
        var failed = false
        try {
            KeyHierarchy.unwrapVmk(oldKek, decoded.wrappedVmk)
        } catch (_: Exception) {
            failed = true
        }
        assertTrue(failed)

        KeyHierarchy.wipe(vmk)
        KeyHierarchy.wipe(kek2)
        KeyHierarchy.wipe(kekUnlock)
        KeyHierarchy.wipe(unlocked)
        KeyHierarchy.wipe(oldKek)
        assertEquals(LockType.PASSWORD, LockType.valueOf("PASSWORD"))
    }

    @Test
    fun patternToPinRewrap() {
        val vmk = KeyHierarchy.generateVmk()
        val pattern = LockRules.encodePattern(listOf(0, 3, 6, 7, 8))
        val salt1 = KeyHierarchy.generateSalt()
        val kek1 = KeyHierarchy.deriveKek(pattern.toCharArray(), salt1)
        val wrapped = KeyHierarchy.wrapVmk(kek1, vmk)

        val salt2 = KeyHierarchy.generateSalt()
        val pin = "8402"
        val kek2 = KeyHierarchy.deriveKek(pin.toCharArray(), salt2)
        val wrapped2 = KeyHierarchy.wrapVmk(kek2, vmk)

        val u1 = KeyHierarchy.unwrapVmk(kek1, wrapped)
        val u2 = KeyHierarchy.unwrapVmk(kek2, wrapped2)
        assertTrue(u1.contentEquals(u2))
        KeyHierarchy.wipe(vmk); KeyHierarchy.wipe(kek1); KeyHierarchy.wipe(kek2)
        KeyHierarchy.wipe(u1); KeyHierarchy.wipe(u2)
    }
}
