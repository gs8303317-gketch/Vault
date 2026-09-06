package app.vault.workspace.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.vault.workspace.auth.LockRules
import app.vault.workspace.ui.theme.VaultAccent
import app.vault.workspace.ui.theme.VaultOnAccent
import app.vault.workspace.ui.theme.VaultSurface
import app.vault.workspace.ui.theme.VaultText
import app.vault.workspace.ui.theme.VaultTextMuted

@Composable
fun PasswordLockField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    label: String = "Password",
    submitLabel: String = "Unlock",
    minForSubmit: Int = LockRules.PASSWORD_MIN,
    maxLength: Int = LockRules.PASSWORD_MAX,
) {
    var visible by remember { mutableStateOf(false) }
    val canSubmit = enabled && value.length in minForSubmit..maxLength
    Column(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = { raw ->
                if (raw.length <= maxLength) onValueChange(raw)
            },
            enabled = enabled,
            singleLine = true,
            label = { Text(label) },
            visualTransformation = if (visible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (visible) "Hide password" else "Show password",
                        tint = VaultAccent,
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { if (canSubmit) onSubmit() }),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = VaultAccent,
                unfocusedBorderColor = VaultAccent.copy(alpha = 0.35f),
                focusedLabelColor = VaultAccent,
                cursorColor = VaultAccent,
                focusedTextColor = VaultText,
                unfocusedTextColor = VaultText,
                focusedContainerColor = VaultSurface,
                unfocusedContainerColor = VaultSurface,
            ),
            modifier = Modifier.fillMaxWidth(),
            supportingText = {
                Text(
                    "${value.length}/$maxLength",
                    color = VaultTextMuted,
                )
            },
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onSubmit,
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = VaultAccent,
                contentColor = VaultOnAccent,
            ),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(submitLabel)
        }
    }
}
