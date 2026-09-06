package app.vault.workspace.ui.viewer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.vault.workspace.crypto.KeyHierarchy
import app.vault.workspace.media.PlaybackPositionStore
import app.vault.workspace.media.SelectableTrack
import app.vault.workspace.media.applyTrackOverride
import app.vault.workspace.media.collectSelectableTracks
import app.vault.workspace.media.DecryptingPlayback
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
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.math.abs

private const val SKIP_MS = 10_000L
private const val CONTROLS_HIDE_MS = 3_000L
private const val OVERLAY_HIDE_MS = 900L
private const val TEMP_SPEED = 2f

internal val PLAYBACK_SPEEDS = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/**
 * Video resize modes for PlayerView.
 * Stretch uses FIXED_WIDTH as the closest edge-to-edge distort-ish fill Media3 exposes
 * without a custom TextureView matrix (true anamorphic stretch is Phase-later polish).
 */
internal enum class VideoFitMode(val label: String, val resizeMode: Int) {
    FIT("Fit", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL("Fill", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    STRETCH("Stretch", AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH),
    ZOOM("Zoom", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
}

internal enum class LoopMode { OFF, ONE, AB }

internal val SLEEP_TIMER_OPTIONS_MIN = intArrayOf(0, 5, 15, 30, 45, 60)

/**
 * Premium offline player overlay on Media3 ExoPlayer (decrypting path unchanged).
 * Video: immersive black + brightness (left vertical) / volume (right vertical) /
 * horizontal scrub / double-tap seek / long-press 2× / lock / speed / fit modes.
 * Audio: dark UI, large play/pause, seek + volume + speed + lock.
 *
 * Brightness writes the activity window [android.view.WindowManager.LayoutParams.screenBrightness]
 * (0.01f..1f) while playing. On dispose (leaving the player), the original window value is
 * restored — typically [android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE] (-1f)
 * so system brightness returns. Only the window attr is touched; global system setting is never written.
 */
@Composable
fun MediaPlayerScreen(
    vatFile: File,
    loadDek: suspend () -> ByteArray,
    mimeType: String,
    onPlaybackActive: (Boolean) -> Unit,
    onPlayerCreated: (DecryptingPlayback) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    itemId: String? = null,
    onControlsVisibilityChanged: (Boolean) -> Unit = {},
    onGesturesLockedChanged: (Boolean) -> Unit = {},
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
) {
    val isAudio = mimeType.startsWith("audio/", ignoreCase = true)
    val context = LocalContext.current
    var playback by remember { mutableStateOf<DecryptingPlayback?>(null) }
    val player = playback?.player
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(vatFile, itemId, mimeType) {
        try {
            // Heavy work on IO; ExoPlayer must be created on main (Media3 thread check).
            val isVideo = mimeType.startsWith("video/", ignoreCase = true)
            val session = if (isVideo) {
                val cacheFile = withContext(Dispatchers.IO) {
                    val dek = loadDek()
                    try {
                        PlayerFactory.prepareVideoCacheFile(
                            context = context,
                            vatFile = vatFile,
                            dek = dek,
                            mimeType = mimeType,
                            itemKey = itemId,
                        )
                    } finally {
                        KeyHierarchy.wipe(dek)
                    }
                }
                PlayerFactory.createDecryptingPlayer(
                    context = context,
                    vatFile = vatFile,
                    dek = ByteArray(0),
                    mimeType = mimeType,
                    itemKey = itemId,
                    preparedVideoFile = cacheFile,
                )
            } else {
                val dek = withContext(Dispatchers.IO) { loadDek() }
                try {
                    PlayerFactory.createDecryptingPlayer(
                        context = context,
                        vatFile = vatFile,
                        dek = dek,
                        mimeType = mimeType,
                        itemKey = itemId,
                    )
                } finally {
                    KeyHierarchy.wipe(dek)
                }
            }
            val p = session.player
            p.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    onPlaybackActive(isPlaying)
                }

                override fun onPlayerError(e: PlaybackException) {
                    Log.e(
                        "VaultPlayer",
                        "Playback error code=${e.errorCode} message=${e.message}",
                        e,
                    )
                    error = "This media format can't play on this device."
                }
            })
            p.playWhenReady = true
            playback = session
            onPlayerCreated(session)
        } catch (e: Exception) {
            error = e.message ?: "Playback failed"
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onPlaybackActive(false)
            playback?.release()
            playback = null
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
                    itemId = itemId,
                    onControlsVisibilityChanged = onControlsVisibilityChanged,
                    onGesturesLockedChanged = onGesturesLockedChanged,
                    onPrevious = onPrevious,
                    onNext = onNext,
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
    itemId: String?,
    onControlsVisibilityChanged: (Boolean) -> Unit,
    onGesturesLockedChanged: (Boolean) -> Unit,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val positionStore = remember(context) { PlaybackPositionStore(context) }

    val originalScreenBrightness = remember(activity) {
        activity?.window?.attributes?.screenBrightness
            ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    }

    DisposableEffect(activity) {
        onDispose {
            activity?.window?.let { win ->
                val lp = win.attributes
                lp.screenBrightness = originalScreenBrightness
                win.attributes = lp
            }
        }
    }

    // Immersive playback: hide phone status/nav bars (swipe edge to peek).
    DisposableEffect(isAudio, activity, view) {
        if (isAudio || activity == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(activity.window, view)
            val prior = controller.systemBarsBehavior
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            onDispose {
                controller.show(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = prior
            }
        }
    }

    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var controlsVisible by remember { mutableStateOf(true) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableFloatStateOf(0f) }
    /** True while ExoPlayer is settling after a user seek — ignore transient position 0. */
    var seekSettling by remember { mutableStateOf(false) }

    var volumeOverlay by remember { mutableStateOf<Int?>(null) }
    var brightnessOverlay by remember { mutableStateOf<Int?>(null) }
    var seekOverlayMs by remember { mutableStateOf<Long?>(null) }
    var lastVolumePct by remember { mutableIntStateOf(0) }
    var lastBrightnessPct by remember { mutableIntStateOf(0) }
    var lastSeekDelta by remember { mutableLongStateOf(0L) }
    var speedBoostActive by remember { mutableStateOf(false) }

    var gesturesLocked by remember { mutableStateOf(false) }
    var lockChromeVisible by remember { mutableStateOf(false) }
    var baseSpeed by remember { mutableFloatStateOf(1f) }
    var speedMenuOpen by remember { mutableStateOf(false) }
    var fitMode by remember { mutableStateOf(VideoFitMode.FIT) }
    var fitMenuOpen by remember { mutableStateOf(false) }
    var lockHintTick by remember { mutableIntStateOf(0) }

    var loopMode by remember { mutableStateOf(LoopMode.OFF) }
    var markerAMs by remember { mutableStateOf<Long?>(null) }
    var markerBMs by remember { mutableStateOf<Long?>(null) }
    var sleepRemainingMs by remember { mutableLongStateOf(0L) }
    var sleepUntilEpochMs by remember { mutableLongStateOf(0L) }
    var sleepMenuOpen by remember { mutableStateOf(false) }
    var audioMenuOpen by remember { mutableStateOf(false) }
    var subtitleMenuOpen by remember { mutableStateOf(false) }
    var audioTracks by remember { mutableStateOf<List<SelectableTrack>>(emptyList()) }
    var textTracks by remember { mutableStateOf<List<SelectableTrack>>(emptyList()) }
    var didResume by remember { mutableStateOf(false) }

    var brightness by remember {
        mutableFloatStateOf(
            activity?.window?.attributes?.screenBrightness
                ?.takeIf { it in 0.01f..1f }
                ?: 0.5f,
        )
    }
    var volumeFraction by remember {
        mutableFloatStateOf(
            run {
                val maxV = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxV
            },
        )
    }

    fun applyBaseSpeed(speed: Float) {
        val s = speed.coerceIn(0.25f, 2f)
        baseSpeed = s
        if (!speedBoostActive) {
            player.playbackParameters = PlaybackParameters(s)
        }
    }

    fun startTempBoost() {
        if (speedBoostActive) return
        speedBoostActive = true
        player.playbackParameters = PlaybackParameters(TEMP_SPEED)
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun endTempBoost() {
        if (!speedBoostActive) return
        speedBoostActive = false
        player.playbackParameters = PlaybackParameters(baseSpeed)
    }

    LaunchedEffect(player) {
        player.playbackParameters = PlaybackParameters(baseSpeed)
        var saveTick = 0
        while (isActive) {
            if (!scrubbing && !seekSettling) {
                val pos = player.currentPosition.coerceAtLeast(0L)
                // While a seek is outstanding ExoPlayer may briefly report 0 for video;
                // do not let that wipe the UI target or fight the real SeekMap land.
                positionMs = pos
            }
            val d = player.duration
            durationMs = if (d > 0) d else 0L
            isPlaying = player.isPlaying

            // A–B loop
            val a = markerAMs
            val b = markerBMs
            if (loopMode == LoopMode.AB && a != null && b != null && b > a) {
                val pos = player.currentPosition
                if (pos >= b) {
                    seekSettling = true
                    positionMs = a
                    player.seekTo(a)
                }
            }

            // Persist resume position ~every 2s; refresh tracks periodically
            saveTick++
            if (saveTick % 5 == 0) {
                val tracks = player.currentTracks
                audioTracks = collectSelectableTracks(tracks, C.TRACK_TYPE_AUDIO)
                textTracks = collectSelectableTracks(tracks, C.TRACK_TYPE_TEXT)
            }
            if (saveTick % 10 == 0 && itemId != null) {
                positionStore.savePositionMs(itemId, player.currentPosition, durationMs)
            }
            delay(200)
        }
    }

    // Resume once duration is known (once). Mark settling so poller won't snap to 0.
    LaunchedEffect(durationMs, itemId) {
        if (didResume || itemId == null || durationMs <= 0L) return@LaunchedEffect
        val saved = positionStore.getPositionMs(itemId)
        val resumeAt = PlaybackPositionStore.resumePosition(saved, durationMs)
        didResume = true
        if (resumeAt > 0L) {
            seekSettling = true
            positionMs = resumeAt
            player.seekTo(resumeAt)
        }
    }

    // Loop mode → ExoPlayer repeat
    LaunchedEffect(loopMode) {
        player.repeatMode = when (loopMode) {
            LoopMode.ONE -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    // Sleep timer countdown (deadline-based so effect doesn't restart every tick)
    LaunchedEffect(sleepUntilEpochMs) {
        if (sleepUntilEpochMs <= 0L) {
            sleepRemainingMs = 0L
            return@LaunchedEffect
        }
        while (isActive) {
            val left = (sleepUntilEpochMs - System.currentTimeMillis()).coerceAtLeast(0L)
            sleepRemainingMs = left
            if (left <= 0L) {
                player.pause()
                controlsVisible = true
                sleepUntilEpochMs = 0L
                break
            }
            delay(1_000)
        }
    }

    // Auto-next when track ends (unless looping); clear seekSettling when READY
    DisposableEffect(player, loopMode, onNext) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY ||
                    playbackState == Player.STATE_ENDED
                ) {
                    if (seekSettling) {
                        seekSettling = false
                        positionMs = player.currentPosition.coerceAtLeast(0L)
                    }
                }
                if (playbackState == Player.STATE_ENDED &&
                    loopMode == LoopMode.OFF
                ) {
                    val id = itemId
                    if (id != null) positionStore.clear(id)
                    onNext?.invoke()
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK ||
                    reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
                ) {
                    // Seek landed (may still buffer). Keep UI on the committed target
                    // until STATE_READY clears seekSettling. Never let a coerced
                    // discontinuity at 0 wipe a committed target > 0 (unseekable map).
                    if (seekSettling) {
                        val landed = newPosition.positionMs.coerceAtLeast(0L)
                        if (landed > 0L || positionMs <= 0L) {
                            positionMs = landed
                        }
                    }
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    DisposableEffect(itemId, player) {
        onDispose {
            val id = itemId ?: return@onDispose
            positionStore.savePositionMs(id, player.currentPosition, player.duration.coerceAtLeast(0L))
        }
    }

    LaunchedEffect(controlsVisible, isPlaying, gesturesLocked, speedMenuOpen, fitMenuOpen, sleepMenuOpen, audioMenuOpen, subtitleMenuOpen) {
        // Top title/chrome follows player controls only — stay hidden while locked.
        onControlsVisibilityChanged(controlsVisible && !gesturesLocked)
        if (controlsVisible && isPlaying && !gesturesLocked && !speedMenuOpen && !fitMenuOpen && !sleepMenuOpen && !audioMenuOpen && !subtitleMenuOpen) {
            delay(CONTROLS_HIDE_MS)
            controlsVisible = false
        }
    }

    LaunchedEffect(gesturesLocked) {
        onGesturesLockedChanged(gesturesLocked)
    }

    LaunchedEffect(volumeOverlay) {
        if (volumeOverlay != null) {
            lastVolumePct = volumeOverlay!!
            delay(OVERLAY_HIDE_MS)
            volumeOverlay = null
        }
    }
    LaunchedEffect(brightnessOverlay) {
        if (brightnessOverlay != null) {
            lastBrightnessPct = brightnessOverlay!!
            delay(OVERLAY_HIDE_MS)
            brightnessOverlay = null
        }
    }
    LaunchedEffect(seekOverlayMs) {
        if (seekOverlayMs != null) {
            lastSeekDelta = seekOverlayMs!!
            delay(700)
            seekOverlayMs = null
        }
    }
    LaunchedEffect(lockChromeVisible, gesturesLocked) {
        if (gesturesLocked && lockChromeVisible) {
            delay(2_500)
            lockChromeVisible = false
        }
    }
    LaunchedEffect(lockHintTick) {
        if (lockHintTick > 0) {
            delay(1_200)
            if (lockHintTick > 0) lockHintTick = 0
        }
    }

    fun showControls() {
        controlsVisible = true
    }

    fun togglePlay() {
        if (gesturesLocked) return
        if (player.isPlaying) player.pause() else player.play()
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        showControls()
    }


    fun commitSeek(targetMs: Long) {
        val dur = player.duration.coerceAtLeast(0L)
        val target = targetMs.coerceIn(0L, if (dur > 0) dur else Long.MAX_VALUE)
        seekSettling = true
        scrubbing = false
        positionMs = target
        player.seekTo(target)
    }

    fun seekBy(deltaMs: Long) {
        if (gesturesLocked) return
        val base = if (seekSettling) positionMs else player.currentPosition.coerceAtLeast(0L)
        val dur = player.duration.coerceAtLeast(0L)
        val target = (base + deltaMs)
            .coerceIn(0L, if (dur > 0) dur else Long.MAX_VALUE)
        commitSeek(target)
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

    fun toggleLock() {
        gesturesLocked = !gesturesLocked
        speedMenuOpen = false
        fitMenuOpen = false
        endTempBoost()
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        if (gesturesLocked) {
            controlsVisible = false
            lockChromeVisible = true
            lockHintTick = lockHintTick + 1
        } else {
            lockChromeVisible = false
            showControls()
        }
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
                        resizeMode = fitMode.resizeMode
                        keepScreenOn = true
                        subtitleView?.visibility = android.view.View.VISIBLE
                        this.player = player
                    }
                },
                update = { pv ->
                    pv.player = player
                    pv.keepScreenOn = true
                    pv.useController = false
                    pv.resizeMode = fitMode.resizeMode
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

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(isAudio, gesturesLocked) {
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    val height = size.height.toFloat().coerceAtLeast(1f)
                    val tapSlop = viewConfiguration.touchSlop
                    val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
                    val longPressTimeout = viewConfiguration.longPressTimeoutMillis.toLong()
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
                        var seekBasePos = 0L
                        var pendingSeekTarget = -1L
                        var gestureVol = volumeFraction
                        var gestureBright = brightness
                        var longPressArmed = !gesturesLocked
                        var boostOn = false
                        val downTime = System.currentTimeMillis()

                        while (true) {
                            val remaining = longPressTimeout - (System.currentTimeMillis() - downTime)
                            val event = if (longPressArmed && !boostOn && !dragged && remaining > 0L) {
                                withTimeoutOrNull(remaining) { awaitPointerEvent() }
                            } else {
                                awaitPointerEvent()
                            }

                            if (event == null) {
                                // Held still past long-press timeout → temporary 2×
                                if (longPressArmed && !boostOn && !dragged && !gesturesLocked) {
                                    boostOn = true
                                    startTempBoost()
                                }
                                continue
                            }

                            val change = event.changes.firstOrNull { it.id == down.id } ?: break

                            if (change.pressed) {
                                val dx = change.positionChange().x
                                val dy = change.positionChange().y
                                totalDx += dx
                                totalDy += dy
                                change.consume()

                                val adx = abs(totalDx)
                                val ady = abs(totalDy)
                                if (adx > tapSlop || ady > tapSlop) {
                                    dragged = true
                                    longPressArmed = false
                                    if (boostOn) {
                                        endTempBoost()
                                        boostOn = false
                                    }
                                }

                                if (gesturesLocked) continue
                                if (boostOn) continue

                                if (mode == 0 && dragged) {
                                    mode = if (ady >= adx) 1 else 2
                                    gestureVol = volumeFraction
                                    gestureBright = brightness
                                    if (mode == 2) {
                                        // Prefer UI position while a prior seek is settling —
                                        // player.currentPosition can read 0 mid-video-seek.
                                        seekBasePos = when {
                                            scrubbing -> scrubPosition.toLong()
                                            seekSettling -> positionMs
                                            else -> player.currentPosition.coerceAtLeast(0L)
                                        }
                                        gestureSeekAccum = 0L
                                        pendingSeekTarget = seekBasePos
                                        scrubbing = true
                                        scrubPosition = seekBasePos.toFloat()
                                        seekOverlayMs = 0L
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
                                        if (dur > 0) {
                                            // Absolute scrub from gesture start — avoids
                                            // currentPosition lag resetting seek to 0.
                                            val deltaMs = ((totalDx / width) * dur).toLong()
                                            val target = (seekBasePos + deltaMs).coerceIn(0L, dur)
                                            // UI-only scrub: do NOT seek until release.
                                            // Live seekTo during drag makes video flush to
                                            // keyframe 0 / Unseekable interim and restart.
                                            pendingSeekTarget = target
                                            positionMs = target
                                            scrubPosition = target.toFloat()
                                            gestureSeekAccum = deltaMs
                                            seekOverlayMs = deltaMs
                                        }
                                    }
                                }
                            } else {
                                if (boostOn) {
                                    endTempBoost()
                                    boostOn = false
                                    break
                                }
                                if (gesturesLocked) {
                                    if (!dragged) {
                                        lockChromeVisible = true
                                        lockHintTick = lockHintTick + 1
                                    }
                                    break
                                }
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
                                    if (mode == 2 && pendingSeekTarget >= 0L) {
                                        commitSeek(pendingSeekTarget)
                                    } else if (mode == 2) {
                                        scrubbing = false
                                    }
                                    showControls()
                                    lastTapTime = 0L
                                }
                                break
                            }
                        }
                        if (boostOn) endTempBoost()
                    }
                },
        )

        AnimatedVisibility(
            visible = volumeOverlay != null,
            enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                scaleIn(initialScale = 0.85f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 24.dp),
        ) {
            TransientPercentOverlay(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                percent = lastVolumePct,
            )
        }
        if (!isAudio) {
            AnimatedVisibility(
                visible = brightnessOverlay != null,
                enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                    scaleIn(initialScale = 0.85f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                exit = fadeOut() + scaleOut(targetScale = 0.9f),
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp),
            ) {
                TransientPercentOverlay(
                    icon = Icons.Default.BrightnessHigh,
                    percent = lastBrightnessPct,
                )
            }
        }
        AnimatedVisibility(
            visible = seekOverlayMs != null && !speedBoostActive,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.92f),
            modifier = Modifier.align(Alignment.Center),
        ) {
            val sign = if (lastSeekDelta >= 0) "+" else "-"
            OverlayChip(text = "$sign${formatPlayerTime(abs(lastSeekDelta))}")
        }
        AnimatedVisibility(
            visible = speedBoostActive,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp),
        ) {
            OverlayChip(text = "2×", large = true)
        }
        AnimatedVisibility(
            visible = gesturesLocked && lockChromeVisible && lockHintTick > 0,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            OverlayChip(text = "Locked — tap unlock")
        }

        if (gesturesLocked && lockChromeVisible) {
            IconButton(
                onClick = { toggleLock() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape),
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "Unlock gestures",
                    tint = VaultAccent,
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible && !gesturesLocked,
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
                    .padding(horizontal = 12.dp, vertical = if (isAudio) 20.dp else 12.dp),
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
                            positionMs = v.toLong()
                            showControls()
                        },
                        onValueChangeFinished = {
                            commitSeek(scrubPosition.toLong())
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
                    if (onPrevious != null) {
                        IconButton(onClick = {
                            onPrevious.invoke()
                            showControls()
                        }) {
                            Icon(
                                Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                tint = Color.White,
                                modifier = Modifier.size(if (isAudio) 36.dp else 32.dp),
                            )
                        }
                    }
                    IconButton(onClick = { seekBy(-SKIP_MS) }) {
                        Icon(
                            Icons.Default.Replay10,
                            contentDescription = "Seek back 10 seconds",
                            tint = Color.White,
                            modifier = Modifier.size(if (isAudio) 36.dp else 32.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
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
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { seekBy(SKIP_MS) }) {
                        Icon(
                            Icons.Default.Forward10,
                            contentDescription = "Seek forward 10 seconds",
                            tint = Color.White,
                            modifier = Modifier.size(if (isAudio) 36.dp else 32.dp),
                        )
                    }
                    if (onNext != null) {
                        IconButton(onClick = {
                            onNext.invoke()
                            showControls()
                        }) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = "Next",
                                tint = Color.White,
                                modifier = Modifier.size(if (isAudio) 36.dp else 32.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(if (isAudio) 10.dp else 6.dp))

                // Premium tool rail — evenly spaced, primary actions first
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        IconButton(onClick = {
                            speedMenuOpen = true
                            fitMenuOpen = false
                            sleepMenuOpen = false
                            audioMenuOpen = false
                            subtitleMenuOpen = false
                            showControls()
                        }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Speed, null, tint = VaultAccent, modifier = Modifier.size(20.dp))
                                Text(formatPlaybackSpeed(baseSpeed), color = Color.White, fontSize = 10.sp)
                            }
                        }
                        DropdownMenu(expanded = speedMenuOpen, onDismissRequest = { speedMenuOpen = false }) {
                            PLAYBACK_SPEEDS.forEach { speed ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            formatPlaybackSpeed(speed),
                                            fontWeight = if (speed == baseSpeed) FontWeight.Bold else FontWeight.Normal,
                                            color = if (speed == baseSpeed) VaultAccent else VaultText,
                                        )
                                    },
                                    onClick = {
                                        applyBaseSpeed(speed)
                                        speedMenuOpen = false
                                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                        showControls()
                                    },
                                )
                            }
                        }
                    }

                    if (!isAudio) {
                        Box {
                            IconButton(onClick = {
                                fitMenuOpen = true
                                speedMenuOpen = false
                                sleepMenuOpen = false
                                audioMenuOpen = false
                                subtitleMenuOpen = false
                                showControls()
                            }) {
                                Icon(Icons.Default.AspectRatio, "Fit", tint = VaultTextMuted)
                            }
                            DropdownMenu(expanded = fitMenuOpen, onDismissRequest = { fitMenuOpen = false }) {
                                VideoFitMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                mode.label,
                                                fontWeight = if (fitMode == mode) FontWeight.Bold else FontWeight.Normal,
                                                color = if (fitMode == mode) VaultAccent else VaultText,
                                            )
                                        },
                                        onClick = {
                                            fitMode = mode
                                            fitMenuOpen = false
                                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                            showControls()
                                        },
                                    )
                                }
                            }
                        }
                    }

                    IconButton(onClick = {
                        loopMode = when (loopMode) {
                            LoopMode.OFF -> LoopMode.ONE
                            LoopMode.ONE -> if (markerAMs != null && markerBMs != null) LoopMode.AB else LoopMode.OFF
                            LoopMode.AB -> LoopMode.OFF
                        }
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        showControls()
                    }) {
                        Icon(
                            if (loopMode == LoopMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                            "Loop",
                            tint = if (loopMode == LoopMode.OFF) VaultTextMuted else VaultAccent,
                        )
                    }

                    // A marker — tap to set, tap again to clear
                    TextButton(
                        onClick = {
                            if (markerAMs != null) {
                                markerAMs = null
                                markerBMs = null
                                if (loopMode == LoopMode.AB) loopMode = LoopMode.OFF
                            } else {
                                markerAMs = player.currentPosition.coerceAtLeast(0L)
                                val b = markerBMs
                                if (b != null && b <= markerAMs!!) markerBMs = null
                            }
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            showControls()
                        },
                    ) {
                        Text(
                            if (markerAMs != null) "A✓" else "A",
                            color = if (markerAMs != null) VaultAccent else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                    }
                    TextButton(
                        onClick = {
                            if (markerBMs != null) {
                                markerBMs = null
                                if (loopMode == LoopMode.AB) loopMode = LoopMode.OFF
                            } else {
                                val pos = player.currentPosition.coerceAtLeast(0L)
                                val a = markerAMs
                                if (a != null && pos > a) {
                                    markerBMs = pos
                                    loopMode = LoopMode.AB
                                } else if (a == null) {
                                    markerAMs = (pos - 1_000L).coerceAtLeast(0L)
                                    markerBMs = pos
                                    loopMode = LoopMode.AB
                                }
                            }
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            showControls()
                        },
                    ) {
                        Text(
                            if (markerBMs != null) "B✓" else "B",
                            color = if (markerBMs != null) VaultAccent else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                    }

                    Box {
                        IconButton(
                            onClick = {
                                audioMenuOpen = true
                                subtitleMenuOpen = false
                                speedMenuOpen = false
                                fitMenuOpen = false
                                sleepMenuOpen = false
                                showControls()
                            },
                            enabled = audioTracks.isNotEmpty(),
                        ) {
                            Icon(
                                Icons.Default.Headphones,
                                "Audio track",
                                tint = if (audioTracks.isNotEmpty()) VaultTextMuted else VaultTextMuted.copy(alpha = 0.35f),
                            )
                        }
                        DropdownMenu(expanded = audioMenuOpen, onDismissRequest = { audioMenuOpen = false }) {
                            if (audioTracks.isEmpty()) {
                                DropdownMenuItem(text = { Text("No audio tracks", color = VaultTextMuted) }, onClick = { audioMenuOpen = false })
                            } else {
                                audioTracks.forEach { track ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                track.label,
                                                fontWeight = if (track.selected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (track.selected) VaultAccent else VaultText,
                                            )
                                        },
                                        onClick = {
                                            applyTrackOverride(player, player.currentTracks, C.TRACK_TYPE_AUDIO, track)
                                            audioMenuOpen = false
                                            showControls()
                                        },
                                    )
                                }
                            }
                        }
                    }

                    Box {
                        IconButton(
                            onClick = {
                                subtitleMenuOpen = true
                                audioMenuOpen = false
                                speedMenuOpen = false
                                fitMenuOpen = false
                                sleepMenuOpen = false
                                showControls()
                            },
                        ) {
                            Icon(
                                Icons.Default.ClosedCaption,
                                "Subtitles",
                                tint = if (textTracks.any { it.selected }) VaultAccent else VaultTextMuted,
                            )
                        }
                        DropdownMenu(expanded = subtitleMenuOpen, onDismissRequest = { subtitleMenuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Off", color = VaultText) },
                                onClick = {
                                    applyTrackOverride(player, player.currentTracks, C.TRACK_TYPE_TEXT, null)
                                    subtitleMenuOpen = false
                                    showControls()
                                },
                            )
                            textTracks.forEach { track ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            track.label,
                                            fontWeight = if (track.selected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (track.selected) VaultAccent else VaultText,
                                        )
                                    },
                                    onClick = {
                                        applyTrackOverride(player, player.currentTracks, C.TRACK_TYPE_TEXT, track)
                                        subtitleMenuOpen = false
                                        showControls()
                                    },
                                )
                            }
                        }
                    }

                    Box {
                        IconButton(onClick = {
                            sleepMenuOpen = true
                            speedMenuOpen = false
                            fitMenuOpen = false
                            audioMenuOpen = false
                            subtitleMenuOpen = false
                            showControls()
                        }) {
                            Icon(
                                Icons.Default.Timer,
                                "Sleep",
                                tint = if (sleepRemainingMs > 0L) VaultAccent else VaultTextMuted,
                            )
                        }
                        DropdownMenu(expanded = sleepMenuOpen, onDismissRequest = { sleepMenuOpen = false }) {
                            SLEEP_TIMER_OPTIONS_MIN.forEach { mins ->
                                DropdownMenuItem(
                                    text = { Text(if (mins == 0) "Off" else "$mins min", color = VaultText) },
                                    onClick = {
                                        sleepUntilEpochMs = if (mins == 0) 0L else System.currentTimeMillis() + mins * 60_000L
                                        sleepMenuOpen = false
                                        showControls()
                                    },
                                )
                            }
                        }
                    }

                    IconButton(onClick = { toggleLock() }) {
                        Icon(Icons.Default.LockOpen, "Lock", tint = VaultTextMuted)
                    }
                }

                if (sleepRemainingMs > 0L || (markerAMs != null && markerBMs != null)) {
                    Text(
                        buildString {
                            if (sleepRemainingMs > 0L) {
                                append("Sleep ${formatPlayerTime(sleepRemainingMs)}")
                            }
                            if (markerAMs != null && markerBMs != null) {
                                if (isNotEmpty()) append(" · ")
                                append("A–B ${formatPlayerTime(markerAMs!!)}–${formatPlayerTime(markerBMs!!)}")
                                if (loopMode == LoopMode.AB) append(" looping")
                            }
                        },
                        color = VaultTextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun OverlayChip(
    text: String,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    Text(
        text,
        color = Color.White,
        fontSize = if (large) 28.sp else 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
            .padding(
                horizontal = if (large) 20.dp else 16.dp,
                vertical = if (large) 12.dp else 10.dp,
            ),
    )
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

internal fun formatPlaybackSpeed(speed: Float): String {
    val normalized = if (abs(speed - speed.toInt()) < 0.001f) {
        speed.toInt().toString()
    } else {
        (("%.2f").format(speed)).trimEnd('0').trimEnd('.')
    }
    return "${normalized}×"
}

internal fun nextVideoFitMode(current: VideoFitMode): VideoFitMode {
    val values = VideoFitMode.entries
    val idx = values.indexOf(current).coerceAtLeast(0)
    return values[(idx + 1) % values.size]
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
