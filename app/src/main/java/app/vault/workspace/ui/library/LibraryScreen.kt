package app.vault.workspace.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vault.workspace.data.VaultCategory
import app.vault.workspace.data.VaultItem
import app.vault.workspace.ui.theme.VaultAccent
import app.vault.workspace.ui.theme.VaultBg
import app.vault.workspace.ui.theme.VaultOnAccent
import app.vault.workspace.ui.theme.VaultSurface
import app.vault.workspace.ui.theme.VaultTextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    items: List<VaultItem>,
    importing: Boolean,
    statusMessage: String? = null,
    onDismissStatus: () -> Unit = {},
    onImport: () -> Unit,
    onOpenItem: (VaultItem) -> Unit,
    onSettings: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusMessage) {
        val msg = statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        onDismissStatus()
    }

    Scaffold(
        containerColor = VaultBg,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = VaultSurface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    actionColor = VaultAccent,
                )
            }
        },
        topBar = {
            TopAppBar(
                title = { Text("Vault") },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultBg),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onImport,
                containerColor = VaultAccent,
                contentColor = VaultOnAccent,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Import")
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (items.isEmpty() && !importing) {
                Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("No files yet", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Tap + to import photos, videos, audio, or documents.",
                        color = VaultTextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 112.dp),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items, key = { it.id }) { item ->
                        LibraryCard(item = item, onClick = { onOpenItem(item) })
                    }
                }
            }
            if (importing) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = VaultAccent,
                )
            }
        }
    }
}

@Composable
private fun LibraryCard(item: VaultItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = VaultSurface),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(
                imageVector = when (item.category) {
                    VaultCategory.IMAGE -> Icons.Default.Image
                    VaultCategory.VIDEO -> Icons.Default.VideoFile
                    VaultCategory.AUDIO -> Icons.Default.AudioFile
                    VaultCategory.DOCUMENT -> Icons.Default.Description
                    VaultCategory.OTHER -> Icons.AutoMirrored.Filled.InsertDriveFile
                },
                contentDescription = item.category.name,
                tint = VaultAccent,
            )
            Text(
                item.displayName,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
