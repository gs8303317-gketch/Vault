package app.vault.workspace.ui.setup

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import app.vault.workspace.auth.LockRules
import app.vault.workspace.auth.LockType
import app.vault.workspace.ui.components.LockTypeChooser
import app.vault.workspace.ui.components.PasswordLockField
import app.vault.workspace.ui.components.PatternLock
import app.vault.workspace.ui.components.PinDots
import app.vault.workspace.ui.components.PinPad
import app.vault.workspace.ui.theme.VaultAccent
import app.vault.workspace.ui.theme.VaultAmoled
import app.vault.workspace.ui.theme.VaultDanger
import app.vault.workspace.ui.theme.VaultText
import app.vault.workspace.ui.theme.VaultTextMuted

private enum class SetupStep {
    ChooseType,
    Enter,
    Confirm,
}

/**
 * First-run lock setup: choose PIN / Password / Pattern, enter twice, weak checks for PIN.
 */
@Composable
fun SetupPinScreen(
    onLockConfirmed: (type: LockType, credential: String) -> Unit,
    errorMessage: String? = null,
) {
    var step by remember { mutableStateOf(SetupStep.ChooseType) }
    var lockType by remember { mutableStateOf<LockType?>(null) }
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    val landscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    fun resetCredential() {
        first = ""
        second = ""
        localError = null
    }

    when (step) {
        SetupStep.ChooseType -> {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(VaultAmoled)
                    .padding(horizontal = 24.dp, vertical = 20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(32.dp))
                Text(
                    "Choose lock type",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = VaultText,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Lock type choose karo — PIN, Password, or Pattern",
                    style = MaterialTheme.typography.titleMedium,
                    color = VaultAccent,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Tap a big card below. You can change this later in Settings. " +
                        "Credential cannot be recovered.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VaultTextMuted,
                )
                Spacer(Modifier.height(32.dp))
                LockTypeChooser(
                    selected = lockType,
                    onSelect = { chosen ->
                        lockType = chosen
                        resetCredential()
                        step = SetupStep.Enter
                    },
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    "Unlock screen will match the type you pick.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VaultTextMuted,
                )
            }
        }
        SetupStep.Enter, SetupStep.Confirm -> {
            val type = lockType ?: LockType.PIN
            val confirming = step == SetupStep.Confirm
            val current = if (confirming) second else first
            val title = when {
                confirming && type == LockType.PIN -> "Confirm your PIN"
                confirming && type == LockType.PASSWORD -> "Confirm password"
                confirming && type == LockType.PATTERN -> "Confirm pattern"
                type == LockType.PIN -> "Choose a PIN"
                type == LockType.PASSWORD -> "Choose a password"
                else -> "Draw a pattern"
            }
            val subtitle = when {
                confirming -> "Enter the same ${type.displayName.lowercase()} again"
                type == LockType.PIN ->
                    "4–6 digits. Tap ✓ when done, or enter 6 digits. Avoid weak codes."
                type == LockType.PASSWORD -> "6–10 characters."
                else -> "Connect at least ${LockRules.PATTERN_MIN_POINTS} dots"
            }

            fun acceptFirst(secret: String) {
                val err = LockRules.validateNew(type, secret)
                if (err != null) {
                    localError = err
                    first = ""
                    return
                }
                first = secret
                second = ""
                localError = null
                step = SetupStep.Confirm
            }

            fun acceptConfirm(secret: String) {
                if (secret != first) {
                    localError = "${type.displayName}s do not match"
                    second = ""
                    return
                }
                onLockConfirmed(type, secret)
            }

            fun handlePinDigit(c: Char) {
                localError = null
                if (confirming) {
                    if (second.length >= LockRules.PIN_MAX) return
                    val next = second + c
                    second = next
                    if (next.length == first.length && first.length >= LockRules.PIN_MIN) {
                        acceptConfirm(next)
                    } else if (next.length == LockRules.PIN_MAX) {
                        acceptConfirm(next)
                    }
                } else {
                    if (first.length >= LockRules.PIN_MAX) return
                    val next = first + c
                    first = next
                    if (next.length == LockRules.PIN_MAX) {
                        acceptFirst(next)
                    }
                }
            }

            fun handlePinEnter() {
                localError = null
                if (confirming) {
                    if (second.length >= LockRules.PIN_MIN) acceptConfirm(second)
                } else {
                    if (first.length >= LockRules.PIN_MIN) acceptFirst(first)
                }
            }

            fun handlePinBack() {
                localError = null
                if (confirming) {
                    if (second.isEmpty()) {
                        step = SetupStep.Enter
                        first = ""
                    } else {
                        second = second.dropLast(1)
                    }
                } else if (first.isNotEmpty()) {
                    first = first.dropLast(1)
                }
            }

            @Composable
            fun ColumnScope.Header() {
                TextButton(
                    onClick = {
                        if (confirming) {
                            step = SetupStep.Enter
                            resetCredential()
                        } else {
                            step = SetupStep.ChooseType
                            resetCredential()
                        }
                    },
                ) {
                    Text("Back", color = VaultAccent)
                }
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = VaultText)
                Spacer(Modifier.height(8.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = VaultTextMuted)
                Spacer(Modifier.height(if (landscape) 12.dp else 24.dp))
                if (type == LockType.PIN) {
                    PinDots(filled = current.length, slotCount = LockRules.PIN_MAX)
                    Spacer(Modifier.height(12.dp))
                }
                val err = localError ?: errorMessage
                if (err != null) {
                    Text(err, color = VaultDanger, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                }
            }

            @Composable
            fun CredentialInput(compact: Boolean) {
                when (type) {
                    LockType.PIN -> {
                        PinPad(
                            enabled = true,
                            onDigit = ::handlePinDigit,
                            onBackspace = ::handlePinBack,
                            compact = compact,
                            showEnter = true,
                            enterEnabled = current.length in LockRules.PIN_MIN until LockRules.PIN_MAX,
                            onEnter = ::handlePinEnter,
                        )
                    }
                    LockType.PASSWORD -> {
                        PasswordLockField(
                            value = current,
                            onValueChange = {
                                localError = null
                                if (confirming) second = it else first = it
                            },
                            onSubmit = {
                                if (confirming) acceptConfirm(second) else acceptFirst(first)
                            },
                            enabled = true,
                            submitLabel = if (confirming) "Confirm" else "Continue",
                            label = if (confirming) "Confirm password" else "Password",
                        )
                    }
                    LockType.PATTERN -> {
                        PatternLock(
                            enabled = true,
                            onPatternComplete = { secret ->
                                localError = null
                                if (confirming) acceptConfirm(secret) else acceptFirst(secret)
                            },
                            compact = compact,
                        )
                    }
                }
            }

            if (landscape) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .background(VaultAmoled)
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
                    Column(
                        Modifier
                            .weight(1.1f)
                            .width(320.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CredentialInput(compact = true)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(VaultAmoled)
                        .padding(horizontal = 28.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Header()
                    Spacer(Modifier.weight(1f))
                    CredentialInput(compact = false)
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}
