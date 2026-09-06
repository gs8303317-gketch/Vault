package app.vault.workspace.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vault.workspace.auth.LockRules
import app.vault.workspace.ui.theme.VaultAccent
import app.vault.workspace.ui.theme.VaultOnAccent
import app.vault.workspace.ui.theme.VaultSurface
import app.vault.workspace.ui.theme.VaultText

/**
 * PIN length indicators. [slotCount] is max slots shown (e.g. stored length on unlock,
 * or [LockRules.PIN_MAX] during setup).
 */
@Composable
fun PinDots(
    filled: Int,
    modifier: Modifier = Modifier,
    slotCount: Int = LockRules.PIN_MAX,
) {
    val slots = slotCount.coerceIn(LockRules.PIN_MIN, LockRules.PIN_MAX)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(slots) { i ->
            val active = i < filled
            Box(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(if (active) 14.dp else 12.dp)
                    .clip(CircleShape)
                    .then(
                        if (active) {
                            Modifier.background(VaultAccent)
                        } else {
                            Modifier
                                .background(VaultSurface)
                                .border(1.dp, VaultAccent.copy(alpha = 0.35f), CircleShape)
                        },
                    ),
            )
        }
    }
}

@Composable
fun PinPad(
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    showEnter: Boolean = false,
    enterEnabled: Boolean = false,
    onEnter: (() -> Unit)? = null,
) {
    val keySize: Dp = if (compact) 56.dp else 72.dp
    val rowPad: Dp = if (compact) 2.dp else 6.dp
    val digitSp = if (compact) 20.sp else 24.sp
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(if (showEnter) "↵" else "", "0", "⌫"),
    )
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = rowPad),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEach { label ->
                    when (label) {
                        "" -> Spacer(Modifier.size(keySize))
                        "⌫" -> KeyButton(
                            enabled = enabled,
                            onClick = onBackspace,
                            contentDescription = "Backspace",
                            size = keySize,
                            accent = false,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = null,
                                tint = VaultText,
                            )
                        }
                        "↵" -> KeyButton(
                            enabled = enabled && enterEnabled && onEnter != null,
                            onClick = { onEnter?.invoke() },
                            contentDescription = "Confirm PIN",
                            size = keySize,
                            accent = enterEnabled,
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = if (enterEnabled) VaultOnAccent else VaultText,
                            )
                        }
                        else -> KeyButton(
                            enabled = enabled,
                            onClick = { onDigit(label[0]) },
                            contentDescription = "Digit $label",
                            size = keySize,
                            accent = false,
                        ) {
                            Text(label, fontSize = digitSp, color = VaultText)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyButton(
    enabled: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    size: Dp,
    accent: Boolean,
    content: @Composable () -> Unit,
) {
    val bg = if (accent) {
        Brush.radialGradient(listOf(VaultAccent, VaultAccent.copy(alpha = 0.85f)))
    } else {
        Brush.radialGradient(
            listOf(VaultSurface.copy(alpha = 1f), VaultSurface.copy(alpha = 0.92f)),
        )
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .border(
                width = 1.dp,
                color = if (accent) VaultAccent else VaultAccent.copy(alpha = 0.22f),
                shape = CircleShape,
            )
            .semantics { this.contentDescription = contentDescription }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
