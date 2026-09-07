package app.vault.workspace.ui.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vault.workspace.ui.theme.VaultAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Read-only OOXML text preview (docx / pptx / xlsx) — not a full Office editor.
 */
@Composable
fun OfficeTextViewer(
    loadBytes: suspend () -> ByteArray,
    mimeType: String,
    displayName: String,
    itemId: String? = null,
    modifier: Modifier = Modifier,
    onSingleTap: () -> Unit = {},
    controlsVisible: Boolean = true,
    onControlsInteraction: () -> Unit = {},
) {
    val latestTap by rememberUpdatedState(onSingleTap)
    var result by remember { mutableStateOf<OfficeTextExtract.Result?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose { result = null }
    }

    LaunchedEffect(itemId, mimeType, displayName) {
        error = null
        result = null
        try {
            val bytes = withContext(Dispatchers.IO) { loadBytes() }
            try {
                result = withContext(Dispatchers.Default) {
                    OfficeTextExtract.extract(bytes, mimeType, displayName)
                }
            } finally {
                bytes.fill(0)
            }
        } catch (e: Exception) {
            error = e.message ?: "Failed to open"
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Color(0xFF0E1014))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { latestTap() })
            },
    ) {
        when {
            error != null -> Text(
                error!!,
                color = Color(0xFFE85D4C),
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
            result == null -> CircularProgressIndicator(
                Modifier.align(Alignment.Center),
                color = VaultAccent,
            )
            result!!.text.isBlank() -> Text(
                "No extractable text found. Use Export for the original file.",
                color = Color(0xFF9A958C),
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
            else -> {
                val scroll = rememberScrollState()
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll)
                        .padding(16.dp)
                        .padding(bottom = 72.dp),
                ) {
                    Text(
                        result!!.text,
                        color = Color(0xFFE8E6E0),
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible && result != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.72f))
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val label = result?.formatLabel ?: "Office"
                val trunc = if (result?.truncated == true) " · truncated" else ""
                Text(
                    "$label · read-only text preview$trunc",
                    color = Color(0xFF9A958C),
                    fontSize = 12.sp,
                )
                Spacer(Modifier.weight(1f))
            }
        }
    }
}
