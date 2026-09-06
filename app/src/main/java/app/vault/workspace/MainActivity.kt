package app.vault.workspace

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import app.vault.workspace.ui.nav.VaultNav
import app.vault.workspace.ui.theme.VaultBg
import app.vault.workspace.ui.theme.VaultTheme

/**
 * Thin activity — no FLAG_SECURE in Phase 0 (screenshots allowed for testing).
 */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as VaultApp
        setContent {
            VaultTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = VaultBg) {
                    VaultNav(
                        session = app.session,
                        repository = app.repository,
                        autoLock = app.autoLock,
                    )
                }
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        (application as VaultApp).autoLock.bumpIdle()
    }
}
