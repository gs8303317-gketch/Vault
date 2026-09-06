package app.vault.workspace.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.vault.workspace.BuildConfig
import app.vault.workspace.ui.theme.VaultBg
import app.vault.workspace.ui.theme.VaultDanger
import app.vault.workspace.ui.theme.VaultTextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLockNow: () -> Unit,
    onOpenTrash: () -> Unit,
) {
    Scaffold(
        containerColor = VaultBg,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            ListItem(
                headlineContent = { Text("Version") },
                supportingContent = {
                    Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Trash") },
                supportingContent = {
                    Text("Restore or permanently delete items", color = VaultTextMuted)
                },
                leadingContent = {
                    Icon(Icons.Default.Delete, contentDescription = null)
                },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = VaultTextMuted,
                    )
                },
                modifier = Modifier.clickable(onClick = onOpenTrash),
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Network") },
                supportingContent = {
                    Text(
                        "Vault has no network permission and never connects online.",
                        color = VaultTextMuted,
                    )
                },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("PIN recovery") },
                supportingContent = {
                    Text(
                        "Your PIN cannot be recovered. Forgetting it permanently locks this vault.",
                        color = VaultTextMuted,
                    )
                },
            )
            HorizontalDivider()
            Button(
                onClick = onLockNow,
                colors = ButtonDefaults.buttonColors(containerColor = VaultDanger),
                modifier = Modifier.padding(top = 24.dp),
            ) {
                Text("Lock now")
            }
        }
    }
}
