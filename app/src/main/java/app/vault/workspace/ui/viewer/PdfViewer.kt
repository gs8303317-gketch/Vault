@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.vault.workspace.ui.viewer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PDF pages must be rendered onto an opaque white bitmap.
 * A fresh ARGB bitmap is transparent black; PdfRenderer blends into it and
 * pages look dark/blue and unreadable on a dark app theme.
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
                Column(Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.weight(1f),
                        userScrollEnabled = true,
                    ) { page ->
                        PdfPage(
                            renderer = renderer!!,
                            pageIndex = page,
                            scaleFactor = (density * 2.5f).coerceIn(2f, 3.5f),
                        )
                    }
                    Text(
                        "Page ${pagerState.currentPage + 1} / $pageCount",
                        color = VaultTextMuted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
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
) {
    var bitmap by remember(pageIndex, scaleFactor) { mutableStateOf<Bitmap?>(null) }
    var scale by remember(pageIndex) { mutableFloatStateOf(1f) }
    var offset by remember(pageIndex) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(pageIndex, scaleFactor) {
        val bmp = withContext(Dispatchers.IO) {
            renderPdfPage(renderer, pageIndex, scaleFactor)
        }
        bitmap = bmp
    }

    DisposableEffect(pageIndex) {
        onDispose {
            // Don't recycle while pager may still hold reference briefly; GC is fine for page bitmaps
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(4.dp))
                    .padding(2.dp),
            ) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "PDF page ${pageIndex + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y,
                        )
                        .pointerInput(pageIndex) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                offset = if (scale <= 1.01f) Offset.Zero else offset + pan
                            }
                        },
                )
            }
        } else {
            CircularProgressIndicator(color = VaultAccent)
        }
    }
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
            // Opaque white canvas — required for correct PdfRenderer colors
            bmp.eraseColor(AndroidColor.WHITE)
            Canvas(bmp).drawColor(AndroidColor.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bmp
        }
    }
}
