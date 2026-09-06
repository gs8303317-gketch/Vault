package app.vault.workspace.ui.folders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.vault.workspace.data.VaultFolder
import app.vault.workspace.ui.theme.VaultAccent
import app.vault.workspace.ui.theme.VaultBg
import app.vault.workspace.ui.theme.VaultDanger
import app.vault.workspace.ui.theme.VaultOnAccent
import app.vault.workspace.ui.theme.VaultTextMuted

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FoldersScreen(
    folders: List<VaultFolder>,
    onBack: () -> Unit,
    onOpenFolder: (VaultFolder) -> Unit,
    onCreateFolder: (String) -> Unit,
    onRenameFolder: (VaultFolder, String) -> Unit,
    onDeleteFolder: (VaultFolder) -> Unit,
) {
    var showCreate by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<VaultFolder?>(null) }
    var pendingRename by remember { mutableStateOf<VaultFolder?>(null) }
    var newName by remember { mutableStateOf("") }
    var renameName by remember { mutableStateOf("") }

    Scaffold(
        containerColor = VaultBg,
        topBar = {
            TopAppBar(
                title = { Text("Folders") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultBg),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    newName = ""
                    showCreate = true
                },
                containerColor = VaultAccent,
                contentColor = VaultOnAccent,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create folder")
            }
        },
    ) { padding ->
        if (folders.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CreateNewFolder,
                        contentDescription = null,
                        tint = VaultAccent,
                    )
                    Text("No folders yet", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Tap + to create a folder, then move items into it.",
                        color = VaultTextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(folders, key = { it.id }) { folder ->
                    ListItem(
                        headlineContent = { Text(folder.name) },
                        leadingContent = {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = VaultAccent)
                        },
                        trailingContent = {
                            Row {
                                IconButton(
                                    onClick = {
                                        renameName = folder.name
                                        pendingRename = folder
                                    },
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Rename folder",
                                        tint = VaultAccent,
                                    )
                                }
                                IconButton(onClick = { pendingDelete = folder }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete folder",
                                        tint = VaultDanger,
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onOpenFolder(folder) },
                                onLongClick = {
                                    renameName = folder.name
                                    pendingRename = folder
                                },
                            ),
                    )
                }
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("New folder") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val n = newName.trim()
                        if (n.isNotEmpty()) {
                            onCreateFolder(n)
                            showCreate = false
                        }
                    },
                ) { Text("Create", color = VaultAccent) }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text("Cancel") }
            },
        )
    }


    pendingRename?.let { folder ->
        AlertDialog(
            onDismissRequest = { pendingRename = null },
            title = { Text("Rename folder") },
            text = {
                OutlinedTextField(
                    value = renameName,
                    onValueChange = { renameName = it },
                    singleLine = true,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val n = renameName.trim()
                        if (n.isNotEmpty()) {
                            onRenameFolder(folder, n)
                            pendingRename = null
                        }
                    },
                ) { Text("Rename", color = VaultAccent) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRename = null }) { Text("Cancel") }
            },
        )
    }

    pendingDelete?.let { folder ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete folder?") },
            text = {
                Text(
                    "“${folder.name}” will be deleted. Items inside move back to the main library (not trash).",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteFolder(folder)
                        pendingDelete = null
                    },
                ) { Text("Delete", color = VaultDanger) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
fun MoveToFolderDialog(
    folders: List<VaultFolder>,
    onDismiss: () -> Unit,
    onSelect: (folderId: String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to folder") },
        text = {
            Column {
                ListItem(
                    headlineContent = { Text("No folder (library root)") },
                    leadingContent = {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = VaultTextMuted)
                    },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    },
                    modifier = Modifier.clickable { onSelect(null) },
                )
                folders.forEach { folder ->
                    ListItem(
                        headlineContent = { Text(folder.name) },
                        leadingContent = {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = VaultAccent)
                        },
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                        },
                        modifier = Modifier.clickable { onSelect(folder.id) },
                    )
                }
                if (folders.isEmpty()) {
                    Text(
                        "No folders yet. Create one from Folders.",
                        color = VaultTextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
