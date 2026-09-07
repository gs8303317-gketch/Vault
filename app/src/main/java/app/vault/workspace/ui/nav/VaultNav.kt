package app.vault.workspace.ui.nav

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.vault.workspace.media.DecryptingPlayback
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.vault.workspace.auth.AutoLockController
import app.vault.workspace.auth.BiometricVault
import app.vault.workspace.auth.LockType
import app.vault.workspace.auth.SessionManager
import app.vault.workspace.data.VaultFolder
import app.vault.workspace.data.VaultCategory
import app.vault.workspace.data.VaultItem
import app.vault.workspace.data.VaultRepository
import app.vault.workspace.export.ExportController
import app.vault.workspace.import.ImportController
import app.vault.workspace.ui.folders.FoldersScreen
import app.vault.workspace.ui.theme.VaultAccent
import app.vault.workspace.ui.theme.VaultAmoled
import app.vault.workspace.ui.theme.VaultDanger
import app.vault.workspace.ui.theme.VaultSurface
import app.vault.workspace.ui.theme.VaultTextMuted
import app.vault.workspace.ui.folders.MoveToFolderDialog
import app.vault.workspace.ui.library.LibraryScreen
import app.vault.workspace.ui.library.ThumbCache
import app.vault.workspace.ui.settings.SettingsScreen
import app.vault.workspace.ui.setup.FirstRunScreen
import app.vault.workspace.ui.setup.SetupPinScreen
import app.vault.workspace.ui.trash.TrashScreen
import app.vault.workspace.ui.unlock.UnlockScreen
import app.vault.workspace.ui.viewer.ViewerScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

object Routes {
    const val FirstRun = "first_run"
    const val SetupPin = "setup_pin"
    const val Unlock = "unlock"
    const val Library = "library"
    const val Settings = "settings"
    const val Trash = "trash"
    const val Folders = "folders"
    const val Viewer = "viewer/{id}"
    fun viewer(id: String) = "viewer/$id"
}

/** Library folder breadcrumb entry (id + display name). */
private data class FolderCrumb(val id: String, val name: String)

