package app.vault.workspace.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import java.io.File

object PlayerFactory {
    fun createDecryptingPlayer(
        context: Context,
        vatFile: File,
        dek: ByteArray,
        mimeType: String,
    ): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs */ 15_000,
                /* maxBufferMs */ 50_000,
                /* bufferForPlaybackMs */ 1_250,
                /* bufferForPlaybackAfterRebufferMs */ 2_500,
            )
            .build()

        // CBR seeking helps audio containers without a TOC. MP4 still uses its
        // sample-table SeekMap when the extractor builds one from decrypted bytes.
        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setConstantBitrateSeekingAlwaysEnabled(true)

        val player = ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        player.repeatMode = Player.REPEAT_MODE_OFF

        val factory = EncryptedDataSourceFactory(vatFile, dek)
        // Custom scheme + stable path (no .vat suffix) so extractors rely on mime/sniff,
        // never on treating encrypted bytes as a raw file:// MP4.
        val playUri = Uri.parse("vaultenc:///play")
        val mediaSource = ProgressiveMediaSource.Factory(factory, extractorsFactory)
            .createMediaSource(
                MediaItem.Builder()
                    .setUri(playUri)
                    .setMimeType(mimeType)
                    .build(),
            )
        player.setMediaSource(mediaSource)
        player.prepare()
        return player
    }
}
