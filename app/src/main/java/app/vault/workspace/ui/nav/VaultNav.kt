package app.vault.workspace.ui.nav

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.vault.workspace.auth.AutoLockController
import app.vault.workspace.auth.SessionManager
import app.vault.workspace.data.VaultItem
import app.vault.workspace.data.VaultRepository
import app.vault.workspace.export.ExportController
import app.vault.workspace.import.ImportController
import app.vault.workspace.ui.library.LibraryScreen
import app.vault.workspace.ui.settings.SettingsScreen
import app.vault.workspace.ui.setup.FirstRunScreen
import app.vault.workspace.ui.setup.SetupPinScreen
import app.vault.workspace.ui.unlock.UnlockScreen
import app.vault.workspace.ui.viewer.ViewerScreen
import kotlinx.coroutines.launch

object Routes {
    const val FirstRun = "first_run"
    const val SetupPin = "setup_pin"
    const val Unlock = "unlock"
    const val Library = "library"
    const val Settings = "settings"
    const val Viewer = "viewer/{id}"
    fun viewer(id: String) = "viewer/$id"
}

private val IMPORT_MIME_TYPES = arrayOf(
    "image/*",
    "video/*",
    "audio/*",
    "application/pdf",
    "text/*",
    "application/zip",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "*/*",
)

@Composable
fun VaultNav(
    session: SessionManager,
    repository: VaultRepository,
    autoLock: AutoLockController,
) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val sessionState by session.state.collectAsState()
    val start = if (session.isSetupComplete) Routes.Unlock else Routes.FirstRun

    var setupError by remember { mutableStateOf<String?>(null) }
    var unlockError by remember { mutableStateOf<String?>(null) }
    var lockoutMs by remember { mutableLongStateOf(0L) }
    var importing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<VaultItem>>(emptyList()) }
    var activePlayers by remember { mutableStateOf<List<ExoPlayer>>(emptyList()) }
    var pendingExport by remember { mutableStateOf<VaultItem?>(null) }

    val importController = remember { ImportController(repository) }
    val exportController = remember { ExportController(repository) }

    LaunchedEffect(sessionState) {
        if (sessionState is SessionManager.SessionState.Unlocked) {
            repository.observeLibrary().collect { items = it }
        } else {
            items = emptyList()
            activePlayers.forEach { it.release() }
            activePlayers = emptyList()
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
        }
        session.addLockListener(listener)
        onDispose { session.removeLockListener(listener) }
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
            statusMessage = "Importing ${uris.size} file(s)…"
            try {
                val result = importController.importAll(uris)
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

    NavHost(navController = nav, startDestination = start) {
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
            )
        }
        composable(Routes.Library) {
            LibraryScreen(
                items = items,
                importing = importing,
                statusMessage = statusMessage,
                onDismissStatus = { statusMessage = null },
                onImport = {
                    autoLock.setDeferBackgroundLock(true)
                    openDocLauncher.launch(IMPORT_MIME_TYPES)
                },
                onOpenItem = { item ->
                    autoLock.bumpIdle()
                    nav.navigate(Routes.viewer(item.id))
                },
                onSettings = { nav.navigate(Routes.Settings) },
            )
        }
        composable(Routes.Settings) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onLockNow = {
                    session.lock()
                },
            )
        }
        composable(
            Routes.Viewer,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("id") ?: return@composable
            var item by remember { mutableStateOf<VaultItem?>(null) }
            LaunchedEffect(id) {
                item = repository.getItem(id)
            }
            val current = item
            if (current != null) {
                ViewerScreen(
                    item = current,
                    repository = repository,
                    onBack = { nav.popBackStack() },
                    onRequestExport = { vaultItem ->
                        pendingExport = vaultItem
                        autoLock.setDeferBackgroundLock(true)
                        createDocLauncher.launch(vaultItem.displayName)
                    },
                    onPlaybackActive = { active -> autoLock.setPlaybackActive(active) },
                    onPlayerCreated = { p -> activePlayers = activePlayers + p },
                )
            }
        }
    }
}