/** Build root→leaf chain from [folder] using in-memory [all] parent links. */
private fun folderChainOf(folder: VaultFolder, all: List<VaultFolder>): List<FolderCrumb> {
    val byId = all.associateBy { it.id }
    val ascending = mutableListOf<FolderCrumb>()
    var cur: VaultFolder? = folder
    val seen = mutableSetOf<String>()
    while (cur != null && cur.id !in seen) {
        seen += cur.id
        ascending += FolderCrumb(cur.id, cur.name)
        cur = cur.parentId?.let { byId[it] }
    }
    return ascending.asReversed()
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun VaultNav(
    session: SessionManager,
    repository: VaultRepository,
    autoLock: AutoLockController,
    pendingShareUris: List<Uri> = emptyList(),
    onShareConsumed: () -> Unit = {},
) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val sessionState by session.state.collectAsState()
    val idleTimeoutMs by autoLock.idleTimeoutMsFlow.collectAsState()
    val start = if (session.isSetupComplete) Routes.Unlock else Routes.FirstRun

    var setupError by remember { mutableStateOf<String?>(null) }
    var unlockError by remember { mutableStateOf<String?>(null) }
    var lockoutMs by remember { mutableLongStateOf(0L) }
    var importing by remember { mutableStateOf(false) }
    var importProgress by remember { mutableStateOf<Pair<String, Float>?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<VaultItem>>(emptyList()) }
    var trashItems by remember { mutableStateOf<List<VaultItem>>(emptyList()) }
    var folders by remember { mutableStateOf<List<VaultFolder>>(emptyList()) }
    // Nested-folder breadcrumb: open pushes/rebuilds chain; back pops one level (exact parent).
    var folderStack by remember { mutableStateOf<List<FolderCrumb>>(emptyList()) }
    val currentFolderId = folderStack.lastOrNull()?.id
    val currentFolderName = folderStack.lastOrNull()?.name
    var activePlayers by remember { mutableStateOf<List<DecryptingPlayback>>(emptyList()) }
    var pendingExport by remember { mutableStateOf<VaultItem?>(null) }
    var moveItemIds by remember { mutableStateOf<List<String>?>(null) }
    var biometricEnabled by remember { mutableStateOf(BiometricVault.isEnabled(context)) }
    var biometricError by remember { mutableStateOf<String?>(null) }
    var storageUsedBytes by remember { mutableLongStateOf(0L) }
    var changePinError by remember { mutableStateOf<String?>(null) }
    var changePinBusy by remember { mutableStateOf(false) }
    var changePinSuccessEpoch by remember { mutableStateOf(0) }
    // Image slideshow hoisted here so play/interval survive image→image navigation.
    var imageSlideshowPlaying by remember { mutableStateOf(false) }
    var imageSlideshowIntervalMs by remember { mutableLongStateOf(3_000L) }
    // Hub-root exit confirm (Library root / bottom-nav leave-app back).
    var showExitConfirm by remember { mutableStateOf(false) }
    val biometricHardware = remember {
        BiometricVault.isBiometricAvailable(context)
    }

    val importController = remember { ImportController(repository) }
    val exportController = remember { ExportController(repository) }

    LaunchedEffect(sessionState, currentFolderId) {
        if (sessionState is SessionManager.SessionState.Unlocked) {
            biometricEnabled = BiometricVault.isEnabled(context)
            // Library collect first for unlock → grid; defer trash/folders/storage one frame.
            launch {
                repository.observeLibrary(currentFolderId).collect { items = it }
            }
            launch {
                yield()
                repository.observeTrashItems().collect { trashItems = it }
            }
            launch {
                yield()
                repository.observeFolders().collect { folders = it }
            }
            launch {
                yield()
                repository.observeTotalStorageBytes().collect { storageUsedBytes = it }
            }
            // Refresh tip crumb name if it changed out-of-band.
            val tip = folderStack.lastOrNull()
            if (tip != null) {
                val fresh = repository.getFolder(tip.id)
                if (fresh != null && fresh.name != tip.name) {
                    folderStack = folderStack.dropLast(1) + FolderCrumb(fresh.id, fresh.name)
                }
            }
        } else {
            items = emptyList()
            trashItems = emptyList()
            folders = emptyList()
            storageUsedBytes = 0L
            folderStack = emptyList()
            activePlayers.forEach { it.release() }
            activePlayers = emptyList()
            ThumbCache.clear()
            imageSlideshowPlaying = false
            autoLock.setPlaybackActive(false)
            autoLock.setDeferBackgroundLock(false)
            if (session.isSetupComplete) {
                nav.navigate(Routes.Unlock) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    androidx.compose.runtime.DisposableEffect(session) {
        val listener: () -> Unit = {
            activePlayers.forEach { it.release() }
            activePlayers = emptyList()
            ThumbCache.clear()
        }
        session.addLockListener(listener)
        onDispose { session.removeLockListener(listener) }
    }

    // Share-into-vault: queue URIs under a stable job id so clearing the activity
    // pending list does not cancel the import coroutine.
    var shareJobId by remember { mutableStateOf(0) }
    var shareUrisForJob by remember { mutableStateOf<List<Uri>>(emptyList()) }
    LaunchedEffect(pendingShareUris) {
        if (pendingShareUris.isNotEmpty()) {
            shareUrisForJob = pendingShareUris.toList()
            shareJobId += 1
            onShareConsumed()
            autoLock.setDeferBackgroundLock(true)
        }
    }
    LaunchedEffect(shareJobId, sessionState) {
        if (shareJobId == 0) return@LaunchedEffect
        val uris = shareUrisForJob
        if (uris.isEmpty()) return@LaunchedEffect
        autoLock.setDeferBackgroundLock(true)
        if (sessionState !is SessionManager.SessionState.Unlocked) {
            return@LaunchedEffect
        }
        importing = true
        importProgress = "Importing shared file(s)…" to 0f
        try {
            val result = importController.importAll(uris) { index, total, frac ->
                val overall = if (total <= 0) frac else (index + frac) / total
                importProgress = "Importing ${index + 1} of $total…" to overall.coerceIn(0f, 1f)
            }
            shareUrisForJob = emptyList()
            val folderId = currentFolderId
            if (folderId != null) {
                result.succeeded.forEach { item ->
                    repository.setItemFolder(item.id, folderId)
                }
            }
            statusMessage = when {
                result.failures.isEmpty() ->
                    "Imported ${result.succeeded.size} shared file(s)"
                result.succeeded.isEmpty() ->
                    "Share import failed: ${result.failures.firstOrNull()?.second ?: "unknown"}"
                else ->
                    "Imported ${result.succeeded.size}, failed ${result.failures.size}"
            }
            nav.navigate(Routes.Library) {
                launchSingleTop = true
            }
        } catch (e: Exception) {
            statusMessage = "Share import failed: ${e.message ?: "error"}"
            shareUrisForJob = emptyList()
        } finally {
            importing = false
            importProgress = null
            autoLock.setDeferBackgroundLock(false)
            autoLock.bumpIdle()
        }
    }

    fun promptBiometricUnlock() {
        val activity = context as? FragmentActivity ?: return
        if (!BiometricVault.isEnabled(context)) return
        if (session.lockoutStore().isLocked()) {
            lockoutMs = session.lockoutStore().remainingLockMs()
            return
        }
        try {
            val cipher = BiometricVault.createCipherForDecrypt(context)
            val executor = ContextCompat.getMainExecutor(context)
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        val crypto = result.cryptoObject?.cipher ?: return
                        scope.launch {
                            try {
                                val vmk = BiometricVault.unwrap(context, crypto)
                                val unlockResult = session.unlockWithVmk(vmk)
                                unlockResult.onSuccess {
                                    unlockError = null
                                    lockoutMs = 0
                                    nav.navigate(Routes.Library) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }.onFailure { e ->
                                    when (e) {
                                        is SessionManager.LockedOutException -> {
                                            lockoutMs = e.remainingMs
                                            unlockError = null
                                        }
                                        else -> unlockError = "Biometric unlock failed"
                                    }
                                }
                            } catch (e: Exception) {
                                unlockError = "Biometric unlock failed"
                            }
                        }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                            errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                            errorCode != BiometricPrompt.ERROR_CANCELED
                        ) {
                            unlockError = errString.toString()
                        }
                    }
                },
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Cyphr")
                .setSubtitle("Use biometrics to unlock")
                .setNegativeButtonText("Use " + session.lockType().displayName)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()
            prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
        } catch (e: Exception) {
            unlockError = "Biometrics unavailable — use your lock"
        }
    }

    fun promptEnableBiometric() {
        val activity = context as? FragmentActivity ?: run {
            biometricError = "Biometrics require a compatible activity"
            return
        }
        try {
            val cipher = BiometricVault.createCipherForEncrypt()
            val executor = ContextCompat.getMainExecutor(context)
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        val crypto = result.cryptoObject?.cipher ?: return
                        scope.launch {
                            try {
                                val vmk = session.requireVmk().copyOf()
                                try {
                                    BiometricVault.wrapAndStore(context, crypto, vmk)
                                    biometricEnabled = true
                                    biometricError = null
                                    statusMessage = "Biometric unlock enabled"
                                } finally {
                                    app.vault.workspace.crypto.KeyHierarchy.wipe(vmk)
                                }
                            } catch (e: Exception) {
                                biometricError = e.message ?: "Could not enable biometrics"
                                biometricEnabled = false
                            }
                        }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                            errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                            errorCode != BiometricPrompt.ERROR_CANCELED
                        ) {
                            biometricError = errString.toString()
                        }
                    }
                },
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Enable biometric unlock")
                .setSubtitle("Confirm to wrap your vault key")
                .setNegativeButtonText("Cancel")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()
            prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
        } catch (e: Exception) {
            biometricError = e.message ?: "Could not enable biometrics"
        }
    }

    val openDocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris: List<Uri> ->
        autoLock.setDeferBackgroundLock(false)
        if (uris.isEmpty()) {
            statusMessage = null
            return@rememberLauncherForActivityResult
        }
        if (session.state.value !is SessionManager.SessionState.Unlocked) {
            statusMessage = "Cyphr locked during import — unlock and try again"
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            importing = true
            importProgress = "Importing ${uris.size} file(s)…" to 0f
            try {
                val result = importController.importAll(uris) { index, total, frac ->
                    val overall = if (total <= 0) frac else (index + frac) / total
                    importProgress =
                        "Importing ${index + 1} of $total…" to overall.coerceIn(0f, 1f)
                }
                // If viewing a folder, place new imports there
                val folderId = currentFolderId
                if (folderId != null) {
                    result.succeeded.forEach { item ->
                        repository.setItemFolder(item.id, folderId)
                    }
                }
                statusMessage = when {
                    result.failures.isEmpty() ->
                        "Imported ${result.succeeded.size} file(s)"
                    result.succeeded.isEmpty() ->
                        "Import failed: ${result.failures.firstOrNull()?.second ?: "unknown error"}"
                    else ->
                        "Imported ${result.succeeded.size}, failed ${result.failures.size}"
                }
            } catch (e: Exception) {
                statusMessage = "Import failed: ${e.message ?: "error"}"
            } finally {
                importing = false
                importProgress = null
            }
        }
    }

    val createDocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*"),
    ) { uri: Uri? ->
        autoLock.setDeferBackgroundLock(false)
        val item = pendingExport
        pendingExport = null
  
