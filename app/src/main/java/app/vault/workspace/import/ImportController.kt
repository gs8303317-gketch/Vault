package app.vault.workspace.import

import android.net.Uri
import app.vault.workspace.data.VaultItem
import app.vault.workspace.data.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImportController(
    private val repository: VaultRepository,
) {
    data class ImportResult(
        val succeeded: List<VaultItem>,
        val failures: List<Pair<Uri, String>>,
    )

    suspend fun importAll(
        uris: List<Uri>,
        onFileProgress: (index: Int, total: Int, fraction: Float) -> Unit = { _, _, _ -> },
    ): ImportResult = withContext(Dispatchers.IO) {
        val ok = mutableListOf<VaultItem>()
        val fail = mutableListOf<Pair<Uri, String>>()
        uris.forEachIndexed { index, uri ->
            onFileProgress(index, uris.size, 0f)
            val result = repository.importUri(uri) { frac ->
                onFileProgress(index, uris.size, frac)
            }
            result.onSuccess { ok.add(it) }
                .onFailure { e -> fail.add(uri to (e.message ?: "Import failed")) }
            onFileProgress(index, uris.size, 1f)
        }
        ImportResult(ok, fail)
    }
}
