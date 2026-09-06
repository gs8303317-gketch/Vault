package app.vault.workspace.ui.viewer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.vault.workspace.media.PlayerFactory
import app.vault.workspace.ui.theme.VaultAccent
import app.vault.workspace.ui.theme.VaultBg
import app.vault.workspace.ui.theme.VaultOnAccent
import app.vault.workspace.ui.theme.VaultSurface
import app.vault.workspace.ui.theme.VaultText
import app.vault.workspace.ui.theme.VaultTextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

private const val SKIP_MS = 10_000L
private const val CONTROLS_HIDE_MS = 3_000L

/**
 * Premium offline player overlay on Media3 ExoPlayer (decrypting path unchanged).
 * Video: immersive black + brightness (left vertical) / volume (right vertical) /
 * horizontal scrub / double-tap seek. Audio: dark UI, large play/pause, seek + volume.
 *
 * Brightness writes the activity window [android.view.WindowManager.LayoutParams.screenBrightness]
 * (0.01f..1f) and is left as-is on exit — session-local window attr only; system brightness
 * is not changed.
 */
@Composable
fun MediaPlayerScreen(
    vatFile: File,
    loadDek: suspend () -> ByteArray,
    mimeType: String,
    onPlaybackActive: (Boolean) -> Unit,
    onPlayerCreated: (ExoPlayer) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    onControlsVisibilityChanged: (Boolean) -> Unit = {},
) {
    val isAudio = mimeType.startsWith("audio/", ignoreCase = true)
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

    Box(
        modifier
            .fillMaxSize()
            .background(if (isAudio) VaultBg else Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        when {
            error != null -> Text(error!!, color = Color.White)
            player == null -> CircularProgressIndicator(color = VaultAccent)
            else -> {
                PremiumPlayerOverlay(
                    player = player!!,
                    isAudio = isAudio,
                    title = title,
                    onControlsVisibilityChanged = onControlsVisibilityChanged,
                )
            }
        }
    }
}

@Composable
private fun PremiumPlayerOverlay(
    player: ExoPlayer,
    isAudio: Boolean,
    title: String?,
    onControlsVisibilityChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var controlsVisible by remember { mutableStateOf(true) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableFloatStateOf(0f) }

    var volumeOverlay by remember { mutableStateOf<Int?>(null) }
    var brightnessOverlay by remember { mutableStateOf<Int?>(null) }
    var seekOverlayMs by remember { mutableStateOf<Long?>(null) }

    // Window brightness 0.01..1; start from current window or mid
    var brightness by remember {
        mutableFloatStateOf(
            activity?.window?.attributes?.screenBrightness
                ?.takeIf { it in 0.01f..1f }
                ?: 0.5f,
        )
    }
    // Fractional music volume 0..1 for smooth vertical drag
    var volumeFraction by remember {
        mutableFloatStateOf(
            run {
                val maxV = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxV
            },
        )
    }

    LaunchedEffect(player) {
        while (isActive) {
            if (!scrubbing) {
                positionMs = player.currentPosition.coerceAtLeast(0L)
            }
            val d = player.duration
            durationMs = if (d > 0) d else 0L
            isPlaying = player.isPlaying
            delay(200)
        }
    }

    LaunchedEffect(controlsVisible, isPlaying) {
        onControlsVisibilityChanged(controlsVisible)
        if (controlsVisible && isPlaying) {
            delay(CONTROLS_HIDE_MS)
            controlsVisible = false
        }
    }

    LaunchedEffect(volumeOverlay) {
        if (volumeOverlay != null) {
            delay(800)
            volumeOverlay = null
        }
    }
    LaunchedEffect(brightnessOverlay) {
        if (brightnessOverlay != null) {
            delay(800)
            brightnessOverlay = null
        }
    }
    LaunchedEffect(seekOverlayMs) {
        if (seekOverlayMs != null) {
            delay(700)
            seekOverlayMs = null
        }
    }

    fun showControls() {
        controlsVisible = true
    }

    fun togglePlay() {
        if (player.isPlaying) player.pause() else player.play()
        showControls()
    }

    fun seekBy(deltaMs: Long) {
        val dur = player.duration.coerceAtLeast(0L)
        val target = (player.currentPosition + deltaMs)
            .coerceIn(0L, if (dur > 0) dur else Long.MAX_VALUE)
        player.seekTo(target)
        positionMs = target
        seekOverlayMs = deltaMs
        showControls()
    }

    fun applyVolumeFraction(fraction: Float) {
        val f = fraction.coerceIn(0f, 1f)
        volumeFraction = f
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val vol = (f * maxVol).toInt().coerceIn(0, maxVol)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0)
        volumeOverlay = (f * 100).toInt()
    }

    fun applyBrightnessFraction(fraction: Float) {
        val v = fraction.coerceIn(0.01f, 1f)
        brightness = v
        activity?.window?.let { win ->
            val lp = win.attributes
            lp.screenBrightness = v
            win.attributes = lp
        }
        brightnessOverlay = (v * 100).toInt()
    }

    Box(Modifier.fillMaxSize()) {
        if (!isAudio) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        keepScreenOn = true
                        this.player = player
                    }
                },
                update = { view ->
                    view.player = player
                    view.keepScreenOn = true
                    view.useController = false
                    view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier
                        .size(120.dp)
                        .background(VaultSurface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = VaultAccent,
                        modifier = Modifier.size(64.dp),
                    )
                }
                if (!title.isNullOrBlank()) {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        title,
                        color = VaultText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        // Gesture layer: vertical = brightness(left)/volume(right), horizontal = scrub,
        // tap = toggle controls, double-tap = ±10s
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(isAudio) {
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    val height = size.height.toFloat().coerceAtLeast(1f)
                    val tapSlop = viewConfiguration.touchSlop
                    val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
                    var lastTapTime = 0L
                    var lastTapX = 0f

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startX = down.position.x
                        val isLeft = startX < width / 2f
                        var totalDx = 0f
                        var totalDy = 0f
                        var mode = 0 // 0 undecided, 1 vertical, 2 horizontal
                        var dragged = false
                        var gestureSeekAccum = 0L
                        // Gesture-local trackers avoid stale Compose snapshot reads mid-drag
                        var gestureVol = volumeFraction
                        var gestureBright = brightness

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break

                            if (change.pressed) {
                                val dx = change.positionChange().x
                                val dy = change.positionChange().y
                                totalDx += dx
                                totalDy += dy
                                change.consume()

                                if (mode == 0) {
                                    val adx = abs(totalDx)
                                    val ady = abs(totalDy)
                                    if (adx > tapSlop || ady > tapSlop) {
                                        mode = if (ady >= adx) 1 else 2
                                        dragged = true
                                        gestureVol = volumeFraction
                                        gestureBright = brightness
                                        if (mode == 2) {
                                            gestureSeekAccum = 0L
                                            seekOverlayMs = 0L
                                        }
                                    }
                                }
                                when (mode) {
                                    1 -> {
                                        if (isAudio || !isLeft) {
                                            gestureVol = (gestureVol + (-dy / height)).coerceIn(0f, 1f)
                                            applyVolumeFraction(gestureVol)
                                        } else {
                                            gestureBright =
                                                (gestureBright + (-dy / height)).coerceIn(0.01f, 1f)
                                            applyBrightnessFraction(gestureBright)
                                        }
                                    }
                                    2 -> {
                                        val dur = player.duration
                                        if (dur > 0 && dx != 0f) {
                                            val deltaMs = ((dx / width) * dur).toLong()
                                            if (deltaMs != 0L) {
                                                val target = (player.currentPosition + deltaMs)
                                                    .coerceIn(0L, dur)
                                                player.seekTo(target)
                                                positionMs = target
                                                gestureSeekAccum += deltaMs
                                                seekOverlayMs = gestureSeekAccum
                                            }
                                        }
                                    }
                                }
                            } else {
                                if (!dragged) {
                                    val now = System.currentTimeMillis()
                                    val isDouble = now - lastTapTime <= doubleTapTimeout &&
                                        abs(startX - lastTapX) < width * 0.3f
                                    if (isDouble) {
                                        if (startX < width / 2f) seekBy(-SKIP_MS) else seekBy(SKIP_MS)
                                        lastTapTime = 0L
                                    } else {
                                        lastTapTime = now
                                        lastTapX = startX
                                        controlsVisible = !controlsVisible
                                    }
                                } else {
                                    showControls()
                                    lastTapTime = 0L
                                }
                                break
                            }
                        }
                    }
                },
        )

        volumeOverlay?.let { pct ->
            TransientPercentOverlay(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                percent = pct,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 24.dp),
            )
        }
        if (!isAudio) {
            brightnessOverlay?.let { pct ->
                TransientPercentOverlay(
                    icon = Icons.Default.BrightnessHigh,
                    percent = pct,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp),
                )
            }
        }
        seekOverlayMs?.let { delta ->
            val sign = if (delta >= 0) "+" else "-"
            Text(
                "$sign${formatPlayerTime(abs(delta))}",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val displayPos = if (scrubbing) scrubPosition.toLong() else positionMs
            val dur = durationMs.coerceAtLeast(0L)
            val sliderMax = dur.toFloat().coerceAtLeast(1f)

            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 16.dp, vertical = if (isAudio) 20.dp else 12.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        formatPlayerTime(displayPos),
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.width(56.dp),
                    )
                    Slider(
                        value = displayPos.toFloat().coerceIn(0f, sliderMax),
                        onValueChange = { v ->
                            scrubbing = true
                            scrubPosition = v
                            showControls()
                        },
                        onValueChangeFinished = {
                            player.seekTo(scrubPosition.toLong())
                            positionMs = scrubPosition.toLong()
                            scrubbing = false
                        },
                        valueRange = 0f..sliderMax,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = VaultAccent,
                            activeTrackColor = VaultAccent,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                        ),
                    )
                    Text(
                        formatPlayerTime(dur),
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.width(56.dp),
                    )
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { seekBy(-SKIP_MS) }) {
                        Icon(
                            Icons.Default.Replay10,
                            contentDescription = "Seek back 10 seconds",
                            tint = Color.White,
                            modifier = Modifier.size(if (isAudio) 36.dp else 32.dp),
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    IconButton(
                        onClick = { togglePlay() },
                        modifier = Modifier
                            .size(if (isAudio) 72.dp else 56.dp)
                            .background(VaultAccent, CircleShape),
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = VaultOnAccent,
                            modifier = Modifier.size(if (isAudio) 40.dp else 32.dp),
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    IconButton(onClick = { seekBy(SKIP_MS) }) {
                        Icon(
                            Icons.Default.Forward10,
                            contentDescription = "Seek forward 10 seconds",
                            tint = Color.White,
                            modifier = Modifier.size(if (isAudio) 36.dp else 32.dp),
                        )
                    }
                }

                if (isAudio) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = VaultTextMuted,
                            modifier = Modifier.size(22.dp),
                        )
                        Slider(
                            value = volumeFraction,
                            onValueChange = { v ->
                                applyVolumeFraction(v)
                                showControls()
                            },
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = VaultAccent,
                                activeTrackColor = VaultAccent,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransientPercentOverlay(
    icon: ImageVector,
    percent: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = VaultAccent, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(6.dp))
        Text("$percent%", color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

internal fun formatPlayerTime(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) {
        "%d:%02d:%02d".format(h, m, s)
    } else {
        "%d:%02d".format(m, s)
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
