package app.vault.workspace.ui.viewer

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import app.vault.workspace.media.PlayerFactory
import app.vault.workspace.ui.theme.VaultAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun MediaPlayerScreen(
    vatFile: File,
    loadDek: suspend () -> ByteArray,
    mimeType: String,
    onPlaybackActive: (Boolean) -> Unit,
    onPlayerCreated: (ExoPlayer) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(vatFile) {
        try {
            val dek = withContext(Dispatchers.IO) { loadDek() }
            val p = PlayerFactory.createDecryptingPlayer(context, vatFile, dek, mimeType)
            p.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    onPlaybackActive(isPlaying)
                }
                override fun onPlayerError(e: PlaybackException) {
                    error = "This media format can't play on this device."
                }
            })
            p.playWhenReady = true
            player = p
            onPlayerCreated(p)
        } catch (e: Exception) {
            error = e.message ?: "Playback failed"
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onPlaybackActive(false)
            player?.release()
            player = null
        }
    }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            error != null -> Text(error!!)
            player == null -> CircularProgressIndicator(color = VaultAccent)
            else -> {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            useController = true
                            this.player = player
                        }
                    },
                    update = { it.player = player },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
