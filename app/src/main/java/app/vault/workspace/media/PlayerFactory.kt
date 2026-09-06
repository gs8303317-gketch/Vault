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
 * ExoPlayer plus optional proxy/memfd handle (unused for media — PDF keeps its own opener).
 * [release] always releases the player then the handle / DEK wipe. Idempotent.
 */
class DecryptingPlayback(
    val player: ExoPlayer,
    private val mediaHandle: EncryptedSeekableHandle?,
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
            mediaHandle?.pfd?.close()
        } catch (_: Exception) {
        }
        try {
            mediaHandle?.releaseResources()
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
     * Always builds ExoPlayer on [EncryptedDataSource] / [EncryptedDataSourceFactory].
     * Do NOT use `/proc/self/fd` + [androidx.media3.datasource.FileDataSource] — opening the
     * StorageManager proxy that way does not reliably drive decrypt callbacks on device, so
     * ExoPlayer sees unusable bytes ("This media format can't play on this device.").
     * Proxy/memfd remain for PDF via [EncryptedSeekableOpener] / [EncryptedPdfOpener].
     */
    fun createDecryptingPlayer(
        context: Context,
        vatFile: File,
        dek: ByteArray,
        mimeType: String,
    ): DecryptingPlayback {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs */ 15_000,
                /* maxBufferMs */ 50_000,
                /* bufferForPlaybackMs */ 1_250,
                /* bufferForPlaybackAfterRebufferMs */ 2_500,
            )
            .build()

        // CBR seeking helps audio containers without a TOC. Do NOT force AlwaysEnabled —
        // that masks unseekable maps and is not a reliable video seek fix.
        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)

        val appCtx = context.applicationContext
        // Own a DEK copy for the DataSource lifetime; wipe on release.
        val dekCopy = dek.copyOf()
        val player = ExoPlayer.Builder(appCtx)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        player.repeatMode = Player.REPEAT_MODE_OFF

        val factory = EncryptedDataSourceFactory(vatFile, dekCopy)
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
        return DecryptingPlayback(
            player = player,
            mediaHandle = null,
            extraCleanup = { KeyHierarchy.wipe(dekCopy) },
        )
    }
}
