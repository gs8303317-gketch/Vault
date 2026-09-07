package app.vault.workspace.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Password
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.vault.workspace.auth.LockType
import app.vault.workspace.ui.theme.VaultAccent
import app.vault.workspace.ui.theme.VaultSurface
import app.vault.workspace.ui.theme.VaultText
import app.vault.workspace.ui.theme.VaultTextMuted

/**
 * First-run / setup lock-type picker — large tappable cards so PIN is not the
 * only option that feels available.
 */
@Composable
fun LockTypeChooser(
    selected: LockType?,
    onSelect: (LockType) -> Unit,
    modifier: Modifier = Modifier,
    /** When true, tapping a card both selects and invokes [onSelect] (caller may advance). */
    compact: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp),
    ) {
        LockType.entries.forEach { type ->
            val isSelected = type == selected
            val shape = RoundedCornerShape(18.dp)
            Surface(
                color = if (isSelected) VaultAccent.copy(alpha = 0.14f) else VaultSurface,
                shape = shape,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) VaultAccent else VaultAccent.copy(alpha = 0.22f),
                        shape = shape,
                    )
                    .clickable { onSelect(type) },
            ) {
                Row(
                    Modifier.padding(
                        horizontal = 20.dp,
                        vertical = if (compact) 16.dp else 22.dp,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = when (type) {
                            LockType.PIN -> Icons.Default.Dialpad
                            LockType.PASSWORD -> Icons.Default.Password
                            LockType.PATTERN -> Icons.Default.Gesture
                        },
                        contentDescription = type.displayName,
                        tint = VaultAccent,
                        modifier = Modifier.size(if (compact) 30.dp else 38.dp),
                    )
                    Spacer(Modifier.size(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            type.displayName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = VaultText,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            when (type) {
                                LockType.PIN -> "4–6 digits · quick unlock"
                                LockType.PASSWORD -> "6–10 characters · strongest"
                                LockType.PATTERN -> "Connect ≥4 dots · visual"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = VaultTextMuted,
                        )
                    }
                    RadioButton(
                        selected = isSelected,
                        onClick = { onSelect(type) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = VaultAccent,
                            unselectedColor = VaultTextMuted,
                        ),
                    )
                }
            }
        }
    }
}
