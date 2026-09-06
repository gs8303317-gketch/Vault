package app.vault.workspace.media

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDescriptorDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import app.vault.workspace.crypto.KeyHierarchy
import java.io.File
import java.io.FileDescriptor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ExoPlayer plus optional proxy PFD handle.
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
    private const val TAG = "VaultPlayerFactory"

    /**
     * Primary: StorageManager proxy PFD + [FileDescriptorDataSource] (seekable FD, no
     * `/proc/self/fd`, no [androidx.media3.datasource.FileDataSource]).
     * Fallback: proven [EncryptedDataSource] path (v0.4.4) if proxy is unavailable or
     * building the FD player throws.
     *
     * Media3 1.11 [DefaultExtractorsFactory] enables mfra seek maps for fMP4 WEB-DLs
     * without sidx; CBR seeking stays on for audio without TOC (not AlwaysEnabled).
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
        // Media3 1.11 DefaultExtractorsFactory already enables FLAG_READ_MFRA_FOR_SEEK_MAP.
        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)

        val appCtx = context.applicationContext
        val handle = EncryptedSeekableOpener.openProxyOrNull(appCtx, vatFile, dek)
        if (handle != null) {
            try {
                return buildProxyFdPlayer(
                    context = appCtx,
                    handle = handle,
                    mimeType = mimeType,
                    loadControl = loadControl,
                    extractorsFactory = extractorsFactory,
                )
            } catch (e: Exception) {
                Log.w(TAG, "Proxy FileDescriptorDataSource path failed; falling back", e)
                try {
                    handle.pfd.close()
                } catch (_: Exception) {
                }
                try {
                    handle.releaseResources()
                } catch (_: Exception) {
                }
            }
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

    /**
     * Feeds ExoPlayer via official [FileDescriptorDataSource] on the proxy PFD.
     * Never uses `/proc/self/fd` or [androidx.media3.datasource.FileDataSource].
     */
    private fun buildProxyFdPlayer(
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

        val factory = ExclusiveFileDescriptorDataSourceFactory(
            fileDescriptor = handle.pfd.fileDescriptor,
            length = handle.plaintextSize,
        )
        // Uri is ignored by FileDescriptorDataSource for reads; keep a stable non-file scheme.
        val playUri = Uri.parse("vaultfd:///play")
        val mediaSource = ProgressiveMediaSource.Factory(factory, extractorsFactory)
            .createMediaSource(
                MediaItem.Builder()
                    .setUri(playUri)
                    .setMimeType(mimeType)
                    .build(),
            )
        player.setMediaSource(mediaSource)
        player.prepare()
        return DecryptingPlayback(player = player, mediaHandle = handle)
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

/**
 * Media3 [FileDescriptorDataSource] allows only one open instance per FD.
 * ProgressiveMediaPeriod opens a single source at a time; this factory reuses one
 * instance and serializes open/close so a second create+open waits for close.
 */
private class ExclusiveFileDescriptorDataSourceFactory(
    fileDescriptor: FileDescriptor,
    length: Long,
) : DataSource.Factory {
    private val lock = Any()
    private val inner = FileDescriptorDataSource(fileDescriptor, /* offset= */ 0L, length)
    private var opened = false

    override fun createDataSource(): DataSource =
        object : DataSource {
            override fun addTransferListener(transferListener: TransferListener) {
                inner.addTransferListener(transferListener)
            }

            override fun open(dataSpec: DataSpec): Long =
                synchronized(lock) {
                    var spins = 0
                    while (opened && spins < 200) {
                        try {
                            (lock as Object).wait(25)
                        } catch (_: InterruptedException) {
                            Thread.currentThread().interrupt()
                            break
                        }
                        spins++
                    }
                    opened = true
                    try {
                        inner.open(dataSpec)
                    } catch (e: Exception) {
                        opened = false
                        (lock as Object).notifyAll()
                        throw e
                    }
                }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                inner.read(buffer, offset, length)

            override fun getUri(): Uri? = inner.uri

            override fun getResponseHeaders(): Map<String, List<String>> =
                inner.responseHeaders

            override fun close() {
                synchronized(lock) {
                    try {
                        inner.close()
                    } finally {
                        opened = false
                        (lock as Object).notifyAll()
                    }
                }
            }
        }
}
