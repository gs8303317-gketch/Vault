package app.vault.workspace.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vault.workspace.ui.theme.VaultAccent
import app.vault.workspace.ui.theme.VaultAmoled
import app.vault.workspace.ui.theme.VaultOnAccent
import app.vault.workspace.ui.theme.VaultSurface
import app.vault.workspace.ui.theme.VaultText
import app.vault.workspace.ui.theme.VaultTextMuted

@Composable
fun FirstRunScreen(onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VaultAmoled)
            .padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(VaultSurface)
                .border(1.5.dp, VaultAccent.copy(alpha = 0.55f), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Shield,
                contentDescription = null,
                tint = VaultAccent,
                modifier = Modifier.size(42.dp),
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "Welcome to Vault",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
            color = VaultText,
            letterSpacing = 0.4.sp,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Your files stay encrypted on this device. There is no account and no cloud.\n\n" +
                "Next you’ll choose lock type — PIN, Password, or Pattern (lock type choose karo). " +
                "It cannot be recovered — if you forget it, your vault is permanently inaccessible. " +
                "Clearing app data also destroys the vault.",
            style = MaterialTheme.typography.bodyLarge,
            color = VaultTextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(36.dp))
        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = VaultAccent,
                contentColor = VaultOnAccent,
            ),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text("Choose lock type", fontWeight = FontWeight.SemiBold)
        }
    }
}
