@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.vault.workspace.ui.viewer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.WidthNormal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.vault.workspace.media.EncryptedPdfHandle
import app.vault.workspace.media.PdfPageStore
import app.vault.workspace.ui.theme.VaultAccent
import app.vault.workspace.ui.theme.VaultSurface
import app.vault.workspace.ui.theme.VaultText
import app.vault.workspace.ui.theme.VaultTextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

private enum class PdfFitMode { FIT_PAGE, FIT_WIDTH }

/** Invert / dark-paper ColorMatrix (white paper → dark). */
internal val PdfInvertColorMatrix = ColorMatrix(
    floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f,
    ),
)

/**
 * Premium PDF viewer (Android PdfRenderer — no PDF.js).
 *
 * - Vertical pager with snap + clear page indicator
 * - Immersive chrome (tap to show/hide)
 * - Pinch / double-tap zoom with pan clamp
 * - Invert / dark paper, fit width / fit page
 * - Keep-screen-on while open
 * - Thumbnail strip + page grid jump (+ jump dialog)
 * - Resume last page per [itemId] via [PdfPageStore]
 *
 * Opens via [openPdfHandle] (proxy / memfd / ashmem — no durable plaintext temp).
 */
@Composable
fun PdfViewer(
    openPdfHandle: suspend () -> EncryptedPdfHandle,
    itemId: String,
    title: String? = null,
    modifier: Modifier = Modifier,
    onSingleTap: () -> Unit = {},
    controlsVisible: Boolean = true,
    onControlsInteraction: () -> Unit = {},
) {
    var handle by remember { mutableStateOf<EncryptedPdfHandle?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var initialPage by remember { mutableIntStateOf(0) }
    var ready by remember { mutableStateOf(false) }
    val density = LocalDensity.current.density
    var pageZoomed by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var showPageGrid by remember { mutableStateOf(false) }
    var jumpResetToken by remember { mutableIntStateOf(0) }
    var invert by remember { mutableStateOf(false) }
    var fitMode by remember { mutableStateOf(PdfFitMode.FIT_PAGE) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val view = LocalView.current
    val pageStore = remember(context) { PdfPageStore(context) }
    val latestOnSingleTap by rememberUpdatedState(onSingleTap)
    val latestOnControlsInteraction by rememberUpdatedState(onControlsInteraction)

    // Keep screen on while PDF is open.
    DisposableEffect(Unit) {
        val window = view.context.findPdfActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(itemId) {
        ready = false
        error = null
        try {
            val h = withContext(Dispatchers.IO) { openPdfHandle() }
            handle = h
            val r = PdfRenderer(h.pfd)
            renderer = r
            pageCount = r.pageCount
            initialPage = PdfPageStore.resumePageIndex(pageStore.getPageIndex(itemId), r.pageCount)
            ready = true
        } catch (e: Exception) {
            error = e.message ?: "Cannot open PDF"
            handle?.releaseResources()
            handle = null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                renderer?.close()
            } catch (_: Exception) {
            }
            renderer = null
            handle?.releaseResources()
            handle = null
        }
    }

    val bg = if (invert) Color(0xFF0D0D0D) else Color(0xFF1A1A1A)

    Box(
        modifier
            .fillMaxSize()
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        when {
            error != null -> Text(error!!, color = Color.White)
            !ready || renderer == null -> CircularProgressIndicator(color = VaultAccent)
            else -> {
                val pagerState = rememberPagerState(
                    initialPage = initialPage,
                    pageCount = { pageCount },
                )
                val thumbListState = rememberLazyListState()

                LaunchedEffect(pagerState.currentPage) {
                    pageZoomed = false
                }

                // Persist last page for resume.
                LaunchedEffect(pagerState, itemId, pageCount) {
                    snapshotFlow { pagerState.currentPage }
                        .distinctUntilChanged()
                        .collect { page ->
                            pageStore.savePageIndex(itemId, page, pageCount)
                        }
                }

                // Keep thumbnail strip scrolled to current page.
                LaunchedEffect(pagerState.currentPage, controlsVisible) {
                    if (controlsVisible && pageCount > 0) {
                        runCatching {
                            thumbListState.animateScrollToItem(
                                pagerState.currentPage.coerceIn(0, pageCount - 1),
                            )
                        }
                    }
                }

                Box(Modifier.fillMaxSize()) {
                    VerticalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = !pageZoomed,
                        beyondViewportPageCount = 0,
                    ) { page ->
                        PdfPage(
                            renderer = renderer!!,
                            pageIndex = page,
                            scaleFactor = (density * 2.5f).coerceIn(2f, 3.5f),
                            resetToken = jumpResetToken,
                            invert = invert,
                            fitMode = fitMode,
                            onZoomedChanged = { zoomed ->
                                if (page == pagerState.currentPage) {
                                    pageZoomed = zoomed
                                }
                            },
                            onSingleTap = { latestOnSingleTap() },
                        )
                    }

                    AnimatedVisibility(
                        visible = controlsVisible,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.BottomCenter),
                    ) {
                        PdfBottomChrome(
                            pageCount = pageCount,
                            currentPage = pagerState.currentPage,
                            renderer = renderer!!,
                            invert = invert,
                            fitMode = fitMode,
                            thumbListState = thumbListState,
                            onToggleInvert = {
                                latestOnControlsInteraction()
                                invert = !invert
                            },
                            onToggleFit = {
                                latestOnControlsInteraction()
                                fitMode = if (fitMode == PdfFitMode.FIT_PAGE) {
                                    PdfFitMode.FIT_WIDTH
                                } else {
                                    PdfFitMode.FIT_PAGE
                                }
                                jumpResetToken++
                            },
                            onOpenGrid = {
                                latestOnControlsInteraction()
                                showPageGrid = true
                            },
                            onPrev = {
                                latestOnControlsInteraction()
                                val prev = pagerState.currentPage - 1
                                if (prev >= 0) {
                                    scope.launch {
                                        jumpResetToken++
                                        pageZoomed = false
                                        pagerState.animateScrollToPage(prev)
                                    }
                                }
                            },
                            onNext = {
                                latestOnControlsInteraction()
                                val next = pagerState.currentPage + 1
                                if (next < pageCount) {
                                    scope.launch {
                                        jumpResetToken++
                                        pageZoomed = false
                                        pagerState.animateScrollToPage(next)
                                    }
                                }
                            },
                            onJump = { index ->
                                latestOnControlsInteraction()
                                scope.launch {
                                    jumpResetToken++
                                    pageZoomed = false
                                    pagerState.scrollToPage(index)
                                }
                            },
                            onPageLabelClick = {
                                latestOnControlsInteraction()
                                showJumpDialog = true
                            },
                        )
                    }
                }

                if (showJumpDialog) {
                    PdfJumpDialog(
                        pageCount = pageCount,
                        currentPageOneBased = pagerState.currentPage + 1,
                        onDismiss = { showJumpDialog = false },
                        onJump = { oneBased ->
                            val index = clampPdfPageIndex(oneBased, pageCount)
                            showJumpDialog = false
                            scope.launch {
                                jumpResetToken++
                                pageZoomed = false
                                pagerState.scrollToPage(index)
                            }
                        },
                    )
                }

                if (showPageGrid) {
                    PdfPageGridDialog(
                        title = title,
                        renderer = renderer!!,
                        pageCount = pageCount,
                        currentPage = pagerState.currentPage,
                        invert = invert,
                        onDismiss = { showPageGrid = false },
                        onSelect = { index ->
                            showPageGrid = false
                            scope.launch {
                                jumpResetToken++
                                pageZoomed = false
                                pagerState.scrollToPage(index)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfBottomChrome(
    pageCount: Int,
    currentPage: Int,
    renderer: PdfRenderer,
    invert: Boolean,
    fitMode: PdfFitMode,
    thumbListState: androidx.compose.foundation.lazy.LazyListState,
    onToggleInvert: () -> Unit,
    onToggleFit: () -> Unit,
    onOpenGrid: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onJump: (Int) -> Unit,
    onPageLabelClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.78f))
            .navigationBarsPadding()
            .padding(bottom = 4.dp),
    ) {
        if (pageCount > 1) {
            LazyRow(
                state = thumbListState,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(pageCount, key = { it }) { index ->
                    PdfThumb(
                        renderer = renderer,
                        pageIndex = index,
                        selected = index == currentPage,
                        invert = invert,
                        onClick = { onJump(index) },
                    )
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onPrev, enabled = currentPage > 0) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous page",
                    tint = if (currentPage > 0) VaultAccent else VaultTextMuted,
                )
            }
            Text(
                "Page ${currentPage + 1} / $pageCount",
                color = VaultAccent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable(enabled = pageCount > 0, onClick = onPageLabelClick)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            )
            IconButton(onClick = onToggleFit) {
                Icon(
                    if (fitMode == PdfFitMode.FIT_WIDTH) Icons.Default.AspectRatio else Icons.Default.WidthNormal,
                    contentDescription = if (fitMode == PdfFitMode.FIT_WIDTH) "Fit page" else "Fit width",
                    tint = VaultAccent,
                )
            }
            IconButton(onClick = onToggleInvert) {
                Icon(
                    if (invert) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = if (invert) "Normal paper" else "Dark paper",
                    tint = VaultAccent,
                )
            }
            IconButton(onClick = onOpenGrid) {
                Icon(
                    Icons.Default.GridView,
                    contentDescription = "Page grid",
                    tint = VaultAccent,
                )
            }
            IconButton(onClick = onNext, enabled = currentPage < pageCount - 1) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next page",
                    tint = if (currentPage < pageCount - 1) VaultAccent else VaultTextMuted,
                )
            }
        }
    }
}

@Composable
private fun PdfThumb(
    renderer: PdfRenderer,
    pageIndex: Int,
    selected: Boolean,
    invert: Boolean,
    onClick: () -> Unit,
) {
    var bitmap by remember(pageIndex) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(pageIndex) {
        bitmap = withContext(Dispatchers.IO) {
            renderPdfPage(renderer, pageIndex, scaleFactor = 0.35f)
        }
    }
    val borderColor = if (selected) VaultAccent else Color.Transparent
    Box(
        Modifier
            .width(48.dp)
            .height(68.dp)
            .border(2.dp, borderColor, RoundedCornerShape(4.dp))
            .background(if (invert) Color(0xFF111111) else Color.White, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                contentScale = ContentScale.Fit,
                colorFilter = if (invert) ColorFilter.colorMatrix(PdfInvertColorMatrix) else null,
                modifier = Modifier.fillMaxSize().padding(2.dp),
            )
        } else {
            CircularProgressIndicator(
                color = VaultAccent,
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
            )
        }
    }
}

@Composable
private fun PdfPageGridDialog(
    title: String?,
    renderer: PdfRenderer,
    pageCount: Int,
    currentPage: Int,
    invert: Boolean,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxSize(0.85f),
            color = VaultSurface,
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (!title.isNullOrBlank()) title else "Pages",
                        color = VaultText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 1,
                    )
                    TextButton(onClick = onDismiss) {
                        Text("Close", color = VaultAccent)
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(96.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items((0 until pageCount).toList(), key = { it }) { index ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            PdfThumb(
                                renderer = renderer,
                                pageIndex = index,
                                selected = index == currentPage,
                                invert = invert,
                                onClick = { onSelect(index) },
                            )
                            Text(
                                "${index + 1}",
                                color = if (index == currentPage) VaultAccent else VaultTextMuted,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfJumpDialog(
    pageCount: Int,
    currentPageOneBased: Int,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(currentPageOneBased.toString()) }
    var localError by remember { mutableStateOf<String?>(null) }

    fun submit() {
        val n = text.trim().toIntOrNull()
        if (n == null || n < 1 || n > pageCount) {
            localError = "Enter 1–$pageCount"
            return
        }
        onJump(n)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultSurface,
        title = { Text("Go to page") },
        text = {
            Column {
                Text("Page 1 – $pageCount", color = VaultTextMuted)
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it.filter { ch -> ch.isDigit() }.take(6)
                        localError = null
                    },
                    singleLine = true,
                    isError = localError != null,
                    supportingText = localError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { submit() }) {
                Text("Go", color = VaultAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun PdfPage(
    renderer: PdfRenderer,
    pageIndex: Int,
    scaleFactor: Float,
    resetToken: Int,
    invert: Boolean,
    fitMode: PdfFitMode,
    onZoomedChanged: (Boolean) -> Unit,
    onSingleTap: () -> Unit,
) {
    var bitmap by remember(pageIndex, scaleFactor) { mutableStateOf<Bitmap?>(null) }
    var scale by remember(pageIndex) { mutableFloatStateOf(1f) }
    var offset by remember(pageIndex) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(pageIndex, scaleFactor) {
        bitmap = withContext(Dispatchers.IO) {
            renderPdfPage(renderer, pageIndex, scaleFactor)
        }
        scale = 1f
        offset = Offset.Zero
        onZoomedChanged(false)
    }

    LaunchedEffect(resetToken, pageIndex) {
        scale = 1f
        offset = Offset.Zero
        onZoomedChanged(false)
    }

    LaunchedEffect(scale) {
        onZoomedChanged(scale > 1.02f)
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(if (fitMode == PdfFitMode.FIT_PAGE) 12.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        val containerW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val containerH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val bmp = bitmap

        if (bmp != null) {
            val fitted = remember(bmp.width, bmp.height, containerW, containerH, fitMode) {
                when (fitMode) {
                    PdfFitMode.FIT_PAGE ->
                        fitSize(bmp.width.toFloat(), bmp.height.toFloat(), containerW, containerH)
                    PdfFitMode.FIT_WIDTH -> {
                        val s = containerW / bmp.width.toFloat().coerceAtLeast(1f)
                        (bmp.width * s) to (bmp.height * s)
                    }
                }
            }

            fun clampOffset(raw: Offset, s: Float): Offset {
                if (s <= 1.02f) return Offset.Zero
                val scaledW = fitted.first * s
                val scaledH = fitted.second * s
                val maxX = max(0f, (scaledW - containerW) / 2f)
                val maxY = max(0f, (scaledH - containerH) / 2f)
                return Offset(
                    raw.x.coerceIn(-maxX, maxX),
                    raw.y.coerceIn(-maxY, maxY),
                )
            }

            val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                scale = newScale
                offset = clampOffset(offset + panChange, newScale)
            }

            val paperColor = if (invert) Color(0xFF121212) else Color.White

            Box(
                Modifier
                    .fillMaxSize()
                    .background(paperColor, RoundedCornerShape(if (fitMode == PdfFitMode.FIT_PAGE) 4.dp else 0.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "PDF page ${pageIndex + 1}",
                    contentScale = when (fitMode) {
                        PdfFitMode.FIT_PAGE -> ContentScale.Fit
                        PdfFitMode.FIT_WIDTH -> ContentScale.FillWidth
                    },
                    colorFilter = if (invert) ColorFilter.colorMatrix(PdfInvertColorMatrix) else null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y,
                        )
                        .transformable(
                            state = transformState,
                            canPan = { scale > 1.02f },
                            lockRotationOnZoomPan = true,
                        )
                        .pointerInput(pageIndex) {
                            detectTapGestures(
                                onTap = { onSingleTap() },
                                onDoubleTap = {
                                    if (scale > 1.05f) {
                                        scale = 1f
                                        offset = Offset.Zero
                                    } else {
                                        scale = 2.5f
                                        offset = Offset.Zero
                                    }
                                },
                            )
                        },
                )
            }
        } else {
            CircularProgressIndicator(color = VaultAccent)
        }
    }
}

private fun fitSize(srcW: Float, srcH: Float, maxW: Float, maxH: Float): Pair<Float, Float> {
    val scale = minOf(maxW / srcW, maxH / srcH)
    return (srcW * scale) to (srcH * scale)
}

/**
 * Convert 1-based user page number to 0-based pager index, clamped into range.
 * Invalid / empty counts as page 1 when [pageCount] > 0.
 */
internal fun clampPdfPageIndex(oneBasedPage: Int, pageCount: Int): Int {
    if (pageCount <= 0) return 0
    return (oneBasedPage - 1).coerceIn(0, pageCount - 1)
}

internal fun renderPdfPage(
    renderer: PdfRenderer,
    pageIndex: Int,
    scaleFactor: Float,
): Bitmap {
    synchronized(renderer) {
        renderer.openPage(pageIndex).use { page ->
            val w = (page.width * scaleFactor).toInt().coerceAtLeast(1)
            val h = (page.height * scaleFactor).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(AndroidColor.WHITE)
            Canvas(bmp).drawColor(AndroidColor.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bmp
        }
    }
}

private tailrec fun Context.findPdfActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findPdfActivity()
    else -> null
}
