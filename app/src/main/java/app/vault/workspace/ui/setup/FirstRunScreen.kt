package app.vault.workspace.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.vault.workspace.ui.theme.VaultAccent
import app.vault.workspace.ui.theme.VaultOnAccent
import app.vault.workspace.ui.theme.VaultTextMuted

@Composable
fun FirstRunScreen(onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.Shield,
            contentDescription = null,
            tint = VaultAccent,
            modifier = Modifier.height(72.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text("Welcome to Vault", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(16.dp))
        Text(
            "Your files stay encrypted on this device. There is no account and no cloud.\n\n" +
                "Next you’ll choose lock type — PIN, Password, or Pattern (lock type choose karo). " +
                "It cannot be recovered — if you forget it, your vault is permanently inaccessible. " +
                "Clearing app data also destroys the vault.",
            style = MaterialTheme.typography.bodyLarge,
            color = VaultTextMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = VaultAccent,
                contentColor = VaultOnAccent,
            ),
        ) {
            Text("Choose lock type")
        }
    }
}
