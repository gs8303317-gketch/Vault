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
