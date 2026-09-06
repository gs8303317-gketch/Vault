package app.vault.workspace.ui.unlock

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
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
    biometricAvailable: Boolean = false,
    onBiometricUnlock: (() -> Unit)? = null,
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

    LaunchedEffect(biometricAvailable, lockedOutMs) {
        if (biometricAvailable && lockedOutMs <= 0L && onBiometricUnlock != null) {
            delay(300)
            onBiometricUnlock()
        }
    }

    val enabled = remaining <= 0L
    val landscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

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

    fun backspace() {
        if (enabled && pin.isNotEmpty()) pin = pin.dropLast(1)
    }

    @Composable
    fun Header(compact: Boolean) {
        if (!compact) {
            Spacer(Modifier.height(48.dp))
        }
        Icon(
            Icons.Default.Shield,
            contentDescription = null,
            tint = VaultAccent,
            modifier = Modifier.height(if (compact) 40.dp else 56.dp),
        )
        Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
        Text("Vault", style = if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(4.dp))
        Text("Enter your PIN", style = MaterialTheme.typography.bodyMedium, color = VaultTextMuted)
        Spacer(Modifier.height(if (compact) 12.dp else 24.dp))
        PinDots(filled = pin.length)
        Spacer(Modifier.height(8.dp))
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
        if (biometricAvailable && onBiometricUnlock != null && enabled) {
            Spacer(Modifier.height(8.dp))
            IconButton(
                onClick = onBiometricUnlock,
                modifier = Modifier.size(if (compact) 48.dp else 56.dp),
            ) {
                Icon(
                    Icons.Default.Fingerprint,
                    contentDescription = "Unlock with biometrics",
                    tint = VaultAccent,
                    modifier = Modifier.size(if (compact) 32.dp else 40.dp),
                )
            }
            if (!compact) {
                TextButton(onClick = onBiometricUnlock) {
                    Text("Unlock with biometrics", color = VaultAccent)
                }
            }
        }
    }

    if (landscape) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(end = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Header(compact = true)
            }
            PinPad(
                enabled = enabled,
                onDigit = ::digit,
                onBackspace = ::backspace,
                compact = true,
                modifier = Modifier
                    .weight(1.1f)
                    .width(320.dp),
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Header(compact = false)
            Spacer(Modifier.weight(1f))
            PinPad(
                enabled = enabled,
                onDigit = ::digit,
                onBackspace = ::backspace,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = (ms + 999) / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}
