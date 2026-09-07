package app.vault.workspace.ui.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vault.workspace.ui.theme.VaultAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Simple CSV/TSV table preview (first N rows/cols) with horizontal scroll; raw text fallback.
 */
@Composable
fun CsvViewer(
    loadBytes: suspend () -> ByteArray,
    itemId: String? = null,
    modifier: Modifier = Modifier,
    onSingleTap: () -> Unit = {},
    controlsVisible: Boolean = true,
    onControlsInteraction: () -> Unit = {},
) {
    val latestTap by rememberUpdatedState(onSingleTap)
    var fullText by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<CsvTable.Preview?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showRaw by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            fullText = null
            preview = null
        }
    }

    LaunchedEffect(itemId) {
        error = null
        fullText = null
        preview = null
        try {
            val loaded = withContext(Dispatchers.IO) {
                val bytes = loadBytes()
                try {
                    val decoded = TextEncoding.decode(bytes).text
                    decoded to CsvTable.parse(decoded)
                } finally {
                    bytes.fill(0)
                }
            }
            fullText = loaded.first
            preview = loaded.second
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
            showRaw || preview == null || (preview!!.headers.isEmpty() && preview!!.rows.isEmpty()) -> {
                val scroll = rememberScrollState()
                Text(
                    fullText!!,
                    color = Color(0xFFE8E6E0),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll)
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp)
                        .padding(bottom = 72.dp),
                )
            }
            else -> {
                val p = preview!!
                val hScroll = rememberScrollState()
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = 64.dp),
                ) {
                    val meta = buildString {
                        append("${p.totalRowsApprox} rows")
                        if (p.truncatedRows) append(" (showing ${CsvTable.MAX_ROWS})")
                        append(" · delim ")
                        append(when (p.delimiter) {
                            '\t' -> "TAB"
                            ';' -> ";"
                            else -> ","
                        })
                        if (p.truncatedCols) append(" · cols capped at ${CsvTable.MAX_COLS}")
                    }
                    Text(
                        meta,
                        color = Color(0xFF9A958C),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                    Box(Modifier.weight(1f).horizontalScroll(hScroll)) {
                        Column {
                            CsvRow(p.headers, header = true)
                            LazyColumn {
                                itemsIndexed(p.rows, key = { index, _ -> index }) { _, row ->
                                    CsvRow(row, header = false)
                                }
                            }
                        }
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
                    Text(if (showRaw) "Table" else "Raw text", color = VaultAccent)
                }
            }
        }
    }
}

@Composable
private fun CsvRow(cells: List<String>, header: Boolean) {
    Row(
        Modifier
            .border(0.5.dp, Color(0xFF2A2E36))
            .background(if (header) Color(0xFF1A1D24) else Color.Transparent),
    ) {
        cells.forEach { cell ->
            Text(
                cell,
                color = if (header) VaultAccent else Color(0xFFE8E6E0),
                fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(min = 88.dp, max = 180.dp)
                    .width(140.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}
