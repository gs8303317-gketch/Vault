package app.vault.workspace.ui.viewer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.media3.exoplayer.ExoPlayer
import app.vault.workspace.data.VaultCategory
import app.vault.workspace.data.VaultItem
import app.vault.workspace.data.VaultRepository
import app.vault.workspace.ui.export.ExportConfirmDialog
import app.vault.workspace.ui.theme.VaultBg
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    item: VaultItem,
    repository: VaultRepository,
    onBack: () -> Unit,
    onRequestExport: (VaultItem) -> Unit,
    onPlaybackActive: (Boolean) -> Unit,
    onPlayerCreated: (ExoPlayer) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showExportConfirm by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = VaultBg,
        topBar = {
            TopAppBar(
                title = { Text(item.displayName, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showExportConfirm = true }) {
                        Icon(Icons.Default.FileUpload, contentDescription = "Export")
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Export") },
                            onClick = {
                                menuOpen = false
                                showExportConfirm = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Info") },
                            onClick = { menuOpen = false },
                        )
                    }
                },
            )
        },
    ) { padding ->
        val mod = Modifier.fillMaxSize().padding(padding)
        when (item.category) {
            VaultCategory.IMAGE -> ImageViewer(
                loadBytes = { repository.decryptFully(item.id) },
                modifier = mod,
            )
            VaultCategory.VIDEO, VaultCategory.AUDIO -> MediaPlayerScreen(
                vatFile = repository.blobFile(item.id),
                loadDek = { repository.unwrapDek(item.id) },
                mimeType = item.mimeType,
                onPlaybackActive = onPlaybackActive,
                onPlayerCreated = onPlayerCreated,
                modifier = mod,
            )
            VaultCategory.DOCUMENT -> {
                when {
                    item.mimeType.equals("application/pdf", ignoreCase = true) -> {
                        PdfViewer(
                            openTempPdf = { repository.decryptToTempPdf(item.id) },
                            onCloseCleanup = { f -> f?.delete() },
                            modifier = mod,
                        )
                    }
                    item.mimeType.startsWith("text/") ||
                        item.mimeType in setOf(
                            "application/json",
                            "application/xml",
                        ) -> {
                        TextFileViewer(
                            loadBytes = { repository.decryptFully(item.id) },
                            modifier = mod,
                        )
                    }
                    else -> OtherFileScreen(
                        item = item,
                        onExport = { showExportConfirm = true },
                        modifier = mod,
                    )
                }
            }
            VaultCategory.OTHER -> OtherFileScreen(
                item = item,
                onExport = { showExportConfirm = true },
                modifier = mod,
            )
        }
    }

    if (showExportConfirm) {
        ExportConfirmDialog(
            fileName = item.displayName,
            onConfirm = {
                showExportConfirm = false
                onRequestExport(item)
            },
            onDismiss = { showExportConfirm = false },
        )
    }
}
