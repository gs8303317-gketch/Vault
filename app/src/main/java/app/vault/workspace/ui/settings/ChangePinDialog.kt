package app.vault.workspace.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import app.vault.workspace.ui.theme.VaultSurface
import app.vault.workspace.ui.theme.VaultTextMuted

private enum class ChangePinStep {
    Current,
    New,
    Confirm,
}

/**
 * Multi-step PIN change: verify current → choose new → confirm.
 * Calls [onSubmit] with (current, new) when confirm matches.
 */
@Composable
fun ChangePinDialog(
    errorMessage: String?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (currentPin: String, newPin: String) -> Unit,
) {
    var step by remember { mutableStateOf(ChangePinStep.Current) }
    var current by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    val display = when (step) {
        ChangePinStep.Current -> current
        ChangePinStep.New -> newPin
        ChangePinStep.Confirm -> confirm
    }
    val title = when (step) {
        ChangePinStep.Current -> "Enter current PIN"
        ChangePinStep.New -> "Choose a new PIN"
        ChangePinStep.Confirm -> "Confirm new PIN"
    }
    val subtitle = when (step) {
        ChangePinStep.Current -> "Verify it’s you before changing the PIN"
        ChangePinStep.New -> "4 digits. Avoid weak codes. Biometric unlock will turn off."
        ChangePinStep.Confirm -> "Enter the new PIN again"
    }

    fun handleDigit(c: Char) {
        if (busy) return
        localError = null
        when (step) {
            ChangePinStep.Current -> {
                if (current.length >= PinRules.PIN_LENGTH) return
                val next = current + c
                current = next
                if (next.length == PinRules.PIN_LENGTH) {
                    step = ChangePinStep.New
                }
            }
            ChangePinStep.New -> {
                if (newPin.length >= PinRules.PIN_LENGTH) return
                val next = newPin + c
                newPin = next
                if (next.length == PinRules.PIN_LENGTH) {
                    val err = PinRules.validateNewPin(next)
                    when {
                        err != null -> {
                            localError = err
                            newPin = ""
                        }
                        next == current -> {
                            localError = "New PIN must be different"
                            newPin = ""
                        }
                        else -> step = ChangePinStep.Confirm
                    }
                }
            }
            ChangePinStep.Confirm -> {
                if (confirm.length >= PinRules.PIN_LENGTH) return
                val next = confirm + c
                confirm = next
                if (next.length == PinRules.PIN_LENGTH) {
                    if (next != newPin) {
                        localError = "PINs do not match"
                        confirm = ""
                    } else {
                        onSubmit(current, newPin)
                    }
                }
            }
        }
    }

    fun handleBack() {
        if (busy) return
        localError = null
        when (step) {
            ChangePinStep.Current -> {
                if (current.isNotEmpty()) current = current.dropLast(1)
            }
            ChangePinStep.New -> {
                if (newPin.isEmpty()) {
                    step = ChangePinStep.Current
                    current = ""
                } else {
                    newPin = newPin.dropLast(1)
                }
            }
            ChangePinStep.Confirm -> {
                if (confirm.isEmpty()) {
                    step = ChangePinStep.New
                    newPin = ""
                } else {
                    confirm = confirm.dropLast(1)
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = VaultSurface,
        title = { Text(title) },
        text = {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = VaultTextMuted)
                Spacer(Modifier.height(20.dp))
                PinDots(filled = display.length)
                Spacer(Modifier.height(12.dp))
                val err = localError ?: errorMessage
                if (err != null) {
                    Text(err, color = VaultDanger, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                }
                PinPad(
                    enabled = !busy,
                    onDigit = { handleDigit(it) },
                    onBackspace = { handleBack() },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("Cancel", color = VaultAccent)
            }
        },
    )
}
