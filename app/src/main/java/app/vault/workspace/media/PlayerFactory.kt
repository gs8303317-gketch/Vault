package app.vault.workspace.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.FileDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import app.vault.workspace.crypto.KeyHierarchy
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ExoPlayer plus optional cleanup (e.g. wipe DEK copy for streaming audio).
 * Video play-cache files are session-scoped and wiped on lock — not deleted here.
 * [release] is idempotent.
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
     * Video (mime starts with video/): decrypt once into [PlaybackPlaintextCache], play via
     * [ProgressiveMediaSource] + [FileDataSource] / [Uri.fromFile] (real SeekMap).
     * Audio: stream via [EncryptedDataSource] (seek already works).
     * Proxy / FileDescriptorDataSource paths are not used for playback.
     */
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

        // CBR seeking helps audio containers without a TOC.
        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)

        val appCtx = context.applicationContext
        val isVideo = mimeType.startsWith("video/", ignoreCase = true)

        return if (isVideo) {
            val key = itemKey?.takeIf { it.isNotBlank() } ?: vatFile.name
            val cacheFile = PlaybackPlaintextCache.getOrCreate(
                context = appCtx,
                itemKey = key,
                vatFile = vatFile,
                dek = dek,
                mimeType = mimeType,
            )
            buildFileCachePlayer(
                context = appCtx,
                cacheFile = cacheFile,
                mimeType = mimeType,
                loadControl = loadControl,
                extractorsFactory = extractorsFactory,
            )
        } else {
            buildEncryptedDataSourcePlayer(
                context = appCtx,
                vatFile = vatFile,
                dek = dek,
                mimeType = mimeType,
                loadControl = loadControl,
                extractorsFactory = extractorsFactory,
            )
        }
    }

    /**
     * Real filesystem file → ProgressiveMediaSource + FileDataSource → real SeekMap.
     * Cache entry is kept for the unlock session (wiped on lock).
     */
    private fun buildFileCachePlayer(
        context: Context,
        cacheFile: File,
        mimeType: String,
        loadControl: DefaultLoadControl,
        extractorsFactory: DefaultExtractorsFactory,
    ): DecryptingPlayback {
        val player = ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        player.repeatMode = Player.REPEAT_MODE_OFF

        val factory = FileDataSource.Factory()
        val mediaSource = ProgressiveMediaSource.Factory(factory, extractorsFactory)
            .createMediaSource(
                MediaItem.Builder()
                    .setUri(Uri.fromFile(cacheFile))
                    .setMimeType(mimeType)
                    .build(),
            )
        player.setMediaSource(mediaSource)
        player.prepare()
        return DecryptingPlayback(player = player)
    }

    private fun buildEncryptedDataSourcePlayer(
        context: Context,
        vatFile: File,
        dek: ByteArray,
        mimeType: String,
        loadControl: DefaultLoadControl,
        extractorsFactory: DefaultExtractorsFactory,
    ): DecryptingPlayback {
        // Own a DEK copy for the DataSource lifetime; wipe on release.
        val dekCopy = dek.copyOf()
        val player = ExoPlayer.Builder(context)
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
            extraCleanup = { KeyHierarchy.wipe(dekCopy) },
        )
    }
}
