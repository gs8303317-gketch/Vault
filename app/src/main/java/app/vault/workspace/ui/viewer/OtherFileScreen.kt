package app.vault.workspace.ui.viewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vault.workspace.data.VaultItem
import app.vault.workspace.data.formatHumanSize
import app.vault.workspace.ui.theme.VaultAccent
import app.vault.workspace.ui.theme.VaultOnAccent
import app.vault.workspace.ui.theme.VaultSurface
import app.vault.workspace.ui.theme.VaultText
import app.vault.workspace.ui.theme.VaultTextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Premium shell for documents / files without a dedicated in-app viewer.
 * Vault-safe actions: Export (existing SAF path), copy filename, open-as-text when
 * decrypted bytes look textual.
 */
@Composable
fun OtherFileScreen(
    item: VaultItem,
    onExport: () -> Unit,
    loadBytes: (suspend () -> ByteArray)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val category = remember(item.mimeType, item.displayName) {
        DocumentMime.categoryLabel(item.mimeType, item.displayName)
    }
    var textProbe by remember { mutableStateOf<TextProbeState>(TextProbeState.Idle) }
    var openAsText by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }

    DisposableEffect(item.id) {
        onDispose {
            textProbe = TextProbeState.Idle
            openAsText = false
        }
    }

    LaunchedEffect(item.id, loadBytes != null) {
        if (loadBytes == null) {
            textProbe = TextProbeState.Unavailable
            return@LaunchedEffect
        }
        textProbe = TextProbeState.Checking
        try {
            val bytes = withContext(Dispatchers.IO) { loadBytes() }
            try {
                val sample = if (bytes.size > 4096) bytes.copyOf(4096) else bytes
                textProbe = if (DocumentMime.looksLikeText(sample)) {
                    TextProbeState.LooksLikeText
                } else {
                    TextProbeState.Binary
                }
            } finally {
                bytes.fill(0)
            }
        } catch (_: Exception) {
            textProbe = TextProbeState.Binary
        }
    }

    if (openAsText && loadBytes != null) {
        TextFileViewer(
            loadBytes = loadBytes,
            itemId = item.id,
            modifier = modifier,
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(VaultSurface)
                .border(1.dp, VaultAccent.copy(alpha = 0.35f), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                tint = VaultAccent,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.height(20.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(VaultSurface)
                .padding(20.dp),
        ) {
            Text(
                item.displayName,
                style = MaterialTheme.typography.titleLarge,
                color = VaultText,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            MetaRow("Category", category)
            MetaRow("MIME", item.mimeType.ifBlank { "unknown" })
            MetaRow("Size", formatHumanSize(item.sizeBytes))
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "No full in-app editor for this format. Export writes an unencrypted copy via the system file picker (same vault-safe path as other viewers).",
            color = VaultTextMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onExport,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = VaultAccent,
                contentColor = VaultOnAccent,
            ),
        ) {
            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Export…")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("filename", item.displayName))
                copied = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(if (copied) "Filename copied" else "Copy filename")
        }

        when (textProbe) {
            TextProbeState.Checking -> {
                Spacer(Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp),
                        color = VaultAccent,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.size(10.dp))
                    Text("Checking if contents look like text…", color = VaultTextMuted)
                }
            }
            TextProbeState.LooksLikeText -> {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { openAsText = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.TextSnippet, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Open as text")
                }
            }
            TextProbeState.Binary -> {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        tint = VaultTextMuted,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "Contents do not look like plain text.",
                        color = VaultTextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            else -> {}
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
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
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private sealed class TextProbeState {
    data object Idle : TextProbeState()
    data object Checking : TextProbeState()
    data object LooksLikeText : TextProbeState()
    data object Binary : TextProbeState()
    data object Unavailable : TextProbeState()
}
