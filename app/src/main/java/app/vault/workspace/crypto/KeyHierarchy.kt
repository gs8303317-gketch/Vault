package app.vault.workspace.crypto

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Key hierarchy:
 * PIN → PBKDF2-HMAC-SHA256 (210000, 16-byte salt) → KEK
 * KEK wraps random 256-bit VMK (AES-GCM, AAD context VAULT-VMK-v1)
 * VMK wraps per-file DEK (AES-GCM, AAD context VAULT-DEK-v1)
 */
object KeyHierarchy {
    const val PBKDF2_ITERS = 210_000
    const val SALT_SIZE = 16
    const val KEY_SIZE_BYTES = 32
    const val GCM_IV_SIZE = 12
    const val GCM_TAG_BITS = 128
    const val GCM_TAG_SIZE = 16

    const val CTX_VMK = "VAULT-VMK-v1"
    const val CTX_DEK = "VAULT-DEK-v1"

    private val random = SecureRandom()

    fun generateSalt(): ByteArray = ByteArray(SALT_SIZE).also { random.nextBytes(it) }

    fun generateVmk(): ByteArray = ByteArray(KEY_SIZE_BYTES).also { random.nextBytes(it) }

    fun generateDek(): ByteArray = ByteArray(KEY_SIZE_BYTES).also { random.nextBytes(it) }

    fun deriveKek(pin: CharArray, salt: ByteArray, iterations: Int = PBKDF2_ITERS): ByteArray {
        require(salt.size == SALT_SIZE)
        require(iterations > 0)
        val spec = PBEKeySpec(pin, salt, iterations, KEY_SIZE_BYTES * 8)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** Returns nonce(12) || ciphertext||tag for the wrapped key. */
    fun wrapKey(wrappingKey: ByteArray, keyToWrap: ByteArray, context: String): ByteArray {
        require(wrappingKey.size == KEY_SIZE_BYTES)
        require(keyToWrap.size == KEY_SIZE_BYTES)
        val iv = ByteArray(GCM_IV_SIZE).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(wrappingKey, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, iv),
        )
        cipher.updateAAD(context.toByteArray(Charsets.UTF_8))
        val ct = cipher.doFinal(keyToWrap)
        return iv + ct
    }

    fun unwrapKey(wrappingKey: ByteArray, wrapped: ByteArray, context: String): ByteArray {
        require(wrappingKey.size == KEY_SIZE_BYTES)
        require(wrapped.size == GCM_IV_SIZE + KEY_SIZE_BYTES + GCM_TAG_SIZE) {
            "Unexpected wrap blob length: ${wrapped.size}"
        }
        val iv = wrapped.copyOfRange(0, GCM_IV_SIZE)
        val ct = wrapped.copyOfRange(GCM_IV_SIZE, wrapped.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(wrappingKey, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, iv),
        )
        cipher.updateAAD(context.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(ct)
    }

    fun wrapVmk(kek: ByteArray, vmk: ByteArray): ByteArray = wrapKey(kek, vmk, CTX_VMK)

    fun unwrapVmk(kek: ByteArray, wrapped: ByteArray): ByteArray = unwrapKey(kek, wrapped, CTX_VMK)

    fun wrapDek(vmk: ByteArray, dek: ByteArray): ByteArray = wrapKey(vmk, dek, CTX_DEK)

    fun unwrapDek(vmk: ByteArray, wrapped: ByteArray): ByteArray = unwrapKey(vmk, wrapped, CTX_DEK)

    fun wipe(bytes: ByteArray?) {
        bytes?.fill(0)
    }

    // ---- vault.hdr binary format ----
    // Magic "VHDR1" (5) | version(1)=1 | iters BE int32 | salt(16) | wrapLen BE int16 | wrap blob
    private val HDR_MAGIC = "VHDR1".toByteArray(Charsets.US_ASCII)

    fun encodeVaultHeader(salt: ByteArray, iterations: Int, wrappedVmk: ByteArray): ByteArray {
        require(salt.size == SALT_SIZE)
        val len = 5 + 1 + 4 + SALT_SIZE + 2 + wrappedVmk.size
        val buf = ByteBuffer.allocate(len).order(ByteOrder.BIG_ENDIAN)
        buf.put(HDR_MAGIC)
        buf.put(1)
        buf.putInt(iterations)
        buf.put(salt)
        buf.putShort(wrappedVmk.size.toShort())
        buf.put(wrappedVmk)
        return buf.array()
    }

    data class VaultHeaderFile(
        val version: Int,
        val iterations: Int,
        val salt: ByteArray,
        val wrappedVmk: ByteArray,
    )

    fun decodeVaultHeader(raw: ByteArray): VaultHeaderFile {
        require(raw.size >= 5 + 1 + 4 + SALT_SIZE + 2) { "vault.hdr too short" }
        val buf = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN)
        val magic = ByteArray(5)
        buf.get(magic)
        require(magic.contentEquals(HDR_MAGIC)) { "Bad vault.hdr magic" }
        val version = buf.get().toInt() and 0xff
        require(version == 1) { "Unsupported vault.hdr version" }
        val iterations = buf.int
        val salt = ByteArray(SALT_SIZE)
        buf.get(salt)
        val wrapLen = buf.short.toInt() and 0xffff
        require(buf.remaining() >= wrapLen) { "Truncated wrap blob" }
        val wrapped = ByteArray(wrapLen)
        buf.get(wrapped)
        return VaultHeaderFile(version, iterations, salt, wrapped)
    }
}
