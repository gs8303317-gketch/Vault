package app.vault.workspace.ui.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.vault.workspace.ui.theme.VaultAccent
import app.vault.workspace.ui.theme.VaultText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_CHARS = 512_000

@Composable
fun TextFileViewer(
    loadBytes: suspend () -> ByteArray,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val bytes = withContext(Dispatchers.IO) { loadBytes() }
            var s = bytes.toString(Charsets.UTF_8)
            if (s.length > MAX_CHARS) {
                s = s.take(MAX_CHARS) + "\n\n… truncated for Phase 0 preview …"
            }
            text = s
        } catch (e: Exception) {
            error = e.message ?: "Cannot read text"
        }
    }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            error != null -> Text(error!!)
            text == null -> CircularProgressIndicator(color = VaultAccent)
            else -> Text(
                text!!,
                color = VaultText,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            )
        }
    }
}
