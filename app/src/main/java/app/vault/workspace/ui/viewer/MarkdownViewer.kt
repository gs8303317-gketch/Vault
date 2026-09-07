package app.vault.workspace.ui.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vault.workspace.ui.theme.VaultAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lightweight Markdown preview + raw/source toggle. Reuses decrypt-in-memory path.
 */
@Composable
fun MarkdownViewer(
    loadBytes: suspend () -> ByteArray,
    itemId: String? = null,
    modifier: Modifier = Modifier,
    onSingleTap: () -> Unit = {},
    controlsVisible: Boolean = true,
    onControlsInteraction: () -> Unit = {},
) {
    val latestTap by rememberUpdatedState(onSingleTap)
    var fullText by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showRaw by remember { mutableStateOf(false) }
    val blocks = remember(fullText) {
        fullText?.let { MarkdownRender.parse(it) }.orEmpty()
    }

    DisposableEffect(Unit) {
        onDispose { fullText = null }
    }

    LaunchedEffect(itemId) {
        error = null
        fullText = null
        try {
            fullText = withContext(Dispatchers.IO) {
                val bytes = loadBytes()
                try {
                    TextEncoding.decode(bytes).text
                } finally {
                    bytes.fill(0)
                }
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
            fullText == null -> CircularProgressIndicator(
                Modifier.align(Alignment.Center),
                color = VaultAccent,
            )
            showRaw -> {
                val scroll = rememberScrollState()
                Text(
                    fullText!!,
                    color = Color(0xFFE8E6E0),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll)
                        .horizontalScroll(rememberScrollState())
                        .padding(16.dp)
                        .padding(bottom = 72.dp),
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 88.dp),
                ) {
                    items(blocks) { block ->
                        MarkdownBlockView(block)
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible && fullText != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.72f))
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        onControlsInteraction()
                        showRaw = !showRaw
                    },
                ) {
                    Text(
                        if (showRaw) "Preview" else "Raw source",
                        color = VaultAccent,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "Markdown",
                    color = Color(0xFF9A958C),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun MarkdownBlockView(block: MarkdownRender.Block) {
    val body = Color(0xFFE8E6E0)
    val muted = Color(0xFF9A958C)
    when (block) {
        is MarkdownRender.Block.Heading -> {
            val sp = when (block.level) {
                1 -> 26.sp
                2 -> 22.sp
                3 -> 18.sp
                else -> 16.sp
            }
            Text(
                MarkdownRender.stripInline(block.text),
                color = body,
                fontSize = sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        is MarkdownRender.Block.Paragraph -> Text(
            MarkdownRender.stripInline(block.text),
            color = body,
            fontSize = 15.sp,
            lineHeight = 22.sp,
        )
        is MarkdownRender.Block.Bullet -> Row {
            Text("•", color = VaultAccent, modifier = Modifier.width(18.dp))
            Text(MarkdownRender.stripInline(block.text), color = body, fontSize = 15.sp)
        }
        is MarkdownRender.Block.Ordered -> Row {
            Text("${block.index}.", color = VaultAccent, modifier = Modifier.width(28.dp))
            Text(MarkdownRender.stripInline(block.text), color = body, fontSize = 15.sp)
        }
        is MarkdownRender.Block.Code -> {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A1D24))
                    .padding(12.dp),
            ) {
                if (block.language.isNotBlank()) {
                    Text(block.language, color = muted, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    block.code,
                    color = body,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                )
            }
        }
        MarkdownRender.Block.Rule -> HorizontalDivider(color = muted.copy(alpha = 0.4f))
        is MarkdownRender.Block.Quote -> Text(
            MarkdownRender.stripInline(block.text),
            color = muted,
            fontSize = 15.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF16181E))
                .padding(12.dp),
        )
    }
}
