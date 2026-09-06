package app.vault.workspace.ui.unlock

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.vault.workspace.auth.PinRules
import app.vault.workspace.ui.components.PinDots
import app.vault.workspace.ui.components.PinPad
import app.vault.workspace.ui.theme.VaultAccent
import app.vault.workspace.ui.theme.VaultDanger
import app.vault.workspace.ui.theme.VaultTextMuted
import kotlinx.coroutines.delay

@Composable
fun UnlockScreen(
    lockedOutMs: Long,
    errorMessage: String?,
    onSubmitPin: (String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var remaining by remember { mutableStateOf(lockedOutMs) }

    LaunchedEffect(lockedOutMs) {
        remaining = lockedOutMs
        while (remaining > 0) {
            delay(250)
            remaining = (remaining - 250).coerceAtLeast(0)
        }
    }

    val enabled = remaining <= 0L

    fun digit(c: Char) {
        if (!enabled) return
        if (pin.length >= PinRules.PIN_LENGTH) return
        val next = pin + c
        pin = next
        if (next.length == PinRules.PIN_LENGTH) {
            onSubmitPin(next)
            pin = ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(64.dp))
        Icon(Icons.Default.Shield, contentDescription = null, tint = VaultAccent, modifier = Modifier.height(56.dp))
        Spacer(Modifier.height(16.dp))
        Text("Vault", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text("Enter your PIN", style = MaterialTheme.typography.bodyMedium, color = VaultTextMuted)
        Spacer(Modifier.height(32.dp))
        PinDots(filled = pin.length)
        Spacer(Modifier.height(16.dp))
        when {
            remaining > 0 -> Text(
                "Try again in ${formatDuration(remaining)}",
                color = VaultDanger,
                style = MaterialTheme.typography.bodyMedium,
            )
            errorMessage != null -> Text(
                errorMessage,
                color = VaultDanger,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.weight(1f))
        PinPad(
            enabled = enabled,
            onDigit = ::digit,
            onBackspace = { if (enabled && pin.isNotEmpty()) pin = pin.dropLast(1) },
        )
        Spacer(Modifier.height(24.dp))
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = (ms + 999) / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}
