package app.vault.workspace.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vault.workspace.auth.PinRules
import app.vault.workspace.ui.theme.VaultAccent
import app.vault.workspace.ui.theme.VaultSurface
import app.vault.workspace.ui.theme.VaultText

@Composable
fun PinDots(filled: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(PinRules.PIN_LENGTH) { i ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (i < filled) VaultAccent else VaultSurface),
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
) {
    val keySize: Dp = if (compact) 56.dp else 72.dp
    val rowPad: Dp = if (compact) 2.dp else 6.dp
    val digitSp = if (compact) 20.sp else 24.sp
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫"),
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
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = null,
                                tint = VaultText,
                            )
                        }
                        else -> KeyButton(
                            enabled = enabled,
                            onClick = { onDigit(label[0]) },
                            contentDescription = "Digit $label",
                            size = keySize,
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
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(VaultSurface)
            .semantics { this.contentDescription = contentDescription }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
