package app.vault.workspace.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import app.vault.workspace.auth.LockRules
import app.vault.workspace.auth.LockType
import app.vault.workspace.ui.components.LockTypeChooser
import app.vault.workspace.ui.components.PasswordLockField
import app.vault.workspace.ui.components.PatternLock
import app.vault.workspace.ui.components.PinDots
import app.vault.workspace.ui.components.PinPad
import app.vault.workspace.ui.theme.VaultAccent
import app.vault.workspace.ui.theme.VaultDanger
import app.vault.workspace.ui.theme.VaultSurface
import app.vault.workspace.ui.theme.VaultTextMuted

private enum class ChangeLockStep {
    VerifyCurrent,
    ChooseType,
    EnterNew,
    ConfirmNew,
}

/**
 * Change lock: verify current credential → pick new type → set + confirm.
 * Calls [onSubmit] with (currentCredential, newType, newCredential).
 */
@Composable
fun ChangePinDialog(
    currentLockType: LockType,
    currentPinLength: Int,
    errorMessage: String?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (current: String, newType: LockType, newCredential: String) -> Unit,
) {
    var step by remember { mutableStateOf(ChangeLockStep.VerifyCurrent) }
    var current by remember { mutableStateOf("") }
    var newType by remember { mutableStateOf<LockType?>(null) }
    var newCred by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    val pinSlots = currentPinLength.coerceIn(LockRules.PIN_MIN, LockRules.PIN_MAX)

    val title = when (step) {
        ChangeLockStep.VerifyCurrent -> "Verify ${currentLockType.displayName.lowercase()}"
        ChangeLockStep.ChooseType -> "Choose new lock type"
        ChangeLockStep.EnterNew -> "Set new ${newType?.displayName ?: "lock"}"
        ChangeLockStep.ConfirmNew -> "Confirm new ${newType?.displayName ?: "lock"}"
    }
    val subtitle = when (step) {
        ChangeLockStep.VerifyCurrent -> "Confirm it’s you before changing the lock"
        ChangeLockStep.ChooseType -> "Switch PIN, password, or pattern. Biometric unlock will turn off."
        ChangeLockStep.EnterNew -> when (newType) {
            LockType.PIN -> "4–6 digits. Avoid weak codes."
            LockType.PASSWORD -> "6–10 characters."
            LockType.PATTERN -> "Connect at least ${LockRules.PATTERN_MIN_POINTS} dots"
            null -> ""
        }
        ChangeLockStep.ConfirmNew -> "Enter the same credential again"
    }

    fun goAfterVerify(secret: String) {
        current = secret
        localError = null
        step = ChangeLockStep.ChooseType
    }

    fun acceptNew(secret: String) {
        val type = newType ?: return
        val err = LockRules.validateNew(type, secret)
        if (err != null) {
            localError = err
            newCred = ""
            return
        }
        if (type == currentLockType && secret == current) {
            localError = "New credential must be different"
            newCred = ""
            return
        }
        newCred = secret
        confirm = ""
        localError = null
        step = ChangeLockStep.ConfirmNew
    }

    fun acceptConfirm(secret: String) {
        val type = newType ?: return
        if (secret != newCred) {
            localError = "${type.displayName}s do not match"
            confirm = ""
            return
        }
        onSubmit(current, type, newCred)
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = VaultSurface,
        title = { Text(title) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = VaultTextMuted)
                Spacer(Modifier.height(16.dp))
                val err = localError ?: errorMessage
                if (err != null) {
                    Text(err, color = VaultDanger, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                }

                when (step) {
                    ChangeLockStep.VerifyCurrent -> {
                        CredentialEntry(
                            type = currentLockType,
                            value = current,
                            onValueChange = { current = it; localError = null },
                            pinSlots = pinSlots,
                            setupMode = false,
                            confirmAgainstLength = null,
                            busy = busy,
                            onComplete = { goAfterVerify(it) },
                        )
                    }
                    ChangeLockStep.ChooseType -> {
                        LockTypeChooser(
                            selected = newType,
                            onSelect = { newType = it },
                        )
                        TextButton(
                            onClick = {
                                if (newType != null) {
                                    newCred = ""
                                    confirm = ""
                                    localError = null
                                    step = ChangeLockStep.EnterNew
                                }
                            },
                            enabled = newType != null && !busy,
                        ) {
                            Text("Continue", color = VaultAccent)
                        }
                    }
                    ChangeLockStep.EnterNew -> {
                        val type = newType ?: LockType.PIN
                        CredentialEntry(
                            type = type,
                            value = newCred,
                            onValueChange = { newCred = it; localError = null },
                            pinSlots = LockRules.PIN_MAX,
                            setupMode = true,
                            confirmAgainstLength = null,
                            busy = busy,
                            onComplete = { acceptNew(it) },
                        )
                    }
                    ChangeLockStep.ConfirmNew -> {
                        val type = newType ?: LockType.PIN
                        CredentialEntry(
                            type = type,
                            value = confirm,
                            onValueChange = { confirm = it; localError = null },
                            pinSlots = if (type == LockType.PIN) {
                                newCred.length.coerceIn(LockRules.PIN_MIN, LockRules.PIN_MAX)
                            } else {
                                LockRules.PIN_MAX
                            },
                            setupMode = true,
                            confirmAgainstLength = if (type == LockType.PIN) newCred.length else null,
                            busy = busy,
                            onComplete = { acceptConfirm(it) },
                        )
                    }
                }
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

@Composable
private fun CredentialEntry(
    type: LockType,
    value: String,
    onValueChange: (String) -> Unit,
    pinSlots: Int,
    setupMode: Boolean,
    confirmAgainstLength: Int?,
    busy: Boolean,
    onComplete: (String) -> Unit,
) {
    when (type) {
        LockType.PIN -> {
            PinDots(filled = value.length, slotCount = pinSlots)
            Spacer(Modifier.height(12.dp))
            PinPad(
                enabled = !busy,
                onDigit = { c ->
                    if (busy) return@PinPad
                    if (value.length >= pinSlots) return@PinPad
                    val next = value + c
                    onValueChange(next)
                    val target = confirmAgainstLength ?: pinSlots
                    if (setupMode && confirmAgainstLength != null) {
                        if (next.length == target) onComplete(next)
                    } else if (setupMode) {
                        if (next.length == LockRules.PIN_MAX) onComplete(next)
                    } else if (next.length == pinSlots) {
                        onComplete(next)
                    }
                },
                onBackspace = {
                    if (!busy && value.isNotEmpty()) onValueChange(value.dropLast(1))
                },
                showEnter = setupMode && confirmAgainstLength == null,
                enterEnabled = value.length in LockRules.PIN_MIN until LockRules.PIN_MAX,
                onEnter = {
                    if (value.length >= LockRules.PIN_MIN) onComplete(value)
                },
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        LockType.PASSWORD -> {
            PasswordLockField(
                value = value,
                onValueChange = onValueChange,
                onSubmit = { onComplete(value) },
                enabled = !busy,
                submitLabel = "Continue",
            )
        }
        LockType.PATTERN -> {
            PatternLock(
                enabled = !busy,
                onPatternComplete = onComplete,
                compact = true,
                maxSize = 240.dp,
            )
        }
    }
}
