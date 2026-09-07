package app.vault.workspace.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vault.workspace.BuildConfig
import app.vault.workspace.auth.AutoLockController
import app.vault.workspace.auth.LockType
import app.vault.workspace.data.formatHumanSize
import app.vault.workspace.ui.nav.VaultMotion
import app.vault.workspace.ui.theme.VaultAccent
import app.vault.workspace.ui.theme.VaultAmoled
import app.vault.workspace.ui.theme.VaultDanger
import app.vault.workspace.ui.theme.VaultOnAccent
import app.vault.workspace.ui.theme.VaultSurface
import app.vault.workspace.ui.theme.VaultText
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
    val lockSummary = when (currentLockType) {
        LockType.PIN -> "PIN · $currentPinLength digits"
        LockType.PASSWORD -> "Password"
        LockType.PATTERN -> "Pattern"
    }

    Scaffold(
        containerColor = VaultAmoled,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Settings",
                            fontWeight = FontWeight.SemiBold,
                            color = VaultText,
                        )
                        Text(
                            "Security · library · about",
                            color = VaultTextMuted,
                            style = MaterialTheme.typography.labelMedium,
                            fontSize = 12.sp,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = VaultText,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VaultAmoled,
                    titleContentColor = VaultText,
                    navigationIconContentColor = VaultText,
                    actionIconContentColor = VaultAccent,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SettingsSectionHeader("Security")
            SettingsGroup {
                SettingsRow(
                    icon = Icons.Default.Lock,
                    title = "Change lock",
                    subtitle = "$lockSummary · re-wraps vault key; biometric turns off",
                    onClick = {
                        onClearChangePinError()
                        showChangePin = true
                    },
                    showChevron = true,
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.Default.Timer,
                    title = "Auto-lock",
                    subtitle = "Lock after $idleLabel idle. Still locks immediately in background.",
                    onClick = { showIdlePicker = true },
                    showChevron = true,
                )
                if (biometricHardwareAvailable) {
                    SettingsRowDivider()
                    SettingsRow(
                        icon = Icons.Default.Fingerprint,
                        title = "Biometric unlock",
                        subtitle = biometricError
                            ?: "Fingerprint or face · lock credential always works as fallback",
                        subtitleColor = if (biometricError != null) VaultDanger else VaultTextMuted,
                        trailing = {
                            Switch(
                                checked = biometricEnabled,
                                onCheckedChange = onBiometricToggle,
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = VaultAccent,
                                    checkedThumbColor = VaultOnAccent,
                                ),
                            )
                        },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            SettingsSectionHeader("Library")
            SettingsGroup {
                SettingsRow(
                    icon = Icons.Default.Folder,
                    title = "Folders",
                    subtitle = "Organize items into encrypted-name folders",
                    onClick = onOpenFolders,
                    showChevron = true,
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.Default.Delete,
                    title = "Trash",
                    subtitle = "Restore or permanently delete items",
                    onClick = onOpenTrash,
                    showChevron = true,
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.Default.Storage,
                    title = "Vault storage",
                    subtitle = "${formatHumanSize(storageUsedBytes)} encrypted (library + trash)",
                )
            }

            Spacer(Modifier.height(20.dp))
            SettingsSectionHeader("About")
            SettingsGroup {
                SettingsRow(
                    icon = Icons.Default.Info,
                    title = "Version",
                    subtitle = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.Default.WifiOff,
                    title = "Network",
                    subtitle = "No network permission — Vault never connects online.",
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.Default.Shield,
                    title = "Lock recovery",
                    subtitle = "Your lock credential cannot be recovered. Forgetting it permanently locks this vault.",
                )
            }

            Spacer(Modifier.height(20.dp))
            SettingsSectionHeader("Danger")
            SettingsGroup {
                Button(
                    onClick = onLockNow,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VaultDanger,
                        contentColor = VaultText,
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .height(48.dp),
                ) {
                    Icon(
                        Icons.Default.LockOpen,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.size(10.dp))
                    Text("Lock now", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }

    if (showIdlePicker) {
        AlertDialog(
            onDismissRequest = { showIdlePicker = false },
            properties = VaultMotion.dialogProperties,
            containerColor = VaultSurface,
            title = {
                Text(
                    "Auto-lock idle time",
                    fontWeight = FontWeight.SemiBold,
                    color = VaultText,
                )
            },
            text = {
                Column {
                    AutoLockController.PRESETS.forEach { (ms, label) ->
                        ListItem(
                            headlineContent = { Text(label, color = VaultText) },
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
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                onIdleTimeoutSelected(ms)
                                showIdlePicker = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIdlePicker = false }) {
                    Text("Close", color = VaultAccent)
                }
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

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = VaultAccent,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.1.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp, top = 4.dp),
    )
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(VaultSurface)
            .border(1.dp, VaultAccent.copy(alpha = 0.12f), RoundedCornerShape(16.dp)),
    ) {
        content()
    }
}

@Composable
private fun SettingsRowDivider() {
    HorizontalDivider(
        color = VaultAmoled.copy(alpha = 0.85f),
        thickness = 1.dp,
        modifier = Modifier.padding(start = 56.dp),
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = false,
    subtitleColor: Color = VaultTextMuted,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(VaultAccent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = VaultAccent,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = VaultText,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = subtitleColor,
            )
        }
        when {
            trailing != null -> trailing()
            showChevron -> Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = VaultTextMuted,
            )
        }
    }
}
