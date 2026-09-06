package app.vault.workspace.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class VaultCryptoTest {

    private fun tmpDir(): File =
        File(System.getProperty("java.io.tmpdir"), "vault-crypto-test-${System.nanoTime()}")
            .also { it.mkdirs() }

    private fun dek(): ByteArray = KeyHierarchy.generateDek()

    @Test
    fun emptyFile_headerOnly_roundTrip() {
        val dir = tmpDir()
        val vat = File(dir, "empty.vat")
        val key = dek()
        val header = VaultCrypto.encryptBytes(ByteArray(0), key, vat, dir)
        assertEquals(0L, header.plaintextSize)
        assertEquals(VaultFormat.HEADER_SIZE.toLong(), vat.length())
        val out = VaultCrypto.decryptToBytes(vat, key)
        assertEquals(0, out.size)
        dir.deleteRecursively()
    }

    @Test
    fun oneByte_roundTrip() {
        val dir = tmpDir()
        val vat = File(dir, "one.vat")
        val key = dek()
        val plain = byteArrayOf(0x42)
        VaultCrypto.encryptBytes(plain, key, vat, dir)
        assertArrayEquals(plain, VaultCrypto.decryptToBytes(vat, key))
        dir.deleteRecursively()
    }

    @Test
    fun exactlyOneChunk_256KiB_roundTrip() {
        val dir = tmpDir()
        val vat = File(dir, "chunk.vat")
        val key = dek()
        val plain = ByteArray(VaultFormat.CHUNK_PLAINTEXT_SIZE) { (it % 251).toByte() }
        VaultCrypto.encryptBytes(plain, key, vat, dir)
        assertArrayEquals(plain, VaultCrypto.decryptToBytes(vat, key))
        dir.deleteRecursively()
    }

    @Test
    fun onePastChunk_256KiBPlus1_roundTrip() {
        val dir = tmpDir()
        val vat = File(dir, "plus1.vat")
        val key = dek()
        val plain = ByteArray(VaultFormat.CHUNK_PLAINTEXT_SIZE + 1) { (it % 251).toByte() }
        VaultCrypto.encryptBytes(plain, key, vat, dir)
        assertArrayEquals(plain, VaultCrypto.decryptToBytes(vat, key))
        // two chunks: one full + one byte
        assertEquals(2, VaultFormat.chunkCount(plain.size.toLong()))
        dir.deleteRecursively()
    }

    @Test
    fun tamperCiphertext_failsAuth() {
        val dir = tmpDir()
        val vat = File(dir, "tamper.vat")
        val key = dek()
        val plain = ByteArray(64) { 7 }
        VaultCrypto.encryptBytes(plain, key, vat, dir)
        val bytes = vat.readBytes()
        // Flip a byte in the first ciphertext chunk (after header)
        require(bytes.size > VaultFormat.HEADER_SIZE + 2)
        bytes[VaultFormat.HEADER_SIZE + 2] =
            (bytes[VaultFormat.HEADER_SIZE + 2].toInt() xor 0x01).toByte()
        vat.writeBytes(bytes)
        try {
            VaultCrypto.decryptToBytes(vat, key)
            fail("Expected AuthFailedException")
        } catch (e: VaultCrypto.AuthFailedException) {
            assertTrue(e.message!!.contains("authentication"))
        }
        dir.deleteRecursively()
    }

    @Test
    fun headerMagicAndLayout() {
        val nonce = ByteArray(8) { 1 }
        val fileId = ByteArray(16) { 2 }
        val h = VaultFormat.Header(
            version = 1,
            flags = 0,
            chunkSize = VaultFormat.CHUNK_PLAINTEXT_SIZE,
            plaintextSize = 100L,
            noncePrefix = nonce,
            fileId = fileId,
        )
        val baos = java.io.ByteArrayOutputStream()
        VaultFormat.writeHeader(baos, h)
        val raw = baos.toByteArray()
        assertEquals(64, raw.size)
        assertEquals("VAULT1", String(raw, 0, 6, Charsets.US_ASCII))
        val parsed = VaultFormat.parseHeader(raw)
        assertEquals(h, parsed)
    }

    @Test
    fun keyHierarchy_wrapUnwrap() {
        val salt = KeyHierarchy.generateSalt()
        val pin = "7391".toCharArray()
        val kek = KeyHierarchy.deriveKek(pin, salt)
        val vmk = KeyHierarchy.generateVmk()
        val wrapped = KeyHierarchy.wrapVmk(kek, vmk)
        val unwrapped = KeyHierarchy.unwrapVmk(kek, wrapped)
        assertArrayEquals(vmk, unwrapped)
        val dek = KeyHierarchy.generateDek()
        val wDek = KeyHierarchy.wrapDek(vmk, dek)
        assertArrayEquals(dek, KeyHierarchy.unwrapDek(vmk, wDek))
    }
}
