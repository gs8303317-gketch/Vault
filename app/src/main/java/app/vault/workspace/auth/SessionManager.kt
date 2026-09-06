package app.vault.workspace.auth

import android.content.Context
import app.vault.workspace.crypto.KeyHierarchy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Holds VMK in RAM while unlocked. Lock wipes VMK and notifies listeners
 * (stop players, delete PDF tmp).
 */
class SessionManager(private val context: Context) {
    private val lockout = LockoutStore(context)
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

    fun addLockListener(listener: () -> Unit) {
        lockListeners.add(listener)
    }

    fun removeLockListener(listener: () -> Unit) {
        lockListeners.remove(listener)
    }

    fun setup(pin: String): Result<Unit> {
        PinRules.validateNewPin(pin)?.let { return Result.failure(IllegalArgumentException(it)) }
        if (isSetupComplete) return Result.failure(IllegalStateException("Already set up"))
        return try {
            val salt = KeyHierarchy.generateSalt()
            val newVmk = KeyHierarchy.generateVmk()
            val kek = KeyHierarchy.deriveKek(pin.toCharArray(), salt)
            try {
                val wrapped = KeyHierarchy.wrapVmk(kek, newVmk)
                val encoded = KeyHierarchy.encodeVaultHeader(salt, KeyHierarchy.PBKDF2_ITERS, wrapped)
                writeHeaderAtomic(encoded)
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

    fun unlock(pin: String): Result<Unit> {
        if (!isSetupComplete) {
            return Result.failure(CorruptHeaderException())
        }
        if (!PinRules.isExactFourDigits(pin)) {
            return Result.failure(IllegalArgumentException("Invalid PIN"))
        }
        if (lockout.isLocked()) {
            return Result.failure(LockedOutException(lockout.remainingLockMs()))
        }
        return try {
            val hdr = readValidHeader()
                ?: return Result.failure(CorruptHeaderException())
            val kek = KeyHierarchy.deriveKek(pin.toCharArray(), hdr.salt, hdr.iterations)
            try {
                val unlocked = KeyHierarchy.unwrapVmk(kek, hdr.wrappedVmk)
                KeyHierarchy.wipe(vmk)
                vmk = unlocked
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

    fun requireVmk(): ByteArray {
        val key = vmk ?: throw IllegalStateException("Vault is locked")
        return key
    }

    fun peekVmk(): ByteArray? = vmk?.copyOf()

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
        val dir = tmpDir()
        if (!dir.exists()) return
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
        if (!f.exists()) return
        if (readValidHeader() == null) {
            f.delete()
            File(f.parentFile, "vault.hdr.part").delete()
        }
    }

    sealed class SessionState {
        data object Locked : SessionState()
        data object Unlocked : SessionState()
    }

    class WrongPinException(val attempts: Int) : Exception("Wrong PIN")
    class LockedOutException(val remainingMs: Long) : Exception("Locked out")
    class CorruptHeaderException : Exception("Vault header missing or corrupt")
}
