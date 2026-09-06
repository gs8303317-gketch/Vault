package app.vault.workspace.ui.nav

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
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
import app.vault.workspace.auth.SessionManager
import app.vault.workspace.data.VaultFolder
import app.vault.workspace.data.VaultCategory
import app.vault.workspace.data.VaultItem
import app.vault.workspace.data.VaultRepository
import app.vault.workspace.export.ExportController
import app.vault.workspace.import.ImportController
import app.vault.workspace.ui.folders.FoldersScreen
import app.vault.workspace.ui.theme.VaultAccent
import app.vault.workspace.ui.theme.VaultBg
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
    var currentFolderId by remember { mutableStateOf<String?>(null) }
    var currentFolderName by remember { mutableStateOf<String?>(null) }
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
    val biometricHardware = remember {
        BiometricVault.isBiometricAvailable(context)
    }

    val importController = remember { ImportController(repository) }
    val exportController = remember { ExportController(repository) }

    LaunchedEffect(sessionState, currentFolderId) {
        if (sessionState is SessionManager.SessionState.Unlocked) {
            biometricEnabled = BiometricVault.isEnabled(context)
            launch {
                repository.observeLibrary(currentFolderId).collect { items = it }
            }
            launch {
                repository.observeTrashItems().collect { trashItems = it }
            }
            launch {
                repository.observeFolders().collect { folders = it }
            }
            launch {
                repository.observeTotalStorageBytes().collect { storageUsedBytes = it }
            }
            if (currentFolderId != null) {
                currentFolderName = repository.getFolder(currentFolderId!!)?.name
            } else {
                currentFolderName = null
            }
        } else {
            items = emptyList()
            trashItems = emptyList()
            folders = emptyList()
            storageUsedBytes = 0L
            currentFolderId = null
            currentFolderName = null
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
                .setTitle("Unlock Vault")
                .setSubtitle("Use biometrics to unlock")
                .setNegativeButtonText("Use PIN")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()
            prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
        } catch (e: Exception) {
            unlockError = "Biometrics unavailable — use PIN"
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
            statusMessage = "Vault locked during import — unlock and try again"
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
        if (uri == null || item == null) return@rememberLauncherForActivityResult
        if (session.state.value !is SessionManager.SessionState.Unlocked) {
            statusMessage = "Vault locked during export — unlock and try again"
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val result = exportController.export(item.id, uri)
            statusMessage = result.fold(
                onSuccess = { "Exported ${item.displayName}" },
                onFailure = { "Export failed: ${it.message ?: "error"}" },
            )
        }
    }

    val navBackStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    BackHandler(enabled = currentFolderId != null && currentRoute == Routes.Library) {
        currentFolderId = null
        currentFolderName = null
    }

    val hubRoutes = setOf(Routes.Library, Routes.Folders, Routes.Settings)
    val showBottomBar = currentRoute in hubRoutes

    fun navigateHub(route: String) {
        if (route == Routes.Library) {
            nav.navigate(Routes.Library) {
                popUpTo(Routes.Library) { inclusive = false }
                launchSingleTop = true
            }
        } else {
            nav.navigate(route) {
                popUpTo(Routes.Library) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        containerColor = VaultBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = VaultSurface) {
                    val itemColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = VaultAccent,
                        selectedTextColor = VaultAccent,
                        indicatorColor = VaultAccent.copy(alpha = 0.22f),
                        unselectedIconColor = VaultTextMuted,
                        unselectedTextColor = VaultTextMuted,
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.Library,
                        onClick = { navigateHub(Routes.Library) },
                        icon = {
                            Icon(Icons.Default.VideoLibrary, contentDescription = "Library")
                        },
                        label = { Text("Library") },
                        colors = itemColors,
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.Folders,
                        onClick = { navigateHub(Routes.Folders) },
                        icon = {
                            Icon(Icons.Default.Folder, contentDescription = "Folders")
                        },
                        label = { Text("Folders") },
                        colors = itemColors,
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.Settings,
                        onClick = { navigateHub(Routes.Settings) },
                        icon = {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        },
                        label = { Text("Settings") },
                        colors = itemColors,
                    )
                }
            }
        },
    ) { scaffoldPadding ->
    NavHost(
        navController = nav,
        startDestination = start,
        modifier = Modifier
            .fillMaxSize()
            .padding(scaffoldPadding),
    ) {
        composable(Routes.FirstRun) {
            FirstRunScreen(onContinue = { nav.navigate(Routes.SetupPin) })
        }
        composable(Routes.SetupPin) {
            SetupPinScreen(
                errorMessage = setupError,
                onPinConfirmed = { pin ->
                    scope.launch {
                        val result = session.setup(pin)
                        result.onSuccess {
                            setupError = null
                            nav.navigate(Routes.Library) {
                                popUpTo(0) { inclusive = true }
                            }
                        }.onFailure {
                            setupError = it.message ?: "Setup failed"
                        }
                    }
                },
            )
        }
        composable(Routes.Unlock) {
            val bioReady = biometricHardware && BiometricVault.isEnabled(context)
            UnlockScreen(
                lockedOutMs = lockoutMs,
                errorMessage = unlockError,
                onSubmitPin = { pin ->
                    scope.launch {
                        val result = session.unlock(pin)
                        result.onSuccess {
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
                                is SessionManager.WrongPinException -> {
                                    unlockError = "Wrong PIN"
                                    lockoutMs = session.lockoutStore().remainingLockMs()
                                }
                                is SessionManager.CorruptHeaderException -> {
                                    unlockError = null
                                    nav.navigate(Routes.FirstRun) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                                else -> unlockError = "Unlock failed"
                            }
                        }
                    }
                },
                biometricAvailable = bioReady,
                onBiometricUnlock = if (bioReady) {
                    { promptBiometricUnlock() }
                } else {
                    null
                },
            )
        }
        composable(Routes.Library) {
            LibraryScreen(
                items = items,
                importing = importing,
                importProgress = importProgress,
                statusMessage = statusMessage,
                onDismissStatus = { statusMessage = null },
                onImport = {
                    autoLock.setDeferBackgroundLock(true)
                    try {
                        // OpenMultipleDocuments uses SAF; arrayOf("*/*") avoids OEM MIME-list quirks.
                        openDocLauncher.launch(arrayOf("*/*"))
                    } catch (e: Exception) {
                        autoLock.setDeferBackgroundLock(false)
                        statusMessage = "Could not open file picker: ${e.message ?: "error"}"
                    }
                },
                onOpenItem = { item ->
                    autoLock.bumpIdle()
                    nav.navigate(Routes.viewer(item.id))
                },
                onSettings = { nav.navigate(Routes.Settings) },
                onFolders = { nav.navigate(Routes.Folders) },
                folderTitle = currentFolderName,
                onClearFolderFilter = if (currentFolderId != null) {
                    {
                        currentFolderId = null
                        currentFolderName = null
                    }
                } else {
                    null
                },
                onToggleFavorite = { item ->
                    scope.launch {
                        repository.setFavorite(item.id, !item.favorite)
                    }
                },
                onMoveToTrash = { ids ->
                    scope.launch {
                        ids.forEach { repository.moveToTrash(it) }
                        statusMessage = if (ids.size == 1) {
                            "Moved to trash"
                        } else {
                            "Moved ${ids.size} items to trash"
                        }
                    }
                },
                onMoveToFolder = { ids ->
                    moveItemIds = ids
                },
                onLoadThumb = { id -> repository.loadThumbBitmap(id) },
            )
        }
        composable(Routes.Folders) {
            FoldersScreen(
                folders = folders,
                onBack = { nav.popBackStack() },
                onOpenFolder = { folder ->
                    currentFolderId = folder.id
                    currentFolderName = folder.name
                    // Return to library (or create it) with folder filter applied
                    if (!nav.popBackStack(Routes.Library, inclusive = false)) {
                        nav.navigate(Routes.Library) {
                            popUpTo(Routes.Unlock) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                },
                onCreateFolder = { name ->
                    scope.launch {
                        val result = repository.createFolder(name)
                        statusMessage = result.fold(
                            onSuccess = { "Created “${it.name}”" },
                            onFailure = { "Could not create folder: ${it.message}" },
                        )
                    }
                },
                onRenameFolder = { folder, name ->
                    scope.launch {
                        val result = repository.renameFolder(folder.id, name)
                        statusMessage = result.fold(
                            onSuccess = {
                                if (currentFolderId == folder.id) {
                                    currentFolderName = name.trim()
                                }
                                "Renamed to “${name.trim()}”"
                            },
                            onFailure = { "Could not rename folder: ${it.message}" },
                        )
                    }
                },
                onDeleteFolder = { folder ->
                    scope.launch {
                        repository.deleteFolder(folder.id)
                        if (currentFolderId == folder.id) {
                            currentFolderId = null
                            currentFolderName = null
                        }
                        statusMessage = "Deleted folder “${folder.name}”"
                    }
                },
            )
        }
        composable(Routes.Settings) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onLockNow = {
                    session.lock()
                },
                onOpenTrash = { nav.navigate(Routes.Trash) },
                onOpenFolders = { nav.navigate(Routes.Folders) },
                idleTimeoutMs = idleTimeoutMs,
                onIdleTimeoutSelected = { autoLock.setIdleTimeoutMs(it) },
                biometricHardwareAvailable = biometricHardware,
                biometricEnabled = biometricEnabled,
                onBiometricToggle = { enable ->
                    if (enable) {
                        promptEnableBiometric()
                    } else {
                        BiometricVault.disable(context)
                        biometricEnabled = false
                        biometricError = null
                        statusMessage = "Biometric unlock disabled"
                    }
                },
                biometricError = biometricError,
                storageUsedBytes = storageUsedBytes,
                changePinError = changePinError,
                changePinBusy = changePinBusy,
                changePinSuccessEpoch = changePinSuccessEpoch,
                onClearChangePinError = { changePinError = null },
                onChangePin = { current, newPin ->
                    scope.launch {
                        changePinBusy = true
                        changePinError = null
                        val result = session.changePin(current, newPin)
                        changePinBusy = false
                        result.onSuccess {
                            biometricEnabled = false
                            changePinSuccessEpoch += 1
                            statusMessage =
                                "PIN changed. Biometric unlock was turned off — re-enable in Settings if desired."
                        }.onFailure { e ->
                            changePinError = when (e) {
                                is SessionManager.WrongPinException -> "Wrong current PIN"
                                else -> e.message ?: "Could not change PIN"
                            }
                        }
                    }
                },
            )
        }
        composable(Routes.Trash) {
            TrashScreen(
                items = trashItems,
                onBack = { nav.popBackStack() },
                onRestore = { item ->
                    scope.launch {
                        repository.restoreFromTrash(item.id)
                        statusMessage = "Restored ${item.displayName}"
                    }
                },
                onDeleteForever = { item ->
                    scope.launch {
                        repository.hardDelete(item.id)
                        statusMessage = "Deleted forever"
                    }
                },
                onEmptyTrash = {
                    scope.launch {
                        val n = trashItems.size
                        repository.emptyTrash()
                        statusMessage = "Emptied trash ($n)"
                    }
                },
                onLoadThumb = { id -> repository.loadThumbBitmap(id) },
            )
        }
        composable(
            Routes.Viewer,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("id") ?: return@composable
            val fromLibrary = items.find { it.id == id }
            var fetched by remember(id) { mutableStateOf<VaultItem?>(null) }
            LaunchedEffect(id, fromLibrary) {
                if (fromLibrary == null) {
                    fetched = repository.getItem(id)
                }
            }
            val current = fromLibrary ?: fetched
            if (current != null) {
                // Image queue for gallery/slideshow; AV queue for player prev/next.
                val mediaQueue = when (current.category) {
                    VaultCategory.IMAGE -> items.filter { it.category == VaultCategory.IMAGE }
                    VaultCategory.VIDEO, VaultCategory.AUDIO -> items.filter {
                        it.category == VaultCategory.VIDEO || it.category == VaultCategory.AUDIO
                    }
                    else -> emptyList()
                }
                val mediaIndex = mediaQueue.indexOfFirst { it.id == current.id }
                // Slideshow/gallery wrap only for images; AV player stays linear.
                val wrapImages = current.category == VaultCategory.IMAGE && mediaQueue.size > 1
                val prevMedia = when {
                    mediaIndex < 0 || mediaQueue.isEmpty() -> null
                    mediaIndex > 0 -> mediaQueue[mediaIndex - 1]
                    wrapImages -> mediaQueue.last()
                    else -> null
                }
                val nextMedia = when {
                    mediaIndex < 0 || mediaQueue.isEmpty() -> null
                    mediaIndex < mediaQueue.lastIndex -> mediaQueue[mediaIndex + 1]
                    wrapImages -> mediaQueue.first()
                    else -> null
                }
                ViewerScreen(
                    item = current,
                    repository = repository,
                    onBack = {
                        imageSlideshowPlaying = false
                        autoLock.setPlaybackActive(false)
                        nav.popBackStack()
                    },
                    onRequestExport = { vaultItem ->
                        pendingExport = vaultItem
                        autoLock.setDeferBackgroundLock(true)
                        createDocLauncher.launch(vaultItem.displayName)
                    },
                    onToggleFavorite = { vaultItem ->
                        scope.launch {
                            repository.setFavorite(vaultItem.id, !vaultItem.favorite)
                        }
                    },
                    onMoveToTrash = { vaultItem ->
                        scope.launch {
                            imageSlideshowPlaying = false
                            repository.moveToTrash(vaultItem.id)
                            statusMessage = "Moved to trash"
                            nav.popBackStack()
                        }
                    },
                    onMoveToFolder = { vaultItem ->
                        moveItemIds = listOf(vaultItem.id)
                    },
                    onPlaybackActive = { active -> autoLock.setPlaybackActive(active) },
                    onPlayerCreated = { p ->
                        // One live player: release any prior session (prev/next / reopen).
                        activePlayers.forEach { old -> if (old !== p) old.release() }
                        activePlayers = listOf(p)
                    },
                    onPreviousMedia = prevMedia?.let { prev ->
                        {
                            nav.navigate(Routes.viewer(prev.id)) {
                                popUpTo(Routes.viewer(current.id)) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    },
                    onNextMedia = nextMedia?.let { next ->
                        {
                            nav.navigate(Routes.viewer(next.id)) {
                                popUpTo(Routes.viewer(current.id)) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    },
                    slideshowPlaying = imageSlideshowPlaying,
                    onSlideshowPlayingChange = { imageSlideshowPlaying = it },
                    slideshowIntervalMs = imageSlideshowIntervalMs,
                    onSlideshowIntervalMsChange = { imageSlideshowIntervalMs = it },
                )
            }
        }
    }

    } // Scaffold

    moveItemIds?.let { ids ->
        MoveToFolderDialog(
            folders = folders,
            onDismiss = { moveItemIds = null },
            onSelect = { folderId ->
                scope.launch {
                    ids.forEach { repository.setItemFolder(it, folderId) }
                    val label = if (folderId == null) {
                        "library root"
                    } else {
                        folders.find { it.id == folderId }?.name ?: "folder"
                    }
                    statusMessage = if (ids.size == 1) {
                        "Moved to $label"
                    } else {
                        "Moved ${ids.size} items to $label"
                    }
                    moveItemIds = null
                }
            },
        )
    }
}
