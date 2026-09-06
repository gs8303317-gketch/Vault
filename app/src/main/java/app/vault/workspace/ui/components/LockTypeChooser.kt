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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.vault.workspace.auth.LockType
import app.vault.workspace.ui.theme.VaultAccent
import app.vault.workspace.ui.theme.VaultSurface
import app.vault.workspace.ui.theme.VaultTextMuted

@Composable
fun LockTypeChooser(
    selected: LockType?,
    onSelect: (LockType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LockType.entries.forEach { type ->
            val isSelected = type == selected
            val shape = RoundedCornerShape(16.dp)
            Surface(
                color = VaultSurface,
                shape = shape,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) VaultAccent else VaultAccent.copy(alpha = 0.2f),
                        shape = shape,
                    )
                    .clickable { onSelect(type) },
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = when (type) {
                            LockType.PIN -> Icons.Default.Dialpad
                            LockType.PASSWORD -> Icons.Default.Password
                            LockType.PATTERN -> Icons.Default.Gesture
                        },
                        contentDescription = null,
                        tint = VaultAccent,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.size(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(type.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            when (type) {
                                LockType.PIN -> "4–6 digits"
                                LockType.PASSWORD -> "6–10 characters"
                                LockType.PATTERN -> "Connect at least 4 dots"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = VaultTextMuted,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
