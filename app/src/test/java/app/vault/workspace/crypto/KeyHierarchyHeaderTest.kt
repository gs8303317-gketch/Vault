package app.vault.workspace.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class KeyHierarchyHeaderTest {
    @Test
    fun encodeDecodeRoundTrip() {
        val salt = KeyHierarchy.generateSalt()
        val vmk = KeyHierarchy.generateVmk()
        val kek = KeyHierarchy.deriveKek("7391".toCharArray(), salt)
        try {
            val wrapped = KeyHierarchy.wrapVmk(kek, vmk)
            val raw = KeyHierarchy.encodeVaultHeader(salt, KeyHierarchy.PBKDF2_ITERS, wrapped)
            val decoded = KeyHierarchy.decodeVaultHeader(raw)
            assertEquals(1, decoded.version)
            assertEquals(KeyHierarchy.PBKDF2_ITERS, decoded.iterations)
            assertArrayEquals(salt, decoded.salt)
            assertArrayEquals(wrapped, decoded.wrappedVmk)
            val unlocked = KeyHierarchy.unwrapVmk(kek, decoded.wrappedVmk)
            assertArrayEquals(vmk, unlocked)
            KeyHierarchy.wipe(unlocked)
        } finally {
            KeyHierarchy.wipe(kek)
            KeyHierarchy.wipe(vmk)
        }
    }

    @Test
    fun emptyHeaderFails() {
        assertThrows(IllegalArgumentException::class.java) {
            KeyHierarchy.decodeVaultHeader(ByteArray(0))
        }
    }

    @Test
    fun shortHeaderFails() {
        assertThrows(IllegalArgumentException::class.java) {
            KeyHierarchy.decodeVaultHeader(ByteArray(10))
        }
    }
}
