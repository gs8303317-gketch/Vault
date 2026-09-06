package app.vault.workspace.auth

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Biometric-gated AES-GCM wrap of the VMK.
 * Blob stored at filesDir/vault/header/vault.bio (nonce||ct||tag).
 * PIN remains the always-available fallback.
 */
object BiometricVault {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "vault_bio_vmk_v1"
    private const val GCM_IV = 12
    private const val GCM_TAG_BITS = 128
    private const val AAD = "VAULT-BIO-VMK-v1"

    fun bioFile(context: Context): File =
        File(File(context.filesDir, "vault/header").also { it.mkdirs() }, "vault.bio")

    fun isBiometricAvailable(context: Context): Boolean {
        val bm = BiometricManager.from(context)
        return bm.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun isEnabled(context: Context): Boolean = bioFile(context).exists() && bioFile(context).length() > GCM_IV

    fun createCipherForEncrypt(): Cipher {
        ensureKey()
        val key = loadKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher
    }

    fun createCipherForDecrypt(context: Context): Cipher {
        val blob = bioFile(context).readBytes()
        require(blob.size > GCM_IV) { "vault.bio missing or empty" }
        val iv = blob.copyOfRange(0, GCM_IV)
        ensureKey()
        val key = loadKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher
    }

    /** After BiometricPrompt success with ENCRYPT cipher — wrap VMK and persist. */
    fun wrapAndStore(context: Context, cipher: Cipher, vmk: ByteArray) {
        cipher.updateAAD(AAD.toByteArray(Charsets.UTF_8))
        val ct = cipher.doFinal(vmk)
        val iv = cipher.iv
        require(iv.size == GCM_IV)
        val blob = iv + ct
        writeAtomic(bioFile(context), blob)
    }

    /** After BiometricPrompt success with DECRYPT cipher — unwrap VMK. */
    fun unwrap(context: Context, cipher: Cipher): ByteArray {
        val blob = bioFile(context).readBytes()
        val ct = blob.copyOfRange(GCM_IV, blob.size)
        cipher.updateAAD(AAD.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(ct)
    }

    fun disable(context: Context) {
        bioFile(context).delete()
        File(bioFile(context).parentFile, "vault.bio.part").delete()
        try {
            val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS)
        } catch (_: Exception) {
        }
    }

    private fun ensureKey() {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        if (ks.containsAlias(ALIAS)) return
        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        keyGen.init(builder.build())
        keyGen.generateKey()
    }

    private fun loadKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        return ks.getKey(ALIAS, null) as SecretKey
    }

    private fun writeAtomic(dest: File, bytes: ByteArray) {
        dest.parentFile?.mkdirs()
        val part = File(dest.parentFile, dest.name + ".part")
        FileOutputStream(part).use { out ->
            out.write(bytes)
            out.flush()
            out.fd.sync()
        }
        if (dest.exists() && !dest.delete()) {
            throw IllegalStateException("Could not replace ${dest.name}")
        }
        if (!part.renameTo(dest)) {
            part.copyTo(dest, overwrite = true)
            part.delete()
        }
    }
}
