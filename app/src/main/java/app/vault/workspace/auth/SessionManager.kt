package app.vault.workspace.auth

import android.content.Context
import app.vault.workspace.crypto.KeyHierarchy
import app.vault.workspace.media.PlaybackPlaintextCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Holds VMK in RAM while unlocked. Lock wipes VMK and notifies listeners
 * (stop players, delete PDF tmp).
 *
 * Credential (PIN / password / pattern secret) → PBKDF2 → KEK wraps VMK in vault.hdr.
 * Changing lock type re-wraps the same VMK under a new salt/KEK; vault blobs untouched.
 */
class SessionManager(private val context: Context) {
    private val lockout = LockoutStore(context)
    private val lockPrefs = LockPrefs(context)
    private val _state = MutableStateFlow<SessionState>(SessionState.Locked)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private var vmk: ByteArray? = null
    private val lockListeners = CopyOnWriteArrayList<() -> Unit>()

    init {
        // Empty/corrupt vault.hdr from a bad write must not trap the user on Unlock.
        discardCorruptHeader()
    }

    val isSetupComplete: Boolean
        get() = readValidHeader() != null

    fun lockoutStore(): LockoutStore = lockout

    fun lockPrefs(): LockPrefs = lockPrefs

    fun lockType(): LockType = lockPrefs.lockType

    fun pinLength(): Int = lockPrefs.pinLength

    fun addLockListener(listener: () -> Unit) {
        lockListeners.add(listener)
    }

    fun removeLockListener(listener: () -> Unit) {
        lockListeners.remove(listener)
    }

