package app.vault.workspace.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import app.vault.workspace.crypto.KeyHierarchy
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ExoPlayer plus optional cleanup (wipe DEK copy for streaming decrypt).
 * [release] is idempotent.
 *
 * Callers must create/use the player on the **main** thread (Media3 requirement).
 * Load the DEK on a background thread first; do not create ExoPlayer on IO.
 *
 * All media plays only via [EncryptedDataSource] / streaming decrypt.
 * No remux, no full decrypt, no plaintext left on disk, no seek prepare/indexing.
 */
class DecryptingPlayback(
    val player: ExoPlayer,
    private val extraCleanup: (() -> Unit)? = null,
) {
    private val released = AtomicBoolean(false)

    fun release() {
        if (!released.compareAndSet(false, true)) return
        try {
            player.release()
        } catch (_: Exception) {
        }
        try {
            extraCleanup?.invoke()
        } catch (_: Exception) {
        }
    }
}

object PlayerFactory {
    /**
     * **Main thread only.** Builds ExoPlayer with streaming [EncryptedDataSource]
     * for both audio and video. Does not write play-cache / remux / seekprep files.
     *
     * [mimeType] / [itemKey] are kept for call-site compatibility. Mime is **not**
     * set on [MediaItem] — extractors sniff the container (WEB-DL may be mkv labeled mp4).
     */
    @Suppress("UNUSED_PARAMETER")
    fun createDecryptingPlayer(
        context: Context,
        vatFile: File,
        dek: ByteArray,
        mimeType: String,
        itemKey: String? = null,
    ): DecryptingPlayback {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs */ 15_000,
                /* maxBufferMs */ 50_000,
                /* bufferForPlaybackMs */ 1_250,
                /* bufferForPlaybackAfterRebufferMs */ 2_500,
            )
            .build()

        // CBR enabled for containers that support it natively — do NOT invent a
        // SeekMap via ConstantBitrateSeekMap (that crashed WEB-DL mid-cluster seeks).
        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)

        val appCtx = context.applicationContext
        val dekCopy = dek.copyOf()
        val player = ExoPlayer.Builder(appCtx)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        player.repeatMode = Player.REPEAT_MODE_OFF

        val factory = EncryptedDataSourceFactory(vatFile, dekCopy)
        val playUri = Uri.parse("vaultenc:///play")
        // Omit setMimeType — let extractors sniff (wrong container label mis-routes).
        val mediaSource = ProgressiveMediaSource.Factory(factory, extractorsFactory)
            .createMediaSource(
                MediaItem.Builder()
                    .setUri(playUri)
                    .build(),
            )
        player.setMediaSource(mediaSource)
        player.prepare()
        return DecryptingPlayback(
            player = player,
            extraCleanup = { KeyHierarchy.wipe(dekCopy) },
        )
    }
}
