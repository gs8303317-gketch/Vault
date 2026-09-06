package app.vault.workspace.export

import android.net.Uri
import app.vault.workspace.data.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExportController(
    private val repository: VaultRepository,
) {
    suspend fun export(itemId: String, dest: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            repository.exportToUri(itemId, dest)
        }
}
