package app.vault.workspace.ui.library

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vault.workspace.data.VaultCategory
import app.vault.workspace.data.VaultItem
import app.vault.workspace.ui.theme.VaultAccent
import app.vault.workspace.ui.theme.VaultBg
import app.vault.workspace.ui.theme.VaultOnAccent
import app.vault.workspace.ui.theme.VaultSurface
import app.vault.workspace.ui.theme.VaultText
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
    onLoadThumb: suspend (id: String) -> Bitmap?,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<VaultCategory?>(null) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(statusMessage) {
        val msg = statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        onDismissStatus()
    }

    val filtered = remember(items, query, selectedCategory) {
        val q = query.trim()
        items.filter { item ->
            val catOk = selectedCategory == null || item.category == selectedCategory
            val nameOk = q.isEmpty() || item.displayName.contains(q, ignoreCase = true)
            catOk && nameOk
        }
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
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                singleLine = true,
                placeholder = { Text("Search by name", color = VaultTextMuted) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = VaultTextMuted)
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VaultAccent,
                    unfocusedBorderColor = VaultSurface,
                    focusedContainerColor = VaultSurface,
                    unfocusedContainerColor = VaultSurface,
                    cursorColor = VaultAccent,
                    focusedTextColor = VaultText,
                    unfocusedTextColor = VaultText,
                ),
                shape = RoundedCornerShape(12.dp),
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CategoryChip(
                    label = "All",
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                )
                CategoryChip(
                    label = "Photos",
                    selected = selectedCategory == VaultCategory.IMAGE,
                    onClick = { selectedCategory = VaultCategory.IMAGE },
                )
                CategoryChip(
                    label = "Videos",
                    selected = selectedCategory == VaultCategory.VIDEO,
                    onClick = { selectedCategory = VaultCategory.VIDEO },
                )
                CategoryChip(
                    label = "Audio",
                    selected = selectedCategory == VaultCategory.AUDIO,
                    onClick = { selectedCategory = VaultCategory.AUDIO },
                )
                CategoryChip(
                    label = "Documents",
                    selected = selectedCategory == VaultCategory.DOCUMENT,
                    onClick = { selectedCategory = VaultCategory.DOCUMENT },
                )
                CategoryChip(
                    label = "Other",
                    selected = selectedCategory == VaultCategory.OTHER,
                    onClick = { selectedCategory = VaultCategory.OTHER },
                )
            }

            Spacer(Modifier.height(8.dp))

            Box(Modifier.fillMaxSize()) {
                when {
                    items.isEmpty() && !importing -> {
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
                    }
                    filtered.isEmpty() && !importing -> {
                        Column(
                            Modifier.align(Alignment.Center).padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("No matches", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Try a different search or category filter.",
                                color = VaultTextMuted,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 112.dp),
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(filtered, key = { it.id }) { item ->
                                LibraryCard(
                                    item = item,
                                    onLoadThumb = onLoadThumb,
                                    onClick = { onOpenItem(item) },
                                )
                            }
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
}

@Composable
private fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = VaultAccent,
            selectedLabelColor = VaultOnAccent,
            containerColor = VaultSurface,
            labelColor = VaultTextMuted,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = VaultSurface,
            selectedBorderColor = VaultAccent,
        ),
    )
}

@Composable
private fun LibraryCard(
    item: VaultItem,
    onLoadThumb: suspend (String) -> Bitmap?,
    onClick: () -> Unit,
) {
    val thumb by produceState<Bitmap?>(initialValue = null, item.id, item.hasThumb) {
        value = if (item.hasThumb) {
            runCatching { onLoadThumb(item.id) }.getOrNull()
        } else {
            null
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = VaultSurface),
        shape = MaterialTheme.shapes.medium,
    ) {
        Box(Modifier.fillMaxSize()) {
            if (thumb != null) {
                Image(
                    bitmap = thumb!!.asImageBitmap(),
                    contentDescription = item.displayName,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(MaterialTheme.shapes.medium),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xCC0B0C0E)),
                            ),
                        ),
                )
            } else {
                Icon(
                    imageVector = categoryIcon(item.category),
                    contentDescription = item.category.name,
                    tint = VaultAccent,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(36.dp)
                        .padding(bottom = 12.dp),
                )
            }
            Text(
                item.displayName,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
                color = VaultText,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(10.dp),
            )
        }
    }
}

private fun categoryIcon(category: VaultCategory): ImageVector =
    when (category) {
        VaultCategory.IMAGE -> Icons.Default.Image
        VaultCategory.VIDEO -> Icons.Default.VideoFile
        VaultCategory.AUDIO -> Icons.Default.AudioFile
        VaultCategory.DOCUMENT -> Icons.Default.Description
        VaultCategory.OTHER -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
