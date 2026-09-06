package app.vault.workspace.ui.export

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import app.vault.workspace.ui.theme.VaultDanger

@Composable
fun ExportConfirmDialog(
    fileName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    /** When true, confirm copy notes that JPEG location EXIF is stripped. */
    stripsLocationExif: Boolean = false,
) {
    val body = buildString {
        append("\"$fileName\" will be saved outside Vault as a normal unencrypted file. ")
        append("Anyone with access to that file can open it. The encrypted original stays in Vault.")
        if (stripsLocationExif) {
            append(" Image export strips location EXIF (GPS) when applicable.")
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export unencrypted copy?") },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Export", color = VaultDanger)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
