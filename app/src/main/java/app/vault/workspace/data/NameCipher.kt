package app.vault.workspace.data

import app.vault.workspace.crypto.KeyHierarchy
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Encrypt / decrypt display names under VMK (AES-GCM). */
object NameCipher {
    private const val CTX = "VAULT-NAME-v1"
    private val random = SecureRandom()

    fun encrypt(vmk: ByteArray, name: String): ByteArray {
        val iv = ByteArray(KeyHierarchy.GCM_IV_SIZE).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(vmk, "AES"),
            GCMParameterSpec(KeyHierarchy.GCM_TAG_BITS, iv),
        )
        cipher.updateAAD(CTX.toByteArray(Charsets.UTF_8))
        return iv + cipher.doFinal(name.toByteArray(Charsets.UTF_8))
    }

    fun decrypt(vmk: ByteArray, blob: ByteArray): String {
        require(blob.size > KeyHierarchy.GCM_IV_SIZE + KeyHierarchy.GCM_TAG_SIZE)
        val iv = blob.copyOfRange(0, KeyHierarchy.GCM_IV_SIZE)
        val ct = blob.copyOfRange(KeyHierarchy.GCM_IV_SIZE, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(vmk, "AES"),
            GCMParameterSpec(KeyHierarchy.GCM_TAG_BITS, iv),
        )
        cipher.updateAAD(CTX.toByteArray(Charsets.UTF_8))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }
}
