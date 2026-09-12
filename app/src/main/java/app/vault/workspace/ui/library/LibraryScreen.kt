package app.vault.workspace.ui.library

import app.vault.workspace.ui.nav.VaultMotion
import androidx.compose.animation.AnimatedVisibility
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import app.vault.workspace.data.VaultCategory
import app.vault.workspace.data.VaultItem
import app.vault.workspace.data.formatHumanSize
import app.vault.workspace.data.formatReadableDate
import app.vault.workspace.ui.theme.VaultAccent
import app.vault.workspace.ui.theme.VaultAmoled
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
    // rememberSaveable so hub tab restore / dialog dismiss does not reset filters.
    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var selectedCategoryName by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedCategory: VaultCategory? = selectedCategoryName?.let { name ->
        VaultCategory.entries.find { it.name == name }
    }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val context = LocalContext.current
    val libraryPrefs = remember(context) { LibraryPrefs(context) }
    var sortName by rememberSaveable { mutableStateOf(libraryPrefs.getSort().name) }
    val sort = LibrarySort.valueOf(sortName)
    var sortMenuOpen by remember { mutableStateOf(false) }
    var viewModeName by rememberSaveable { mutableStateOf(libraryPrefs.getViewMode().name) }
    val viewMode = LibraryViewMode.valueOf(viewModeName)
    var viewMenuOpen by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    LaunchedEffect(statusMessage) {
        val msg = statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        onDismissStatus()
    }

    fun exitSelection() {
        selectionMode = false
        selectedIds = emptySet()
    }

    BackHandler(enabled = selectionMode) { exitSelection() }
    BackHandler(enabled = searchOpen && !selectionMode) {
        searchOpen = false
        query = ""
        focusManager.clearFocus()
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
        containerColor = VaultAmoled,
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
                    title = {
                        Text(
                            "${selectedIds.size} selected",
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { exitSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                selectedIds = filtered.map { it.id }.toSet()
                            },
                            enabled = filtered.isNotEmpty(),
                        ) {
                            Text(
                                "All",
                                color = if (filtered.isNotEmpty()) VaultAccent else VaultTextMuted,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }
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
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = VaultAmoled,
                        titleContentColor = VaultText,
                        navigationIconContentColor = VaultText,
                        actionIconContentColor = VaultText,
                    ),
                )
            } else if (searchOpen) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("Search library", color = VaultTextMuted) },
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
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                cursorColor = VaultAccent,
                                focusedTextColor = VaultText,
                                unfocusedTextColor = VaultText,
                            ),
                            textStyle = MaterialTheme.typography.titleMedium.copy(color = VaultText),
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                searchOpen = false
                                query = ""
                                focusManager.clearFocus()
                            },
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close search",
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
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                folderTitle ?: "Cyphr",
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val countLabel = when {
                                favoritesOnly -> "${filtered.size} favorites"
                                selectedCategory != null -> "${filtered.size} · ${selectedCategory!!.name.lowercase().replaceFirstChar { it.titlecase() }}"
                                query.isNotBlank() -> "${filtered.size} results"
                                else -> "${items.size} items"
                            }
                            Text(
                                countLabel,
                                color = VaultTextMuted,
                                style = MaterialTheme.typography.labelMedium,
                                fontSize = 12.sp,
                            )
                        }
                    },
                    navigationIcon = {
                        if (onClearFolderFilter != null) {
                            IconButton(onClick = onClearFolderFilter) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Up",
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { searchOpen = true }) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search",
                                tint = VaultAccent,
                            )
                        }
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
                                            viewModeName = option.name
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
                                            sortName = option.name
                                            libraryPrefs.setSort(option)
                                            sortMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = VaultAmoled,
                        titleContentColor = VaultText,
                        navigationIconContentColor = VaultText,
                        actionIconContentColor = VaultAccent,
                    ),
                )
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = !selectionMode,
                enter = VaultMotion.fabEnter,
                exit = VaultMotion.fabExit,
            ) {
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CategoryChip(
                        label = "All",
                        selected = selectedCategory == null && !favoritesOnly,
                        icon = Icons.Default.Inbox,
                        onClick = {
                            selectedCategoryName = null
                            favoritesOnly = false
                        },
                    )
                    CategoryChip(
                        label = "Favorites",
                        selected = favoritesOnly,
                        icon = if (favoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        onClick = {
                            favoritesOnly = true
                            selectedCategoryName = null
                        },
                    )
                    CategoryChip(
                        label = "Images",
                        selected = selectedCategory == VaultCategory.IMAGE && !favoritesOnly,
                        icon = Icons.Default.Image,
                        onClick = {
                            selectedCategoryName = VaultCategory.IMAGE.name
                            favoritesOnly = false
                        },
                    )
                    CategoryChip(
                        label = "Video",
                        selected = selectedCategory == VaultCategory.VIDEO && !favoritesOnly,
                        icon = Icons.Default.VideoFile,
                        onClick = {
                            selectedCategoryName = VaultCategory.VIDEO.name
                            favoritesOnly = false
                        },
                    )
                    CategoryChip(
                        label = "Audio",
                        selected = selectedCategory == VaultCategory.AUDIO && !favoritesOnly,
                        icon = Icons.Default.AudioFile,
                        onClick = {
                            selectedCategoryName = VaultCategory.AUDIO.name
                            favoritesOnly = false
                        },
                    )
                    CategoryChip(
                        label = "Docs",
                        selected = selectedCategory == VaultCategory.DOCUMENT && !favoritesOnly,
                        icon = Icons.Default.Description,
                        onClick = {
                            selectedCategoryName = VaultCategory.DOCUMENT.name
                            favoritesOnly = false
                        },
                    )
                    CategoryChip(
                        label = "Other",
                        selected = selectedCategory == VaultCategory.OTHER && !favoritesOnly,
                        icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                        onClick = {
                            selectedCategoryName = VaultCategory.OTHER.name
                            favoritesOnly = false
                        },
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            AnimatedVisibility(
                visible = importing,
                enter = VaultMotion.overlayEnter,
                exit = VaultMotion.overlayExit,
            ) {
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
                        LibraryEmptyState(
                            icon = Icons.Default.Lock,
                            title = "Your library is empty",
                            body = "Import photos, videos, audio, or documents. Everything stays encrypted on this device.",
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    filtered.isEmpty() && !importing -> {
                        LibraryEmptyState(
                            icon = if (favoritesOnly) Icons.Default.FavoriteBorder else Icons.Default.Search,
                            title = if (favoritesOnly) "No favorites yet" else "No matches",
                            body = if (favoritesOnly) {
                                "Tap the star on any item to pin it here."
                            } else {
                                "Try another search or clear the category filter."
                            },
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    else -> {
                        when (viewMode) {
                            LibraryViewMode.LIST -> {
                                LazyColumn(
                                    state = listState,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
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
                                    if (viewMode == LibraryViewMode.COMFORTABLE) 160.dp else 104.dp
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = minSize),
                                    state = gridState,
                                    contentPadding = PaddingValues(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
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
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        },
        leadingIcon = icon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = VaultAccent,
            selectedLabelColor = VaultOnAccent,
            selectedLeadingIconColor = VaultOnAccent,
            containerColor = VaultSurface,
            labelColor = VaultTextMuted,
            iconColor = VaultTextMuted,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = Color.Transparent,
            selectedBorderColor = VaultAccent,
        ),
        shape = RoundedCornerShape(50),
        modifier = Modifier.heightIn(min = 34.dp),
    )
}

@Composable
private fun LibraryEmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(VaultSurface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = VaultAccent,
                modifier = Modifier.size(40.dp),
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = VaultText,
        )
        Text(
            body,
            color = VaultTextMuted,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.widthIn(max = 280.dp),
        )
    }
}

private val LibraryThumbShape = RoundedCornerShape(14.dp)

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
        targetValue = if (pressed) VaultMotion.PressScale else 1f,
        animationSpec = tween(VaultMotion.PressMs),
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
                    Modifier.border(2.dp, VaultAccent, LibraryThumbShape)
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
        shape = LibraryThumbShape,
    ) {
        Box(Modifier.fillMaxSize()) {
            if (thumb != null) {
                val useBounds = item.category == VaultCategory.DOCUMENT ||
                    item.category == VaultCategory.OTHER
                val imageBitmap = remember(thumb) { thumb!!.asImageBitmap() }
                Image(
                    bitmap = imageBitmap,
                    contentDescription = item.displayName,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(LibraryThumbShape)
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

            // Type badge (bottom-end) — gallery-style density cue
            if (!selectionMode) {
                val badge = categoryBadge(item.category)
                if (badge != null) {
                    Text(
                        badge,
                        color = VaultText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 8.dp, bottom = 34.dp)
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
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
                    .padding(horizontal = 10.dp, vertical = 8.dp),
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
            .clip(LibraryThumbShape)
            .background(VaultSurface)
            .then(if (selected) Modifier.border(2.dp, VaultAccent, LibraryThumbShape) else Modifier)
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
                .clip(RoundedCornerShape(10.dp))
                .background(VaultAmoled),
            contentAlignment = Alignment.Center,
        ) {
            if (thumb != null) {
                val useBounds = item.category == VaultCategory.DOCUMENT ||
                    item.category == VaultCategory.OTHER
                val imageBitmap = remember(thumb) { thumb!!.asImageBitmap() }
                Image(
                    bitmap = imageBitmap,
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

private fun categoryBadge(category: VaultCategory): String? =
    when (category) {
        VaultCategory.IMAGE -> null
        VaultCategory.VIDEO -> "VIDEO"
        VaultCategory.AUDIO -> "AUDIO"
        VaultCategory.DOCUMENT -> "DOC"
        VaultCategory.OTHER -> "FILE"
    }

private fun categoryIcon(category: VaultCategory): ImageVector =
    when (category) {
        VaultCategory.IMAGE -> Icons.Default.Image
        VaultCategory.VIDEO -> Icons.Default.VideoFile
        VaultCategory.AUDIO -> Icons.Default.AudioFile
        VaultCategory.DOCUMENT -> Icons.Default.Description
        VaultCategory.OTHER -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
