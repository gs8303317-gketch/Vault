package app.vault.workspace.ui.unlock

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vault.workspace.auth.LockRules
import app.vault.workspace.auth.LockType
import app.vault.workspace.ui.components.PasswordLockField
import app.vault.workspace.ui.components.PatternLock
import app.vault.workspace.ui.components.PinDots
import app.vault.workspace.ui.components.PinPad
import app.vault.workspace.ui.theme.VaultAccent
import app.vault.workspace.ui.theme.VaultAmoled
import app.vault.workspace.ui.theme.VaultDanger
import app.vault.workspace.ui.theme.VaultSurface
import app.vault.workspace.ui.theme.VaultText
import app.vault.workspace.ui.theme.VaultTextMuted
import kotlinx.coroutines.delay

@Composable
fun UnlockScreen(
    lockedOutMs: Long,
    errorMessage: String?,
    onSubmitCredential: (String) -> Unit,
    lockType: LockType = LockType.PIN,
    pinLength: Int = LockRules.PIN_MIN,
    biometricAvailable: Boolean = false,
    onBiometricUnlock: (() -> Unit)? = null,
) {
    val submit = onSubmitCredential
    var pin by remember(lockType) { mutableStateOf("") }
    var password by remember(lockType) { mutableStateOf("") }
    var patternError by remember { mutableStateOf(false) }
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

    LaunchedEffect(errorMessage) {
        if (errorMessage != null && lockType == LockType.PATTERN) {
            patternError = true
            delay(450)
            patternError = false
        }
    }

    val enabled = remaining <= 0L
    val landscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val pinSlots = pinLength.coerceIn(LockRules.PIN_MIN, LockRules.PIN_MAX)
    val showBiometric = biometricAvailable && onBiometricUnlock != null && enabled

    fun digit(c: Char) {
        if (!enabled) return
        if (pin.length >= pinSlots) return
        val next = pin + c
        pin = next
        if (next.length == pinSlots) {
            submit(next)
            pin = ""
        }
    }

    fun backspace() {
        if (enabled && pin.isNotEmpty()) pin = pin.dropLast(1)
    }

    fun enterPin() {
        if (!enabled) return
        if (pin.length in LockRules.PIN_MIN..pinSlots) {
            submit(pin)
            pin = ""
        }
    }

    @Composable
    fun BrandMark(compact: Boolean) {
        val shield = if (compact) 52.dp else 76.dp
        val icon = if (compact) 26.dp else 38.dp
        Box(
            modifier = Modifier
                .size(shield)
                .clip(RoundedCornerShape(if (compact) 16.dp else 22.dp))
                .background(VaultSurface)
                .border(
                    width = 1.5.dp,
                    color = VaultAccent.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(if (compact) 16.dp else 22.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Shield,
                contentDescription = null,
                tint = VaultAccent,
                modifier = Modifier.size(icon),
            )
        }
        Spacer(Modifier.height(if (compact) 12.dp else 20.dp))
        Text(
            "Vault",
            style = if (compact) {
                MaterialTheme.typography.headlineMedium
            } else {
                MaterialTheme.typography.headlineLarge
            },
            fontWeight = FontWeight.SemiBold,
            color = VaultText,
            letterSpacing = 1.2.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            lockType.unlockPrompt,
            style = MaterialTheme.typography.bodyMedium,
            color = VaultTextMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(if (compact) 8.dp else 14.dp))
        when {
            remaining > 0 -> Text(
                "Try again in ${formatDuration(remaining)}",
                color = VaultDanger,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            errorMessage != null -> Text(
                errorMessage,
                color = VaultDanger,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            else -> Spacer(Modifier.height(20.dp))
        }
    }

    @Composable
    fun BiometricControl(compact: Boolean) {
        if (!showBiometric) return
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
                onClick = onBiometricUnlock!!,
                modifier = Modifier
                    .size(if (compact) 48.dp else 56.dp)
                    .clip(CircleShape)
                    .background(VaultSurface)
                    .border(1.dp, VaultAccent.copy(alpha = 0.4f), CircleShape),
            ) {
                Icon(
                    Icons.Default.Fingerprint,
                    contentDescription = "Unlock with biometrics",
                    tint = VaultAccent,
                    modifier = Modifier.size(if (compact) 28.dp else 32.dp),
                )
            }
            if (!compact) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onBiometricUnlock) {
                    Text("Use biometrics", color = VaultAccent)
                }
            }
        }
    }

    @Composable
    fun CredentialPanel(compact: Boolean) {
        when (lockType) {
            LockType.PIN -> {
                PinDots(filled = pin.length, slotCount = pinSlots)
                Spacer(Modifier.height(if (compact) 10.dp else 20.dp))
                PinPad(
                    enabled = enabled,
                    onDigit = ::digit,
                    onBackspace = ::backspace,
                    compact = compact,
                    showEnter = pinSlots > LockRules.PIN_MIN,
                    enterEnabled = pin.length in LockRules.PIN_MIN until pinSlots,
                    onEnter = ::enterPin,
                )
            }
            LockType.PASSWORD -> {
                PasswordLockField(
                    value = password,
                    onValueChange = { password = it },
                    onSubmit = {
                        submit(password)
                        password = ""
                    },
                    enabled = enabled,
                    submitLabel = "Unlock",
                    modifier = Modifier.widthIn(max = 360.dp),
                )
            }
            LockType.PATTERN -> {
                PatternLock(
                    enabled = enabled,
                    onPatternComplete = { secret -> submit(secret) },
                    errorFlash = patternError,
                    compact = compact,
                )
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(VaultAmoled),
    ) {
        if (landscape) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(end = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    BrandMark(compact = true)
                    Spacer(Modifier.height(12.dp))
                    BiometricControl(compact = true)
                }
                Column(
                    Modifier
                        .weight(1.15f)
                        .width(360.dp)
                        .padding(start = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CredentialPanel(compact = true)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(28.dp))
                BrandMark(compact = false)
                Spacer(Modifier.weight(1f))
                CredentialPanel(compact = false)
                Spacer(Modifier.height(20.dp))
                BiometricControl(compact = false)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = (ms + 999) / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}
