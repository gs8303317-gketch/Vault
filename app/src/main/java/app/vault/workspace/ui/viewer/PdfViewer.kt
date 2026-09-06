@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.vault.workspace.ui.viewer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.vault.workspace.ui.theme.VaultAccent
import app.vault.workspace.ui.theme.VaultTextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/**
 * PDF pages must be rendered onto an opaque white bitmap.
 * Zoom pan is clamped to content bounds.
 * [Modifier.transformable] uses canPan so horizontal pans at scale≈1 pass through
 * to [HorizontalPager] for page swipes; pan only when zoomed.
 */
@Composable
fun PdfViewer(
    openTempPdf: suspend () -> File,
    onCloseCleanup: (File?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pdfFile by remember { mutableStateOf<File?>(null) }
    var pageCount by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pfd by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    val density = LocalDensity.current.density
    var pageZoomed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            val file = withContext(Dispatchers.IO) { openTempPdf() }
            pdfFile = file
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pfd = descriptor
            val r = PdfRenderer(descriptor)
            renderer = r
            pageCount = r.pageCount
        } catch (e: Exception) {
            error = e.message ?: "Cannot open PDF"
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            renderer?.close()
            pfd?.close()
            onCloseCleanup(pdfFile)
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            error != null -> Text(error!!, color = Color.White)
            renderer == null -> CircularProgressIndicator(color = VaultAccent)
            else -> {
                val pagerState = rememberPagerState(pageCount = { pageCount })
                LaunchedEffect(pagerState.currentPage) {
                    pageZoomed = false
                }
                Column(Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.weight(1f),
                        userScrollEnabled = !pageZoomed,
                        beyondViewportPageCount = 0,
                    ) { page ->
                        PdfPage(
                            renderer = renderer!!,
                            pageIndex = page,
                            scaleFactor = (density * 2.5f).coerceIn(2f, 3.5f),
                            onZoomedChanged = { zoomed ->
                                if (page == pagerState.currentPage) {
                                    pageZoomed = zoomed
                                }
                            },
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        IconButton(
                            onClick = {
                                val prev = pagerState.currentPage - 1
                                if (prev >= 0) {
                                    scope.launch { pagerState.animateScrollToPage(prev) }
                                }
                            },
                            enabled = pagerState.currentPage > 0,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Previous page",
                                tint = if (pagerState.currentPage > 0) VaultAccent else VaultTextMuted,
                            )
                        }
                        Text(
                            "Page ${pagerState.currentPage + 1} / $pageCount",
                            color = VaultTextMuted,
                        )
                        IconButton(
                            onClick = {
                                val next = pagerState.currentPage + 1
                                if (next < pageCount) {
                                    scope.launch { pagerState.animateScrollToPage(next) }
                                }
                            },
                            enabled = pagerState.currentPage < pageCount - 1,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Next page",
                                tint = if (pagerState.currentPage < pageCount - 1) {
                                    VaultAccent
                                } else {
                                    VaultTextMuted
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPage(
    renderer: PdfRenderer,
    pageIndex: Int,
    scaleFactor: Float,
    onZoomedChanged: (Boolean) -> Unit,
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

    LaunchedEffect(scale) {
        onZoomedChanged(scale > 1.02f)
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val containerW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val containerH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val bmp = bitmap

        if (bmp != null) {
            val fitted = remember(bmp.width, bmp.height, containerW, containerH) {
                fitSize(bmp.width.toFloat(), bmp.height.toFloat(), containerW, containerH)
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

            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.White, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "PDF page ${pageIndex + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y,
                        )
                        // canPan=false at scale≈1 lets HorizontalPager receive swipe pans
                        .transformable(
                            state = transformState,
                            canPan = { scale > 1.02f },
                            lockRotationOnZoomPan = true,
                        )
                        .pointerInput(pageIndex) {
                            detectTapGestures(
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
