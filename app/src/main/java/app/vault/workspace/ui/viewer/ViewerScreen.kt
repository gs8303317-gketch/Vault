package app.vault.workspace.ui.viewer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.platform.LocalContext
import app.vault.workspace.media.DecryptingPlayback
import app.vault.workspace.data.VaultCategory
import app.vault.workspace.data.VaultItem
import app.vault.workspace.data.VaultRepository
import app.vault.workspace.data.formatHumanSize
import app.vault.workspace.data.formatReadableDate
import app.vault.workspace.ui.export.ExportConfirmDialog
import app.vault.workspace.ui.library.ThumbCache
import app.vault.workspace.ui.theme.VaultAccent
import app.vault.workspace.ui.theme.VaultBg
import app.vault.workspace.ui.theme.VaultDanger
import app.vault.workspace.ui.theme.VaultSurface
import app.vault.workspace.ui.theme.VaultText
import app.vault.workspace.ui.theme.VaultTextMuted
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    item: VaultItem,
    repository: VaultRepository,
    onBack: () -> Unit,
    onRequestExport: (VaultItem) -> Unit,
    onToggleFavorite: (VaultItem) -> Unit,
    onMoveToTrash: (VaultItem) -> Unit,
    onMoveToFolder: (VaultItem) -> Unit = {},
    onPlaybackActive: (Boolean) -> Unit,
    onPlayerCreated: (DecryptingPlayback) -> Unit,
    onPreviousMedia: (() -> Unit)? = null,
    onNextMedia: (() -> Unit)? = null,
    slideshowPlaying: Boolean = false,
    onSlideshowPlayingChange: (Boolean) -> Unit = {},
    slideshowIntervalMs: Long = 3_000L,
    onSlideshowIntervalMsChange: (Long) -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showExportConfirm by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var showTrashConfirm by remember { mutableStateOf(false) }
    var chromeVisible by remember { mutableStateOf(true) }
    var chromeBump by remember { mutableIntStateOf(0) }
    var videoPlaying by remember { mutableStateOf(false) }
    var playerLocked by remember { mutableStateOf(false) }
    var localPlayer by remember { mutableStateOf<DecryptingPlayback?>(null) }
    val view = LocalView.current
    val context = LocalContext.current

    val isPdfDocument = item.category == VaultCategory.DOCUMENT &&
        item.mimeType.equals("application/pdf", ignoreCase = true)
    val isTextDocument = item.category == VaultCategory.DOCUMENT &&
        TextEncoding.isTextDocumentMime(item.mimeType)
    val immersive = item.category == VaultCategory.IMAGE ||
        item.category == VaultCategory.VIDEO ||
        isPdfDocument ||
        isTextDocument

    // Auto-hide chrome for images / PDF / text; tool-rail taps bump [chromeBump] to reset the timer.
    // Video chrome follows MediaPlayer controls (not this timer).
    LaunchedEffect(chromeVisible, chromeBump, immersive, item.category, item.id, isPdfDocument, isTextDocument) {
        if (immersive &&
            chromeVisible &&
            (item.category == VaultCategory.IMAGE || isPdfDocument || isTextDocument)
        ) {
            delay(3_500)
            chromeVisible = false
        }
    }

    fun toggleChrome() {
        chromeVisible = !chromeVisible
        if (chromeVisible) chromeBump++
    }

    fun keepChromeVisible() {
        chromeVisible = true
        chromeBump++
    }

    fun exitViewer() {
        // Pause + restore system bars before nav pop; DisposableEffect releases ExoPlayer.
        val p = localPlayer
        try {
            p?.player?.playWhenReady = false
            p?.player?.pause()
        } catch (_: Exception) {
        }
        videoPlaying = false
        onPlaybackActive(false)
        // Restore status/nav bars before the pop transition (avoids immersive jank).
        runCatching {
            var ctx: Context? = context
            var act: Activity? = null
            while (ctx != null) {
                if (ctx is Activity) { act = ctx; break }
                ctx = (ctx as? ContextWrapper)?.baseContext
            }
            act?.let { activity ->
                val controller = WindowCompat.getInsetsController(activity.window, view)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onBack()
    }

    // Gesture-lock blocks back; otherwise release player then pop (smooth video Back).
    BackHandler(enabled = playerLocked || immersive) {
        if (!playerLocked) exitViewer()
    }

    if (immersive) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            when {
                item.category == VaultCategory.IMAGE -> ImageViewer(
                    loadBytes = { repository.decryptFully(item.id) },
                    mimeType = item.mimeType,
                    modifier = Modifier.fillMaxSize(),
                    onSingleTap = { toggleChrome() },
                    onPrevious = onPreviousMedia,
                    onNext = onNextMedia,
                    title = item.displayName,
                    controlsVisible = chromeVisible,
                    slideshowPlaying = slideshowPlaying,
                    onSlideshowPlayingChange = onSlideshowPlayingChange,
                    slideshowIntervalMs = slideshowIntervalMs,
                    onSlideshowIntervalMsChange = onSlideshowIntervalMsChange,
                    onCropConfirm = { left, top, right, bottom ->
                        repository.cropAndReplaceImage(item.id, left, top, right, bottom).also { result ->
                            if (result.isSuccess) ThumbCache.remove(item.id)
                        }
                    },
                    onControlsInteraction = { keepChromeVisible() },
                )
                item.category == VaultCategory.VIDEO -> MediaPlayerScreen(
                    vatFile = repository.blobFile(item.id),
                    loadDek = { repository.unwrapDek(item.id) },
                    mimeType = item.mimeType,
                    title = item.displayName,
                    itemId = item.id,
                    onPlaybackActive = { active ->
                        videoPlaying = active
                        onPlaybackActive(active)
                        if (!active && !playerLocked) chromeVisible = true
                    },
                    onPlayerCreated = { playback ->
                        localPlayer = playback
                        onPlayerCreated(playback)
                    },
                    onControlsVisibilityChanged = { visible -> chromeVisible = visible },
                    onGesturesLockedChanged = { locked ->
                        playerLocked = locked
                        if (locked) {
                            chromeVisible = false
                            menuOpen = false
                        }
                    },
                    onPrevious = onPreviousMedia,
                    onNext = onNextMedia,
                    modifier = Modifier.fillMaxSize(),
                )
                isPdfDocument -> PdfViewer(
                    openPdfHandle = { repository.openPdfHandle(item.id) },
                    itemId = item.id,
                    title = item.displayName,
                    modifier = Modifier.fillMaxSize(),
                    onSingleTap = { toggleChrome() },
                    controlsVisible = chromeVisible,
                    onControlsInteraction = { keepChromeVisible() },
                )
                isTextDocument -> TextFileViewer(
                    loadBytes = { repository.decryptFully(item.id) },
                    itemId = item.id,
                    modifier = Modifier.fillMaxSize(),
                    onSingleTap = { toggleChrome() },
                    controlsVisible = chromeVisible,
                    onControlsInteraction = { keepChromeVisible() },
                )
                else -> {}
            }

            AnimatedVisibility(
                visible = chromeVisible,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                ViewerTopChrome(
                    item = item,
                    menuOpen = menuOpen,
                    onMenuOpenChange = { menuOpen = it },
                    onBack = { exitViewer() },
                    onShowExport = { showExportConfirm = true },
                    onShowInfo = { showInfo = true },
                    onToggleFavorite = { onToggleFavorite(item) },
                    onShowTrash = { showTrashConfirm = true },
                    onShowMoveFolder = { onMoveToFolder(item) },
                    overlay = true,
                )
            }
        }
    } else {
        Scaffold(
            containerColor = VaultBg,
            topBar = {
                ViewerTopChrome(
                    item = item,
                    menuOpen = menuOpen,
                    onMenuOpenChange = { menuOpen = it },
                    onBack = onBack,
                    onShowExport = { showExportConfirm = true },
                    onShowInfo = { showInfo = true },
                    onToggleFavorite = { onToggleFavorite(item) },
                    onShowTrash = { showTrashConfirm = true },
                    onShowMoveFolder = { onMoveToFolder(item) },
                    overlay = false,
                )
            },
        ) { padding ->
            val mod = Modifier.fillMaxSize().padding(padding)
            when (item.category) {
                VaultCategory.AUDIO -> MediaPlayerScreen(
                    vatFile = repository.blobFile(item.id),
                    loadDek = { repository.unwrapDek(item.id) },
                    mimeType = item.mimeType,
                    title = item.displayName,
                    itemId = item.id,
                    onPlaybackActive = onPlaybackActive,
                    onPlayerCreated = onPlayerCreated,
                    onGesturesLockedChanged = { locked -> playerLocked = locked },
                    onPrevious = onPreviousMedia,
                    onNext = onNextMedia,
                    modifier = mod,
                )
                VaultCategory.DOCUMENT -> {
                    when {
                        item.mimeType.equals("application/pdf", ignoreCase = true) -> {
                            // Immersive path above; fallback if chrome routing changes.
                            PdfViewer(
                                openPdfHandle = { repository.openPdfHandle(item.id) },
                                itemId = item.id,
                                title = item.displayName,
                                modifier = mod,
                                onSingleTap = { toggleChrome() },
                                controlsVisible = chromeVisible,
                                onControlsInteraction = { keepChromeVisible() },
                            )
                        }
                        TextEncoding.isTextDocumentMime(item.mimeType) -> {
                            // Immersive path above; fallback if chrome routing changes.
                            TextFileViewer(
                                loadBytes = { repository.decryptFully(item.id) },
                                itemId = item.id,
                                modifier = mod,
                                onSingleTap = { toggleChrome() },
                                controlsVisible = chromeVisible,
                                onControlsInteraction = { keepChromeVisible() },
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
                else -> {}
            }
        }
    }

    if (showExportConfirm) {
        ExportConfirmDialog(
            fileName = item.displayName,
            stripsLocationExif = item.category == VaultCategory.IMAGE &&
                (item.mimeType.equals("image/jpeg", ignoreCase = true) ||
                    item.mimeType.equals("image/jpg", ignoreCase = true)),
            onConfirm = {
                showExportConfirm = false
                onRequestExport(item)
            },
            onDismiss = { showExportConfirm = false },
        )
    }

    if (showTrashConfirm) {
        AlertDialog(
            onDismissRequest = { showTrashConfirm = false },
            title = { Text("Move to trash?") },
            text = {
                Text("“${item.displayName}” will be moved to Trash. You can restore it later.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showTrashConfirm = false
                        onMoveToTrash(item)
                    },
                ) {
                    Text("Move to trash", color = VaultDanger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTrashConfirm = false }) {
                    Text("Cancel")
                }
            },
            containerColor = VaultSurface,
        )
    }

    if (showInfo) {
        ItemInfoSheet(
            item = item,
            onDismiss = { showInfo = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewerTopChrome(
    item: VaultItem,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onShowExport: () -> Unit,
    onShowInfo: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShowTrash: () -> Unit,
    onShowMoveFolder: () -> Unit = {},
    overlay: Boolean,
) {
    val colors = if (overlay) {
        TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Black.copy(alpha = 0.55f),
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White,
            actionIconContentColor = Color.White,
        )
    } else {
        TopAppBarDefaults.topAppBarColors(containerColor = VaultBg)
    }

    TopAppBar(
        modifier = if (overlay) Modifier.statusBarsPadding() else Modifier,
        title = { Text(item.displayName, maxLines = 1) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            IconButton(onClick = onShowExport) {
                Icon(Icons.Default.FileUpload, contentDescription = "Export")
            }
            IconButton(onClick = { onMenuOpenChange(true) }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More")
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { onMenuOpenChange(false) },
            ) {
                DropdownMenuItem(
                    text = {
                        Text(if (item.favorite) "Unfavorite" else "Favorite")
                    },
                    leadingIcon = {
                        Icon(
                            if (item.favorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            tint = VaultAccent,
                        )
                    },
                    onClick = {
                        onMenuOpenChange(false)
                        onToggleFavorite()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Move to folder") },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null, tint = VaultAccent)
                    },
                    onClick = {
                        onMenuOpenChange(false)
                        onShowMoveFolder()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Move to trash") },
                    leadingIcon = {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = VaultDanger)
                    },
                    onClick = {
                        onMenuOpenChange(false)
                        onShowTrash()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Info") },
                    leadingIcon = {
                        Icon(Icons.Default.Info, contentDescription = null)
                    },
                    onClick = {
                        onMenuOpenChange(false)
                        onShowInfo()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Export") },
                    leadingIcon = {
                        Icon(Icons.Default.FileUpload, contentDescription = null)
                    },
                    onClick = {
                        onMenuOpenChange(false)
                        onShowExport()
                    },
                )
            }
        },
        colors = colors,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemInfoSheet(
    item: VaultItem,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = VaultSurface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text("Info", style = MaterialTheme.typography.titleLarge, color = VaultText)
            Spacer(Modifier.height(16.dp))
            InfoRow("Name", item.displayName)
            InfoRow("Category", item.category.name.lowercase().replaceFirstChar { it.titlecase() })
            InfoRow("MIME", item.mimeType)
            InfoRow("Size", formatHumanSize(item.sizeBytes))
            InfoRow("Created", formatReadableDate(item.createdAt))
            InfoRow("Thumbnail", if (item.hasThumb) "Yes" else "No")
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = VaultBg)
            Text("File ID", style = MaterialTheme.typography.labelMedium, color = VaultTextMuted)
            Text(
                item.id,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                ),
                color = VaultTextMuted,
            )
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Close", color = VaultAccent)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = VaultTextMuted, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            color = VaultText,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f, fill = false),
        )
    }
}
