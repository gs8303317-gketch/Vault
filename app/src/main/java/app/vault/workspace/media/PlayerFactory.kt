package app.vault.workspace.media

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import java.io.File

object PlayerFactory {
    fun createDecryptingPlayer(
        context: Context,
        vatFile: File,
        dek: ByteArray,
        mimeType: String,
    ): ExoPlayer {
        val player = ExoPlayer.Builder(context).build()
        val factory = EncryptedDataSourceFactory(vatFile, dek)
        val mediaSource = ProgressiveMediaSource.Factory(factory)
            .createMediaSource(
                MediaItem.Builder()
                    .setUri(android.net.Uri.fromFile(vatFile))
                    .setMimeType(mimeType)
                    .build(),
            )
        player.setMediaSource(mediaSource)
        player.prepare()
        return player
    }
}
