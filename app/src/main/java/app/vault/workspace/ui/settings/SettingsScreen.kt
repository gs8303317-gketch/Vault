package app.vault.workspace.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.vault.workspace.BuildConfig
import app.vault.workspace.data.formatHumanSize
import app.vault.workspace.auth.AutoLockController
import app.vault.workspace.auth.LockType
import app.vault.workspace.ui.theme.VaultAccent
import app.vault.workspace.ui.theme.VaultBg
import app.vault.workspace.ui.theme.VaultDanger
import app.vault.workspace.ui.theme.VaultTextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLockNow: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenFolders: () -> Unit,
    idleTimeoutMs: Long,
    onIdleTimeoutSelected: (Long) -> Unit,
    biometricHardwareAvailable: Boolean = false,
    biometricEnabled: Boolean = false,
    onBiometricToggle: (Boolean) -> Unit = {},
    biometricError: String? = null,
    storageUsedBytes: Long = 0L,
    currentLockType: LockType = LockType.PIN,
    currentPinLength: Int = 4,
    onChangeLock: (current: String, newType: LockType, newCredential: String) -> Unit = { _, _, _ -> },
    changePinError: String? = null,
    changePinBusy: Boolean = false,
    changePinSuccessEpoch: Int = 0,
    onClearChangePinError: () -> Unit = {},
) {
    var showIdlePicker by remember { mutableStateOf(false) }
    var showChangePin by remember { mutableStateOf(false) }
    LaunchedEffect(changePinSuccessEpoch) {
        if (changePinSuccessEpoch > 0) {
            showChangePin = false
            onClearChangePinError()
        }
    }
    val idleLabel = AutoLockController.PRESETS.firstOrNull { it.first == idleTimeoutMs }?.second
        ?: "${idleTimeoutMs / 1000}s"

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
                headlineContent = { Text("Vault storage") },
                supportingContent = {
                    Text(
                        "${formatHumanSize(storageUsedBytes)} encrypted (library + trash)",
                        color = VaultTextMuted,
                    )
                },
                leadingContent = {
                    Icon(Icons.Default.Storage, contentDescription = null, tint = VaultAccent)
                },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Auto-lock") },
                supportingContent = {
                    Text(
                        "Lock after $idleLabel idle. App still locks immediately in background.",
                        color = VaultTextMuted,
                    )
                },
                leadingContent = {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = VaultAccent)
                },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = VaultTextMuted,
                    )
                },
                modifier = Modifier.clickable { showIdlePicker = true },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Change lock") },
                supportingContent = {
                    Text(
                        "Switch PIN, password, or pattern. Re-wraps vault key; biometric turns off.",
                        color = VaultTextMuted,
                    )
                },
                leadingContent = {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = VaultAccent)
                },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = VaultTextMuted,
                    )
                },
                modifier = Modifier.clickable {
                    onClearChangePinError()
                    showChangePin = true
                },
            )
            HorizontalDivider()
            if (biometricHardwareAvailable) {
                ListItem(
                    headlineContent = { Text("Biometric unlock") },
                    supportingContent = {
                        Text(
                            biometricError
                                ?: "Unlock with fingerprint or face. Your lock credential always works as fallback.",
                            color = if (biometricError != null) VaultDanger else VaultTextMuted,
                        )
                    },
                    leadingContent = {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, tint = VaultAccent)
                    },
                    trailingContent = {
                        Switch(
                            checked = biometricEnabled,
                            onCheckedChange = onBiometricToggle,
                            colors = SwitchDefaults.colors(checkedTrackColor = VaultAccent),
                        )
                    },
                )
                HorizontalDivider()
            }
            ListItem(
                headlineContent = { Text("Folders") },
                supportingContent = {
                    Text("Organize items into encrypted-name folders", color = VaultTextMuted)
                },
                leadingContent = {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = VaultAccent)
                },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = VaultTextMuted,
                    )
                },
                modifier = Modifier.clickable(onClick = onOpenFolders),
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
                headlineContent = { Text("Lock recovery") },
                supportingContent = {
                    Text(
                        "Your lock credential cannot be recovered. Forgetting it permanently locks this vault.",
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

    if (showIdlePicker) {
        AlertDialog(
            onDismissRequest = { showIdlePicker = false },
            title = { Text("Auto-lock idle time") },
            text = {
                Column {
                    AutoLockController.PRESETS.forEach { (ms, label) ->
                        ListItem(
                            headlineContent = { Text(label) },
                            leadingContent = {
                                RadioButton(
                                    selected = idleTimeoutMs == ms,
                                    onClick = {
                                        onIdleTimeoutSelected(ms)
                                        showIdlePicker = false
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = VaultAccent),
                                )
                            },
                            modifier = Modifier.clickable {
                                onIdleTimeoutSelected(ms)
                                showIdlePicker = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIdlePicker = false }) { Text("Close") }
            },
        )
    }

    if (showChangePin) {
        ChangePinDialog(
            currentLockType = currentLockType,
            currentPinLength = currentPinLength,
            errorMessage = changePinError,
            busy = changePinBusy,
            onDismiss = {
                showChangePin = false
                onClearChangePinError()
            },
            onSubmit = { current, newType, newCred ->
                onChangeLock(current, newType, newCred)
            },
        )
    }
}
