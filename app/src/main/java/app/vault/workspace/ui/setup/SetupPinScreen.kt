package app.vault.workspace.ui.setup

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import app.vault.workspace.ui.theme.VaultDanger
import app.vault.workspace.ui.theme.VaultTextMuted

@Composable
fun SetupPinScreen(
    onPinConfirmed: (String) -> Unit,
    errorMessage: String? = null,
) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    val current = if (confirming) second else first
    val title = if (!confirming) "Choose a 4-digit PIN" else "Confirm your PIN"
    val subtitle = if (!confirming) {
        "PIN cannot be recovered. Avoid weak codes."
    } else {
        "Enter the same PIN again"
    }
    val landscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    fun handleDigit(c: Char) {
        localError = null
        if (confirming) {
            if (second.length >= PinRules.PIN_LENGTH) return
            val next = second + c
            second = next
            if (next.length == PinRules.PIN_LENGTH) {
                if (next != first) {
                    localError = "PINs do not match"
                    second = ""
                } else {
                    onPinConfirmed(next)
                }
            }
        } else {
            if (first.length >= PinRules.PIN_LENGTH) return
            val next = first + c
            first = next
            if (next.length == PinRules.PIN_LENGTH) {
                val err = PinRules.validateNewPin(next)
                if (err != null) {
                    localError = err
                    first = ""
                } else {
                    confirming = true
                }
            }
        }
    }

    fun handleBack() {
        localError = null
        if (confirming) {
            if (second.isEmpty()) {
                confirming = false
                first = ""
            } else {
                second = second.dropLast(1)
            }
        } else if (first.isNotEmpty()) {
            first = first.dropLast(1)
        }
    }

    @Composable
    fun Header() {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = VaultTextMuted)
        Spacer(Modifier.height(if (landscape) 12.dp else 32.dp))
        PinDots(filled = current.length)
        Spacer(Modifier.height(12.dp))
        val err = localError ?: errorMessage
        if (err != null) {
            Text(err, color = VaultDanger, style = MaterialTheme.typography.bodyMedium)
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
                Header()
            }
            PinPad(
                enabled = true,
                onDigit = ::handleDigit,
                onBackspace = ::handleBack,
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
            Spacer(Modifier.height(48.dp))
            Header()
            Spacer(Modifier.weight(1f))
            PinPad(enabled = true, onDigit = ::handleDigit, onBackspace = ::handleBack)
            Spacer(Modifier.height(24.dp))
        }
    }
}
