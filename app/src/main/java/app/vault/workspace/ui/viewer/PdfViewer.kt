@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.vault.workspace.ui.viewer

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.vault.workspace.ui.theme.VaultAccent
import app.vault.workspace.ui.theme.VaultTextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            error != null -> Text(error!!)
            renderer == null -> CircularProgressIndicator(color = VaultAccent)
            else -> {
                val pagerState = rememberPagerState(pageCount = { pageCount })
                Column(Modifier.fillMaxSize()) {
                    HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                        PdfPage(renderer = renderer!!, pageIndex = page)
                    }
                    Text(
                        "Page ${pagerState.currentPage + 1} / $pageCount",
                        color = VaultTextMuted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfPage(renderer: PdfRenderer, pageIndex: Int) {
    var bitmap by remember(pageIndex) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(pageIndex) {
        withContext(Dispatchers.IO) {
            synchronized(renderer) {
                renderer.openPage(pageIndex).use { page ->
                    val bmp = Bitmap.createBitmap(
                        page.width * 2,
                        page.height * 2,
                        Bitmap.Config.ARGB_8888,
                    )
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap = bmp
                }
            }
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "PDF page ${pageIndex + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            CircularProgressIndicator(color = VaultAccent)
        }
    }
}
