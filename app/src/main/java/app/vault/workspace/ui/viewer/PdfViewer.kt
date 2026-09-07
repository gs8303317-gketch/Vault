@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.vault.workspace.ui.viewer

import app.vault.workspace.ui.nav.VaultMotion
import app.vault.workspace.ui.nav.VaultDialogEnter
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
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import app.vault.workspace.ui.nav.vaultSharedThumb
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.ui.input.pointer.positionChanged
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
import kotlin.math.abs
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
@OptIn(ExperimentalSharedTransitionApi::class)
fun PdfViewer(
    openPdfHandle: suspend () -> EncryptedPdfHandle,
    itemId: String,
    title: String? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    hasThumb: Boolean = false,
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
            data class Opened(val handle: EncryptedPdfHandle, val renderer: PdfRenderer, val pages: Int, val resume: Int)
            val opened = withContext(Dispatchers.IO) {
                val h = openPdfHandle()
                try {
                    val r = PdfRenderer(h.pfd)
                    val pages = r.pageCount
                    val resume = PdfPageStore.resumePageIndex(pageStore.getPageIndex(itemId), pages)
                    Opened(h, r, pages, resume)
                } catch (e: Exception) {
                    h.releaseResources()
                    throw e
                }
            }
            handle = opened.handle
            renderer = opened.renderer
            pageCount = opened.pages
            initialPage = opened.resume
            ready = true
        } catch (e: Exception) {
            error = e.message ?: "Cannot open PDF"
            handle?.releaseResources()
            handle = null
            renderer = null
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

    val sharedMod = if (hasThumb) {
        Modifier.vaultSharedThumb(
            sharedTransitionScope,
            animatedVisibilityScope,
            itemId,
            useBounds = true,
        )
    } else {
        Modifier
    }
    Box(
        modifier
            .fillMaxSize()
            .then(sharedMod)
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
                        beyondViewportPageCount = 1,
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
        val rendered = withContext(Dispatchers.IO) {
            renderPdfPage(renderer, pageIndex, scaleFactor = 0.35f)
        }
        val prev = bitmap
        bitmap = rendered
        if (prev != null && prev !== rendered && !prev.isRecycled) prev.recycle()
    }
    DisposableEffect(pageIndex) {
        onDispose {
            val b = bitmap
            bitmap = null
            if (b != null && !b.isRecycled) b.recycle()
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
            val imageBitmap = remember(bmp) { bmp.asImageBitmap() }
            Image(
                bitmap = imageBitmap,
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
        properties = VaultMotion.fullWidthDialogProperties,
    ) {
        VaultDialogEnter {
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
        properties = VaultMotion.dialogProperties,
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
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val latestOnSingleTap by rememberUpdatedState(onSingleTap)
    val latestOnZoomedChanged by rememberUpdatedState(onZoomedChanged)
    val density = LocalDensity.current

    LaunchedEffect(pageIndex, scaleFactor) {
        val rendered = withContext(Dispatchers.IO) {
            renderPdfPage(renderer, pageIndex, scaleFactor)
        }
        val prev = bitmap
        bitmap = rendered
        if (prev != null && prev !== rendered && !prev.isRecycled) {
            prev.recycle()
        }
        scale = 1f
        offset = Offset.Zero
        latestOnZoomedChanged(false)
    }

    DisposableEffect(Unit) {
        onDispose {
            val b = bitmap
            bitmap = null
            if (b != null && !b.isRecycled) b.recycle()
        }
    }

    LaunchedEffect(resetToken, pageIndex) {
        scale = 1f
        offset = Offset.Zero
        latestOnZoomedChanged(false)
    }

    LaunchedEffect(scale) {
        latestOnZoomedChanged(scale > 1.02f)
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
            val imageBitmap = remember(bmp) { bmp.asImageBitmap() }
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
            val dispW = fitted.first
            val dispH = fitted.second

            fun clampOffset(raw: Offset, s: Float): Offset {
                if (s <= 1.02f) return Offset.Zero
                val scaledW = dispW * s
                val scaledH = dispH * s
                val maxX = max(0f, (scaledW - containerW) / 2f)
                val maxY = max(0f, (scaledH - containerH) / 2f)
                return Offset(
                    raw.x.coerceIn(-maxX, maxX),
                    raw.y.coerceIn(-maxY, maxY),
                )
            }

            val paperColor = if (invert) Color(0xFF121212) else Color.White

            Box(
                Modifier
                    .fillMaxSize()
                    .background(paperColor, RoundedCornerShape(if (fitMode == PdfFitMode.FIT_PAGE) 4.dp else 0.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "PDF page ${pageIndex + 1}",
                    contentScale = ContentScale.FillBounds,
                    colorFilter = if (invert) ColorFilter.colorMatrix(PdfInvertColorMatrix) else null,
                    modifier = Modifier
                        .size(
                            width = with(density) { dispW.toDp() },
                            height = with(density) { dispH.toDp() },
                        )
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y,
                        )
                        .pointerInput(pageIndex, dispW, dispH, containerW, containerH) {
                            val touchSlop = viewConfiguration.touchSlop
                            val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
                            var lastTapTime = 0L
                            var lastTapPos = Offset.Zero
                            val center = Offset(size.width / 2f, size.height / 2f)

                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var zoomAcc = 1f
                                var panAcc = Offset.Zero
                                var pastTouchSlop = false
                                var lockedToTransform = false
                                val startScale = scale

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    val pressedCount = event.changes.count { it.pressed }

                                    if (!pastTouchSlop) {
                                        zoomAcc *= zoomChange
                                        panAcc += panChange
                                        val centroidSize =
                                            event.calculateCentroidSize(useCurrent = false)
                                        val zoomMotion = abs(1f - zoomAcc) * centroidSize
                                        val panMotion = panAcc.getDistance()
                                        if (zoomMotion > touchSlop ||
                                            panMotion > touchSlop ||
                                            pressedCount > 1
                                        ) {
                                            pastTouchSlop = true
                                            val multi = pressedCount > 1
                                            val mostlyZoom = zoomMotion > panMotion
                                            val canPanContent = startScale > 1.02f
                                            // Steal gesture from VerticalPager only for pinch or zoomed pan.
                                            if (multi || mostlyZoom || canPanContent) {
                                                lockedToTransform = true
                                            } else {
                                                // Vertical page scroll — do not consume; let pager win.
                                                break
                                            }
                                        }
                                    }

                                    if (lockedToTransform) {
                                        val z = event.calculateZoom()
                                        val p = event.calculatePan()
                                        val centroid = event.calculateCentroid(useCurrent = true)
                                        val oldScale = scale
                                        val newScale = (oldScale * z).coerceIn(1f, 5f)
                                        if (newScale <= 1.01f) {
                                            scale = 1f
                                            offset = Offset.Zero
                                        } else {
                                            val tapRel = centroid - center
                                            val zoomed = if (abs(z - 1f) > 0.001f) {
                                                offset * (newScale / oldScale) +
                                                    tapRel * (1f - newScale / oldScale)
                                            } else {
                                                offset
                                            }
                                            scale = newScale
                                            offset = clampOffset(zoomed + p, newScale)
                                        }
                                        event.changes.forEach {
                                            if (it.positionChanged()) it.consume()
                                        }
                                    }

                                    if (event.changes.none { it.pressed }) {
                                        if (!lockedToTransform && !pastTouchSlop) {
                                            val now = System.currentTimeMillis()
                                            val tapPos = down.position
                                            if (now - lastTapTime <= doubleTapTimeout &&
                                                (tapPos - lastTapPos).getDistance() < touchSlop * 4
                                            ) {
                                                if (scale > 1.05f) {
                                                    scale = 1f
                                                    offset = Offset.Zero
                                                } else {
                                                    val target = 2.5f
                                                    val tapRel = tapPos - center
                                                    val newOff =
                                                        offset * (target / scale) +
                                                            tapRel * (1f - target / scale)
                                                    scale = target
                                                    offset = clampOffset(newOff, target)
                                                }
                                                lastTapTime = 0L
                                            } else {
                                                lastTapTime = now
                                                lastTapPos = tapPos
                                                latestOnSingleTap()
                                            }
                                        }
                                        break
                                    }
                                }
                            }
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