    fun setup(credential: String, type: LockType = LockType.PIN): Result<Unit> {
        LockRules.validateNew(type, credential)?.let {
            return Result.failure(IllegalArgumentException(it))
        }
        // Recover interrupted setup: header may already exist from a prior confirm
        // that froze on Main-thread PBKDF2 before navigation / before LockPrefs.
        if (isSetupComplete) {
            val recovered = unlock(credential)
            if (recovered.isSuccess) {
                // Ensure prefs match the credential the user just confirmed.
                lockPrefs.setLock(
                    type,
                    pinLength = if (type == LockType.PIN) credential.length else null,
                )
                return Result.success(Unit)
            }
            val hdr = readValidHeader()
            if (hdr != null) {
                // Prefs may still be default (setLock never ran). Try raw unwrap.
                return try {
                    val chars = credential.toCharArray()
                    val kek = KeyHierarchy.deriveKek(chars, hdr.salt, hdr.iterations)
                    try {
                        chars.fill('\u0000')
                        val unlocked = KeyHierarchy.unwrapVmk(kek, hdr.wrappedVmk)
                        lockPrefs.setLock(
                            type,
                            pinLength = if (type == LockType.PIN) credential.length else null,
                        )
                        KeyHierarchy.wipe(vmk)
                        vmk = unlocked
                        lockout.recordSuccess()
                        ensureDirs()
                        _state.value = SessionState.Unlocked
                        Result.success(Unit)
                    } finally {
                        KeyHierarchy.wipe(kek)
                    }
                } catch (_: Exception) {
                    Result.failure(IllegalStateException("Already set up"))
                }
            }
            // Corrupt header was discarded — fall through to fresh setup.
        } else {
            discardCorruptHeader()
        }
        return try {
            val salt = KeyHierarchy.generateSalt()
            val newVmk = KeyHierarchy.generateVmk()
            val chars = credential.toCharArray()
            val kek = KeyHierarchy.deriveKek(chars, salt)
            try {
                chars.fill('\u0000')
                val wrapped = KeyHierarchy.wrapVmk(kek, newVmk)
                val encoded = KeyHierarchy.encodeVaultHeader(salt, KeyHierarchy.PBKDF2_ITERS, wrapped)
                writeHeaderAtomic(encoded)
                lockPrefs.setLock(
                    type,
                    pinLength = if (type == LockType.PIN) credential.length else null,
                )
                vmk = newVmk.copyOf()
                KeyHierarchy.wipe(newVmk)
                lockout.recordSuccess()
                ensureDirs()
                _state.value = SessionState.Unlocked
                Result.success(Unit)
            } finally {
                KeyHierarchy.wipe(kek)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun unlock(credential: String): Result<Unit> {
        if (!isSetupComplete) {
            return Result.failure(CorruptHeaderException())
        }
        val type = lockPrefs.lockType
        val expectedPinLen = if (type == LockType.PIN) lockPrefs.pinLength else null
        if (!LockRules.isValidUnlockFormat(type, credential, expectedPinLen)) {
            // Legacy vaults: PIN length pref may be wrong if user never migrated —
            // still allow any valid PIN format when stored type is PIN.
            val looseOk = type == LockType.PIN && LockRules.isValidPinFormat(credential)
            if (!looseOk) {
                return Result.failure(IllegalArgumentException("Invalid credential"))
            }
        }
        if (lockout.isLocked()) {
            return Result.failure(LockedOutException(lockout.remainingLockMs()))
        }
        return try {
            val hdr = readValidHeader()
                ?: return Result.failure(CorruptHeaderException())
            val chars = credential.toCharArray()
            val kek = KeyHierarchy.deriveKek(chars, hdr.salt, hdr.iterations)
            try {
                chars.fill('\u0000')
                val unlocked = KeyHierarchy.unwrapVmk(kek, hdr.wrappedVmk)
                KeyHierarchy.wipe(vmk)
                vmk = unlocked
                // Sync PIN length if unlock succeeded with a different length than prefs
                if (type == LockType.PIN && credential.length != lockPrefs.pinLength) {
                    lockPrefs.pinLength = credential.length
                }
                lockout.recordSuccess()
                ensureDirs()
                _state.value = SessionState.Unlocked
                Result.success(Unit)
            } catch (e: Exception) {
                lockout.recordFailure()
                val remaining = lockout.remainingLockMs()
                if (lockout.isLocked()) {
                    Result.failure(LockedOutException(remaining))
                } else {
                    Result.failure(WrongPinException(lockout.failedAttempts))
                }
            } finally {
                KeyHierarchy.wipe(kek)
            }
        } catch (e: LockedOutException) {
            Result.failure(e)
        } catch (e: CorruptHeaderException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Complete unlock after biometric unwrap of VMK.
     * Does not touch lockout counters (biometric is a second factor over an already-enrolled wrap).
     */
    fun unlockWithVmk(unlockedVmk: ByteArray): Result<Unit> {
        if (!isSetupComplete) {
            return Result.failure(CorruptHeaderException())
        }
        if (lockout.isLocked()) {
            return Result.failure(LockedOutException(lockout.remainingLockMs()))
        }
        return try {
            require(unlockedVmk.size == KeyHierarchy.KEY_SIZE_BYTES)
            KeyHierarchy.wipe(vmk)
            vmk = unlockedVmk.copyOf()
            KeyHierarchy.wipe(unlockedVmk)
            lockout.recordSuccess()
            ensureDirs()
            _state.value = SessionState.Unlocked
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun bioFile(): File = File(File(vaultRoot(), "header").also { it.mkdirs() }, "vault.bio")

    fun requireVmk(): ByteArray {
        val key = vmk ?: throw IllegalStateException("Vault is locked")
        return key
    }

    fun peekVmk(): ByteArray? = vmk?.copyOf()

    /**
     * Verify [currentCredential], then re-wrap VMK under a new KEK from [newCredential]
     * and persist [newType]. Clears biometric wrap (must re-enable). Session stays unlocked.
     */
    fun changeLock(
        currentCredential: String,
        newType: LockType,
        newCredential: String,
    ): Result<Unit> {
        if (_state.value !is SessionState.Unlocked || vmk == null) {
            return Result.failure(IllegalStateException("Vault is locked"))
        }
        val currentType = lockPrefs.lockType
        val expectedPinLen = if (currentType == LockType.PIN) lockPrefs.pinLength else null
        val currentFormatOk =
            LockRules.isValidUnlockFormat(currentType, currentCredential, expectedPinLen) ||
                (currentType == LockType.PIN && LockRules.isValidPinFormat(currentCredential))
        if (!currentFormatOk) {
            return Result.failure(IllegalArgumentException("Current credential invalid"))
        }
        LockRules.validateNew(newType, newCredential)?.let {
            return Result.failure(IllegalArgumentException(it))
        }
        if (currentType == newType && currentCredential == newCredential) {
            return Result.failure(IllegalArgumentException("New credential must be different"))
        }
        return try {
            val hdr = readValidHeader()
                ?: return Result.failure(CorruptHeaderException())
            val oldChars = currentCredential.toCharArray()
            val oldKek = KeyHierarchy.deriveKek(oldChars, hdr.salt, hdr.iterations)
            try {
                oldChars.fill('\u0000')
                // Verify current credential by unwrapping; must match session VMK
                val check = KeyHierarchy.unwrapVmk(oldKek, hdr.wrappedVmk)
                val match = check.contentEquals(vmk)
                KeyHierarchy.wipe(check)
                if (!match) {
                    return Result.failure(WrongPinException(0))
                }
            } catch (e: Exception) {
                return Result.failure(WrongPinException(0))
            } finally {
                KeyHierarchy.wipe(oldKek)
            }

            val newSalt = KeyHierarchy.generateSalt()
            val newChars = newCredential.toCharArray()
            val newKek = KeyHierarchy.deriveKek(newChars, newSalt)
            try {
                newChars.fill('\u0000')
                val sessionVmk = vmk ?: return Result.failure(IllegalStateException("Vault is locked"))
                val wrapped = KeyHierarchy.wrapVmk(newKek, sessionVmk)
                val encoded = KeyHierarchy.encodeVaultHeader(
                    newSalt,
                    KeyHierarchy.PBKDF2_ITERS,
                    wrapped,
                )
                writeHeaderAtomic(encoded)
                lockPrefs.setLock(
                    newType,
                    pinLength = if (newType == LockType.PIN) newCredential.length else null,
                )
            } finally {
                KeyHierarchy.wipe(newKek)
            }

            // Safest: clear bio wrap so old biometric blob cannot unlock with stale assumption
            try {
                BiometricVault.disable(context)
            } catch (_: Exception) {
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Re-wrap VMK under a new PIN-derived KEK. Clears biometric wrap (must re-enable).
     * Session stays unlocked; intermediates wiped.
     */
    fun changePin(currentPin: String, newPin: String): Result<Unit> =
        changeLock(currentPin, LockType.PIN, newPin)

    fun lock() {
        KeyHierarchy.wipe(vmk)
        vmk = null
        _state.value = SessionState.Locked
        for (l in lockListeners) {
            try {
                l()
            } catch (_: Exception) {
            }
        }
        wipeTmp()
    }

    fun wipeTmp() {
        // playcache / legacy seekprep leftovers (never leave plaintext)
        try {
            PlaybackPlaintextCache.wipeAll(context)
        } catch (_: Exception) {
        }
        val dir = tmpDir()
        if (!dir.exists()) return
        // Belt-and-suspenders: purge any leftover plaintext PDF caches first
        dir.listFiles()?.forEach { f ->
            if (f.isFile && f.name.endsWith(".pdf", ignoreCase = true)) {
                try {
                    f.delete()
                } catch (_: Exception) {
                }
            }
        }
        // Also purge any playcache_* leftovers under vault/tmp
        dir.listFiles()?.forEach { f ->
            if (f.isFile && f.name.startsWith("playcache_", ignoreCase = true)) {
                try {
                    f.delete()
                } catch (_: Exception) {
                }
            }
        }
        dir.listFiles()?.forEach { f ->
            try {
                if (f.isDirectory) f.deleteRecursively() else f.delete()
            } catch (_: Exception) {
            }
        }
    }

    fun vaultRoot(): File = File(context.filesDir, "vault").also { it.mkdirs() }
    fun blobsDir(): File = File(vaultRoot(), "blobs").also { it.mkdirs() }
    fun thumbsDir(): File = File(vaultRoot(), "thumbs").also { it.mkdirs() }
    fun tmpDir(): File = File(vaultRoot(), "tmp").also { it.mkdirs() }
    fun headerFile(): File = File(File(vaultRoot(), "header").also { it.mkdirs() }, "vault.hdr")

    private fun ensureDirs() {
        vaultRoot(); blobsDir(); thumbsDir(); tmpDir()
        headerFile().parentFile?.mkdirs()
    }

    /**
     * Write vault.hdr via .part + sync + rename.
     * Never open a second truncating OutputStream after writeBytes — that emptied the file
     * and produced "vault.hdr too short" on unlock.
     */
    private fun writeHeaderAtomic(encoded: ByteArray) {
        val hdr = headerFile()
        hdr.parentFile?.mkdirs()
        val part = File(hdr.parentFile, "vault.hdr.part")
        FileOutputStream(part).use { out ->
            out.write(encoded)
            out.flush()
            out.fd.sync()
        }
        if (hdr.exists() && !hdr.delete()) {
            throw IllegalStateException("Could not replace vault.hdr")
        }
        if (!part.renameTo(hdr)) {
            part.copyTo(hdr, overwrite = true)
            part.delete()
        }
        // Sanity: never leave an empty header claiming setup is done
        if (hdr.length() < 28L) {
            hdr.delete()
            throw IllegalStateException("vault.hdr write failed")
        }
    }

    private fun readValidHeader(): KeyHierarchy.VaultHeaderFile? {
        val f = headerFile()
        if (!f.exists() || f.length() == 0L) return null
        return try {
            KeyHierarchy.decodeVaultHeader(f.readBytes())
        } catch (_: Exception) {
            null
        }
    }

    private fun discardCorruptHeader() {
        val f = headerFile()
        if (!f.exists() && !File(f.parentFile, "vault.hdr.part").exists()) return
        if (readValidHeader() == null) {
            f.delete()
            File(f.parentFile, "vault.hdr.part").delete()
            // Keep prefs in sync so a corrupt header cannot leave stale lock type.
            try {
                lockPrefs.clear()
            } catch (_: Exception) {
            }
        }
    }

    sealed class SessionState {
        data object Locked : SessionState()
        data object Unlocked : SessionState()
    }

    class WrongPinException(val attempts: Int) : Exception("Wrong credential")
    class LockedOutException(val remainingMs: Long) : Exception("Locked out")
    class CorruptHeaderException : Exception("Vault header missing or corrupt")
}
