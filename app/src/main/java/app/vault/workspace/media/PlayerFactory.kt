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
 *
 * Callers must create/use the player on the **main** thread (Media3 requirement).
 * Heavy decrypt belongs on a background thread via [prepareVideoCacheFile] first.
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
     * IO-only: decrypt video into session play-cache. Does not touch ExoPlayer.
     */
    fun prepareVideoCacheFile(
        context: Context,
        vatFile: File,
        dek: ByteArray,
        mimeType: String,
        itemKey: String? = null,
    ): File {
        val key = itemKey?.takeIf { it.isNotBlank() } ?: vatFile.name
        return PlaybackPlaintextCache.getOrCreate(
            context = context.applicationContext,
            itemKey = key,
            vatFile = vatFile,
            dek = dek,
            mimeType = mimeType,
        )
    }

    /**
     * **Main thread only.** Builds ExoPlayer.
     * Video: pass [preparedVideoFile] from [prepareVideoCacheFile].
     * Audio: pass [vatFile]+[dek]; streams via [EncryptedDataSource].
     */
    fun createDecryptingPlayer(
        context: Context,
        vatFile: File,
        dek: ByteArray,
        mimeType: String,
        itemKey: String? = null,
        preparedVideoFile: File? = null,
    ): DecryptingPlayback {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs */ 15_000,
                /* maxBufferMs */ 50_000,
                /* bufferForPlaybackMs */ 1_250,
                /* bufferForPlaybackAfterRebufferMs */ 2_500,
            )
            .build()

        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)

        val appCtx = context.applicationContext
        val isVideo = mimeType.startsWith("video/", ignoreCase = true)

        return if (isVideo) {
            val cacheFile = preparedVideoFile
                ?: prepareVideoCacheFile(appCtx, vatFile, dek, mimeType, itemKey)
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
