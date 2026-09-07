package app.vault.workspace.ui.folders

import app.vault.workspace.ui.nav.VaultMotion
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vault.workspace.data.VaultFolder
import app.vault.workspace.ui.theme.VaultAccent
import app.vault.workspace.ui.theme.VaultAmoled
import app.vault.workspace.ui.theme.VaultDanger
import app.vault.workspace.ui.theme.VaultOnAccent
import app.vault.workspace.ui.theme.VaultSurface
import app.vault.workspace.ui.theme.VaultText
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
        containerColor = VaultAmoled,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Folders",
                            fontWeight = FontWeight.SemiBold,
                            color = VaultText,
                        )
                        Text(
                            if (folders.isEmpty()) "No folders yet" else "${folders.size} folders",
                            color = VaultTextMuted,
                            style = MaterialTheme.typography.labelMedium,
                            fontSize = 12.sp,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = VaultText,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VaultAmoled,
                    titleContentColor = VaultText,
                    navigationIconContentColor = VaultText,
                    actionIconContentColor = VaultAccent,
                ),
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = true,
                enter = VaultMotion.fabEnter,
                exit = VaultMotion.fabExit,
            ) {
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(horizontal = 36.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(VaultSurface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.CreateNewFolder,
                            contentDescription = null,
                            tint = VaultAccent,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                    Text(
                        "No folders yet",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = VaultText,
                    )
                    Text(
                        "Tap + to create a folder, then move items into it.",
                        color = VaultTextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.widthIn(max = 280.dp),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(folders, key = { it.id }) { folder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(VaultSurface)
                            .border(1.dp, VaultAccent.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                            .combinedClickable(
                                onClick = { onOpenFolder(folder) },
                                onLongClick = {
                                    renameName = folder.name
                                    pendingRename = folder
                                },
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(VaultAccent.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                tint = VaultAccent,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.size(14.dp))
                        Text(
                            folder.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = VaultText,
                            modifier = Modifier.weight(1f),
                        )
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
                }
            }
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = VaultAccent,
        unfocusedBorderColor = VaultAccent.copy(alpha = 0.35f),
        focusedLabelColor = VaultAccent,
        cursorColor = VaultAccent,
        focusedTextColor = VaultText,
        unfocusedTextColor = VaultText,
        focusedContainerColor = VaultAmoled,
        unfocusedContainerColor = VaultAmoled,
    )

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            properties = VaultMotion.dialogProperties,
            containerColor = VaultSurface,
            title = {
                Text("New folder", fontWeight = FontWeight.SemiBold, color = VaultText)
            },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                    shape = RoundedCornerShape(14.dp),
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
                TextButton(onClick = { showCreate = false }) {
                    Text("Cancel", color = VaultTextMuted)
                }
            },
        )
    }

    pendingRename?.let { folder ->
        AlertDialog(
            onDismissRequest = { pendingRename = null },
            properties = VaultMotion.dialogProperties,
            containerColor = VaultSurface,
            title = {
                Text("Rename folder", fontWeight = FontWeight.SemiBold, color = VaultText)
            },
            text = {
                OutlinedTextField(
                    value = renameName,
                    onValueChange = { renameName = it },
                    singleLine = true,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                    shape = RoundedCornerShape(14.dp),
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
                TextButton(onClick = { pendingRename = null }) {
                    Text("Cancel", color = VaultTextMuted)
                }
            },
        )
    }

    pendingDelete?.let { folder ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            properties = VaultMotion.dialogProperties,
            containerColor = VaultSurface,
            title = {
                Text("Delete folder?", fontWeight = FontWeight.SemiBold, color = VaultText)
            },
            text = {
                Text(
                    "“${folder.name}” will be deleted. Items inside move back to the main library (not trash).",
                    color = VaultTextMuted,
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
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel", color = VaultTextMuted)
                }
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
        properties = VaultMotion.dialogProperties,
        containerColor = VaultSurface,
        title = {
            Text("Move to folder", fontWeight = FontWeight.SemiBold, color = VaultText)
        },
        text = {
            Column {
                ListItem(
                    headlineContent = { Text("No folder (library root)", color = VaultText) },
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
                        headlineContent = { Text(folder.name, color = VaultText) },
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
            TextButton(onClick = onDismiss) { Text("Cancel", color = VaultTextMuted) }
        },
    )
}
