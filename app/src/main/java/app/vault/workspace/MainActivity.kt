package app.vault.workspace

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.vault.workspace.import.extractShareUris
import app.vault.workspace.ui.nav.VaultNav
import app.vault.workspace.ui.theme.VaultAmoled
import app.vault.workspace.ui.theme.VaultTheme

/**
 * Thin activity — no FLAG_SECURE in Phase 0 (screenshots allowed for testing).
 * Accepts ACTION_SEND / SEND_MULTIPLE to import into the vault when unlocked.
 */
class MainActivity : FragmentActivity() {
    private var pendingShareUris by mutableStateOf<List<Uri>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingShareUris = extractShareUris(intent)
        val app = application as VaultApp
        setContent {
            VaultTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = VaultAmoled) {
                    VaultNav(
                        session = app.session,
                        repository = app.repository,
                        autoLock = app.autoLock,
                        pendingShareUris = pendingShareUris,
                        onShareConsumed = { pendingShareUris = emptyList() },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val uris = extractShareUris(intent)
        if (uris.isNotEmpty()) {
            pendingShareUris = uris
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        (application as VaultApp).autoLock.bumpIdle()
    }
}
