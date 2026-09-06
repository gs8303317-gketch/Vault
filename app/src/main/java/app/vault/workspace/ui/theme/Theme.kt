package app.vault.workspace.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val VaultColorScheme = darkColorScheme(
    primary = VaultAccent,
    onPrimary = VaultOnAccent,
    secondary = VaultAccent,
    onSecondary = VaultOnAccent,
    background = VaultBg,
    onBackground = VaultText,
    surface = VaultSurface,
    onSurface = VaultText,
    surfaceVariant = VaultSurface,
    onSurfaceVariant = VaultTextMuted,
    error = VaultDanger,
    onError = VaultText,
)

private val VaultShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun VaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VaultColorScheme,
        typography = VaultTypography,
        shapes = VaultShapes,
        content = content,
    )
}
