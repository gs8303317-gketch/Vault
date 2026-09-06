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
 * ExoPlayer plus optional proxy/memfd handle. [release] always releases the player
 * then the handle (closes PFD → wipe DEK / quit proxy thread). Idempotent.
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
     * Prefer StorageManager proxy FD so Mp4Extractor sees a normal seekable file
     * (plaintext size + random-access pread). CBR-always was masking unseekable
     * SeekMaps for audio only — remove it so video uses real sample-table seeks.
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

        // CBR seeking helps audio containers without a TOC. Do NOT force
        // AlwaysEnabled — that does not make Mp4Extractor seekable, and hides
        // unseekable maps. Proxy FD path gives Mp4 a real SeekMap.
        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)

        val appCtx = context.applicationContext
        val handle =
            EncryptedSeekableOpener.openProxyOrNull(appCtx, vatFile, dek)
                ?: EncryptedSeekableOpener.openMemfdOrNull(vatFile, dek)

        if (handle != null) {
            return buildProxyPlayer(
                context = appCtx,
                handle = handle,
                mimeType = mimeType,
                loadControl = loadControl,
                extractorsFactory = extractorsFactory,
            )
        }

        return buildEncryptedDataSourcePlayer(
            context = appCtx,
            vatFile = vatFile,
            dek = dek,
            mimeType = mimeType,
            loadControl = loadControl,
            extractorsFactory = extractorsFactory,
        )
    }

    private fun buildProxyPlayer(
        context: Context,
        handle: EncryptedSeekableHandle,
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

        // Independent open per DataSource via /proc/self/fd — keeps proxy PFD alive
        // for the player lifetime; Media3 sees a normal local file length + seeks.
        val playUri = Uri.parse("file:///proc/self/fd/${handle.pfd.fd}")
        val mediaSource = ProgressiveMediaSource.Factory(
            FileDataSource.Factory(),
            extractorsFactory,
        ).createMediaSource(
            MediaItem.Builder()
                .setUri(playUri)
                .setMimeType(mimeType)
                .build(),
        )
        player.setMediaSource(mediaSource)
        player.prepare()
        return DecryptingPlayback(player, handle)
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
            mediaHandle = null,
            extraCleanup = { KeyHierarchy.wipe(dekCopy) },
        )
    }
}
