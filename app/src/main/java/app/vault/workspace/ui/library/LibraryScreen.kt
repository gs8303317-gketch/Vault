package app.vault.workspace.ui.library

import android.graphics.Bitmap
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import app.vault.workspace.ui.nav.vaultSharedThumb
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyListItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vault.workspace.data.VaultCategory
import app.vault.workspace.data.VaultItem
import app.vault.workspace.data.formatHumanSize
import app.vault.workspace.data.formatReadableDate
import app.vault.workspace.ui.theme.VaultAccent
import app.vault.workspace.ui.theme.VaultBg
import app.vault.workspace.ui.theme.VaultDanger
import app.vault.workspace.ui.theme.VaultOnAccent
import app.vault.workspace.ui.theme.VaultSurface
import app.vault.workspace.ui.theme.VaultText
import app.vault.workspace.ui.theme.VaultTextMuted

enum class LibrarySort(val label: String) {
    NEWEST("Newest first"),
    OLDEST("Oldest first"),
    NAME_AZ("Name A–Z"),
    NAME_ZA("Name Z–A"),
}

enum class LibraryViewMode(val label: String) {
    GRID("Grid"),
    COMFORTABLE("Comfortable"),
    LIST("List"),
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalSharedTransitionApi::class,
)
@Composable
fun LibraryScreen(
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    items: List<VaultItem>,
    importing: Boolean,
    importProgress: Pair<String, Float>? = null,
    statusMessage: String? = null,
    onDismissStatus: () -> Unit = {},
    onImport: () -> Unit,
    onOpenItem: (VaultItem) -> Unit,
    onSettings: () -> Unit,
    onFolders: () -> Unit = {},
    folderTitle: String? = null,
    onClearFolderFilter: (() -> Unit)? = null,
    onToggleFavorite: (VaultItem) -> Unit,
    onMoveToTrash: (List<String>) -> Unit,
    onMoveToFolder: (List<String>) -> Unit = {},
    onLoadThumb: suspend (id: String) -> Bitmap?,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<VaultCategory?>(null) }
    var favoritesOnly by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val context = LocalContext.current
    val libraryPrefs = remember(context) { LibraryPrefs(context) }
    var sort by remember { mutableStateOf(libraryPrefs.getSort()) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf(libraryPrefs.getViewMode()) }
    var viewMenuOpen by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(statusMessage) {
        val msg = statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        onDismissStatus()
    }

    fun exitSelection() {
        selectionMode = false
        selectedIds = emptySet()
    }

    val filtered = remember(items, query, selectedCategory, favoritesOnly, sort) {
        val q = query.trim()
        val base = items.filter { item ->
            val catOk = selectedCategory == null || item.category == selectedCategory
            val favOk = !favoritesOnly || item.favorite
            val nameOk = q.isEmpty() || item.displayName.contains(q, ignoreCase = true)
            catOk && favOk && nameOk
        }
        when (sort) {
            LibrarySort.NEWEST -> base.sortedByDescending { it.createdAt }
            LibrarySort.OLDEST -> base.sortedBy { it.createdAt }
            LibrarySort.NAME_AZ -> base.sortedBy { it.displayName.lowercase() }
            LibrarySort.NAME_ZA -> base.sortedByDescending { it.displayName.lowercase() }
        }
    }
    val newestId = filtered.firstOrNull()?.id

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
            if (selectionMode) {
                TopAppBar(
                    title = { Text("${selectedIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { exitSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                if (selectedIds.isNotEmpty()) {
                                    onMoveToFolder(selectedIds.toList())
                                    exitSelection()
                                }
                            },
                            enabled = selectedIds.isNotEmpty(),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.DriveFileMove,
                                contentDescription = "Move to folder",
                                tint = if (selectedIds.isNotEmpty()) VaultAccent else VaultTextMuted,
                            )
                        }
                        IconButton(
                            onClick = {
                                if (selectedIds.isNotEmpty()) {
                                    onMoveToTrash(selectedIds.toList())
                                    exitSelection()
                                }
                            },
                            enabled = selectedIds.isNotEmpty(),
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = if (selectedIds.isNotEmpty()) VaultDanger else VaultTextMuted,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultBg),
                )
            } else {
                TopAppBar(
                    title = { Text(folderTitle ?: "Vault") },
                    navigationIcon = {
                        if (onClearFolderFilter != null) {
                            IconButton(onClick = onClearFolderFilter) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                )
                            }
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { viewMenuOpen = true }) {
                                Icon(
                                    when (viewMode) {
                                        LibraryViewMode.GRID -> Icons.Default.GridView
                                        LibraryViewMode.COMFORTABLE -> Icons.Default.ViewAgenda
                                        LibraryViewMode.LIST -> Icons.Default.ViewList
                                    },
                                    contentDescription = "View mode",
                                    tint = VaultAccent,
                                )
                            }
                            DropdownMenu(
                                expanded = viewMenuOpen,
                                onDismissRequest = { viewMenuOpen = false },
                            ) {
                                LibraryViewMode.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                option.label,
                                                color = if (viewMode == option) VaultAccent else VaultText,
                                            )
                                        },
                                        onClick = {
                                            viewMode = option
                                            libraryPrefs.setViewMode(option)
                                            viewMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }
                        Box {
                            IconButton(onClick = { sortMenuOpen = true }) {
                                Icon(
                                    Icons.Default.Sort,
                                    contentDescription = "Sort",
                                    tint = VaultAccent,
                                )
                            }
                            DropdownMenu(
                                expanded = sortMenuOpen,
                                onDismissRequest = { sortMenuOpen = false },
                            ) {
                                LibrarySort.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                option.label,
                                                color = if (sort == option) VaultAccent else VaultText,
                                            )
                                        },
                                        onClick = {
                                            sort = option
                                            libraryPrefs.setSort(option)
                                            sortMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultBg),
                )
            }
        },
        floatingActionButton = {
            if (!selectionMode) {
                val view = LocalView.current
                FloatingActionButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        onImport()
                    },
                    containerColor = VaultAccent,
                    contentColor = VaultOnAccent,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Import")
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (!selectionMode) {
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
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CategoryChip(
                    label = "All",
                    selected = selectedCategory == null && !favoritesOnly,
                    onClick = {
                        selectedCategory = null
                        favoritesOnly = false
                    },
                )
                CategoryChip(
                    label = "★ Favorites",
                    selected = favoritesOnly,
                    onClick = {
                        favoritesOnly = true
                        selectedCategory = null
                    },
                )
                CategoryChip(
                    label = "Photos",
                    selected = selectedCategory == VaultCategory.IMAGE && !favoritesOnly,
                    onClick = {
                        selectedCategory = VaultCategory.IMAGE
                        favoritesOnly = false
                    },
                )
                CategoryChip(
                    label = "Videos",
                    selected = selectedCategory == VaultCategory.VIDEO && !favoritesOnly,
                    onClick = {
                        selectedCategory = VaultCategory.VIDEO
                        favoritesOnly = false
                    },
                )
                CategoryChip(
                    label = "Audio",
                    selected = selectedCategory == VaultCategory.AUDIO && !favoritesOnly,
                    onClick = {
                        selectedCategory = VaultCategory.AUDIO
                        favoritesOnly = false
                    },
                )
                CategoryChip(
                    label = "Documents",
                    selected = selectedCategory == VaultCategory.DOCUMENT && !favoritesOnly,
                    onClick = {
                        selectedCategory = VaultCategory.DOCUMENT
                        favoritesOnly = false
                    },
                )
                CategoryChip(
                    label = "Other",
                    selected = selectedCategory == VaultCategory.OTHER && !favoritesOnly,
                    onClick = {
                        selectedCategory = VaultCategory.OTHER
                        favoritesOnly = false
                    },
                )
            }

            Spacer(Modifier.height(8.dp))

            if (importing) {
                val progress = importProgress
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(VaultSurface)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(
                        progress?.first ?: "Importing…",
                        color = VaultText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (progress?.second ?: 0f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = VaultAccent,
                        trackColor = VaultBg,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

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
                                if (favoritesOnly) {
                                    "No favorites yet. Tap the star on a card."
                                } else {
                                    "Try a different search or category filter."
                                },
                                color = VaultTextMuted,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    else -> {
                        when (viewMode) {
                            LibraryViewMode.LIST -> {
                                LazyColumn(
                                    contentPadding = PaddingValues(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    lazyListItems(filtered, key = { it.id }) { item ->
                                        LibraryListRow(
                                            sharedTransitionScope = sharedTransitionScope,
                                            animatedVisibilityScope = animatedVisibilityScope,
                                            item = item,
                                            selected = item.id in selectedIds,
                                            selectionMode = selectionMode,
                                            isNew = item.id == newestId && sort == LibrarySort.NEWEST,
                                            onLoadThumb = onLoadThumb,

                                    onClick = {
                                        if (selectionMode) {
                                            selectedIds = if (item.id in selectedIds) {
                                                selectedIds - item.id
                                            } else {
                                                selectedIds + item.id
                                            }
                                            if (selectedIds.isEmpty()) {
                                                selectionMode = false
                                            }
                                        } else {
                                            onOpenItem(item)
                                        }
                                    },
                                    onLongClick = {
                                        if (!selectionMode) {
                                            selectionMode = true
                                            selectedIds = setOf(item.id)
                                        }
                                    },
                                    onToggleFavorite = { onToggleFavorite(item) },
                                        )
                                    }
                                }
                            }
                            else -> {
                                val minSize =
                                    if (viewMode == LibraryViewMode.COMFORTABLE) 156.dp else 112.dp
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = minSize),
                                    contentPadding = PaddingValues(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    items(filtered, key = { it.id }) { item ->
                                        LibraryCard(
                                            sharedTransitionScope = sharedTransitionScope,
                                            animatedVisibilityScope = animatedVisibilityScope,
                                            item = item,
                                            selected = item.id in selectedIds,
                                            selectionMode = selectionMode,
                                            isNew = item.id == newestId && sort == LibrarySort.NEWEST,
                                            onLoadThumb = onLoadThumb,

                                    onClick = {
                                        if (selectionMode) {
                                            selectedIds = if (item.id in selectedIds) {
                                                selectedIds - item.id
                                            } else {
                                                selectedIds + item.id
                                            }
                                            if (selectedIds.isEmpty()) {
                                                selectionMode = false
                                            }
                                        } else {
                                            onOpenItem(item)
                                        }
                                    },
                                    onLongClick = {
                                        if (!selectionMode) {
                                            selectionMode = true
                                            selectedIds = setOf(item.id)
                                        }
                                    },
                                    onToggleFavorite = { onToggleFavorite(item) },
                                        )
                                    }
                                }
                            }
                        }
                    }
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun LibraryCard(
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    item: VaultItem,
    selected: Boolean,
    selectionMode: Boolean,
    isNew: Boolean = false,
    onLoadThumb: suspend (String) -> Bitmap?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val thumb by produceState<Bitmap?>(
        initialValue = if (item.hasThumb) ThumbCache.peek(item.id) else null,
        item.id,
        item.hasThumb,
    ) {
        value = if (item.hasThumb) {
            runCatching {
                ThumbCache.get(item.id) { onLoadThumb(item.id) }
            }.getOrNull()
        } else {
            null
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(100),
        label = "libraryCardPress",
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (selected) {
                    Modifier.border(2.dp, VaultAccent, MaterialTheme.shapes.medium)
                } else {
                    Modifier
                },
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        colors = CardDefaults.cardColors(containerColor = VaultSurface),
        shape = MaterialTheme.shapes.medium,
    ) {
        Box(Modifier.fillMaxSize()) {
            if (thumb != null) {
                val useBounds = item.category == VaultCategory.DOCUMENT ||
                    item.category == VaultCategory.OTHER
                Image(
                    bitmap = thumb!!.asImageBitmap(),
                    contentDescription = item.displayName,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(MaterialTheme.shapes.medium)
                        .vaultSharedThumb(
                            sharedTransitionScope,
                            animatedVisibilityScope,
                            item.id,
                            useBounds = useBounds,
                        ),
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

            if (isNew && !selectionMode) {
                Text(
                    "NEW",
                    color = VaultOnAccent,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(VaultAccent, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            if (selectionMode) {
                Icon(
                    imageVector = if (selected) {
                        Icons.Default.CheckCircle
                    } else {
                        Icons.Default.RadioButtonUnchecked
                    },
                    contentDescription = if (selected) "Selected" else "Not selected",
                    tint = if (selected) VaultAccent else Color.White.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(22.dp)
                        .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                        .padding(2.dp),
                )
            } else {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(36.dp),
                ) {
                    Icon(
                        imageVector = if (item.favorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = if (item.favorite) "Unfavorite" else "Favorite",
                        tint = if (item.favorite) VaultAccent else Color.White.copy(alpha = 0.9f),
                        modifier = Modifier
                            .size(20.dp)
                            .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                            .padding(2.dp),
                    )
                }
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


@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun LibraryListRow(
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    item: VaultItem,
    selected: Boolean,
    selectionMode: Boolean,
    isNew: Boolean,
    onLoadThumb: suspend (String) -> Bitmap?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val thumb by produceState<Bitmap?>(
        initialValue = if (item.hasThumb) ThumbCache.peek(item.id) else null,
        item.id,
        item.hasThumb,
    ) {
        value = if (item.hasThumb) {
            runCatching { ThumbCache.get(item.id) { onLoadThumb(item.id) } }.getOrNull()
        } else null
    }
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(VaultSurface)
            .then(if (selected) Modifier.border(2.dp, VaultAccent, RoundedCornerShape(12.dp)) else Modifier)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(VaultBg),
            contentAlignment = Alignment.Center,
        ) {
            if (thumb != null) {
                val useBounds = item.category == VaultCategory.DOCUMENT ||
                    item.category == VaultCategory.OTHER
                Image(
                    bitmap = thumb!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .vaultSharedThumb(
                            sharedTransitionScope,
                            animatedVisibilityScope,
                            item.id,
                            useBounds = useBounds,
                        ),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(categoryIcon(item.category), null, tint = VaultAccent)
            }
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.displayName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = VaultText,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isNew) {
                    Spacer(Modifier.size(6.dp))
                    Text(
                        "NEW",
                        color = VaultOnAccent,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .background(VaultAccent, RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
            Text(
                "${formatHumanSize(item.sizeBytes)} · ${formatReadableDate(item.createdAt)}",
                color = VaultTextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (selectionMode) {
            Icon(
                if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                null,
                tint = if (selected) VaultAccent else VaultTextMuted,
            )
        } else {
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (item.favorite) Icons.Default.Star else Icons.Default.StarBorder,
                    null,
                    tint = if (item.favorite) VaultAccent else VaultTextMuted,
                )
            }
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
