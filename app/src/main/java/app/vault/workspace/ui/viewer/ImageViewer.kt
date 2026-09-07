package app.vault.workspace.ui.viewer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Movie
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import app.vault.workspace.ui.nav.vaultSharedThumb
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.vault.workspace.image.ImageCrop
import app.vault.workspace.ui.theme.VaultAccent
import app.vault.workspace.ui.theme.VaultTextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Fit / Fill (crop) / Width — Phase 1 image viewer. */
enum class ImageFitMode(val label: String) {
    FIT("Fit"),
    FILL("Fill"),
    WIDTH("Width"),
}

/** Slideshow advance intervals (Phase 2). Default 3s. */
enum class SlideshowInterval(val ms: Long, val label: String) {
    S2(2_000L, "2s"),
    S3(3_000L, "3s"),
    S5(5_000L, "5s"),
    S10(10_000L, "10s");

    fun next(): SlideshowInterval = entries[(ordinal + 1) % entries.size]

    companion object {
        val Default: SlideshowInterval = S3

        fun fromMs(ms: Long): SlideshowInterval =
            entries.firstOrNull { it.ms == ms } ?: Default
    }
}

/**
 * Premium image viewer (Phase 1–3):
 * decrypt via [loadBytes], pinch zoom, double-tap toward point, clamped pan,
 * rotate/flip, fit modes, swipe prev/next at scale≈1, HUD resolution chip,
 * slideshow, GIF playback, keep-screen-on, in-vault crop (still images).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ImageViewer(
    loadBytes: suspend () -> ByteArray,
    modifier: Modifier = Modifier,
    mimeType: String? = null,
    itemId: String? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    onSingleTap: (() -> Unit)? = null,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    title: String? = null,
    controlsVisible: Boolean = true,
    slideshowPlaying: Boolean = false,
    onSlideshowPlayingChange: (Boolean) -> Unit = {},
    slideshowIntervalMs: Long = SlideshowInterval.Default.ms,
    onSlideshowIntervalMsChange: (Long) -> Unit = {},
    /** In-vault crop: normalized rect → repository crop/replace. Null disables crop button. */
    onCropConfirm: (suspend (left: Float, top: Float, right: Float, bottom: Float) -> Result<Unit>)? = null,
    /** Tool-rail / crop interactions — parent resets chrome auto-hide timer. */
    onControlsInteraction: () -> Unit = {},
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var gifMovie by remember { mutableStateOf<Movie?>(null) }
    var gifBytes by remember { mutableStateOf<ByteArray?>(null) }
    var gifCanvasBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var intrinsicW by remember { mutableIntStateOf(0) }
    var intrinsicH by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var rotationDeg by remember { mutableIntStateOf(0) }
    var flipH by remember { mutableStateOf(false) }
    var flipV by remember { mutableStateOf(false) }
    var fitMode by remember { mutableStateOf(ImageFitMode.FIT) }
    var hudVisible by remember { mutableStateOf(false) }
    val view = LocalView.current
    val interval = SlideshowInterval.fromMs(slideshowIntervalMs)
    val currentOnNext by rememberUpdatedState(onNext)
    val currentSlideshowPlaying by rememberUpdatedState(slideshowPlaying)
    val currentOnSlideshowPlayingChange by rememberUpdatedState(onSlideshowPlayingChange)
    val currentOnControlsInteraction by rememberUpdatedState(onControlsInteraction)
    val currentIntervalMs by rememberUpdatedState(interval.ms)
    val scope = rememberCoroutineScope()
    var reloadEpoch by remember { mutableIntStateOf(0) }
    var cropping by remember { mutableStateOf(false) }
    var cropBusy by remember { mutableStateOf(false) }
    var cropNorm by remember { mutableStateOf(ImageCrop.defaultNormRect()) }

    // Keep screen on while immersive image viewer is showing; clear on dispose.
    DisposableEffect(Unit) {
        val window = view.context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(mimeType, reloadEpoch) {
        // Reset display state on (re)load / post-crop
        error = null
        gifMovie = null
        gifBytes?.fill(0)
        gifBytes = null
        gifCanvasBitmap?.let { if (!it.isRecycled) it.recycle() }
        gifCanvasBitmap = null
        bitmap?.let { if (!it.isRecycled) it.recycle() }
        bitmap = null
        try {
            data class Loaded(
                val bitmap: Bitmap?,
                val movie: Movie?,
                val gifBytes: ByteArray?,
                val w: Int,
                val h: Int,
            )
            val loaded = withContext(Dispatchers.IO) {
                val bytes = loadBytes()
                val isGif = mimeType.equals("image/gif", ignoreCase = true)
                if (isGif) {
                    val movie = Movie.decodeByteArray(bytes, 0, bytes.size)
                    if (movie != null && movie.width() > 0 && movie.height() > 0 && movie.duration() > 0) {
                        // Keep plaintext only while Movie is alive; wipe on dispose.
                        return@withContext Loaded(
                            bitmap = null,
                            movie = movie,
                            gifBytes = bytes,
                            w = movie.width(),
                            h = movie.height(),
                        )
                    }
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                val maxSide = 4096
                while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) {
                    sample *= 2
                }
                val decode = BitmapFactory.Options().apply { inSampleSize = sample }
                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decode)
                bytes.fill(0)
                Loaded(
                    bitmap = decoded,
                    movie = null,
                    gifBytes = null,
                    w = bounds.outWidth,
                    h = bounds.outHeight,
                )
            }
            intrinsicW = loaded.w
            intrinsicH = loaded.h
            if (loaded.movie != null) {
                gifMovie = loaded.movie
                gifBytes = loaded.gifBytes
                // Placeholder so UI leaves the loading spinner; GIF draws via AndroidView.
                bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                hudVisible = true
            } else {
                bitmap = loaded.bitmap
                if (bitmap == null) {
                    error = "Cannot decode image"
                } else {
                    hudVisible = true
                }
            }
        } catch (e: Exception) {
            error = e.message ?: "Failed to load image"
        }
    }

    // Slideshow: advance via onNext when available; stop at end if null.
    // Keyed only on playing/interval — onNext lambdas are unstable across recomposition.
    LaunchedEffect(slideshowPlaying, slideshowIntervalMs) {
        if (!slideshowPlaying) return@LaunchedEffect
        while (isActive) {
            delay(currentIntervalMs)
            if (!currentSlideshowPlaying) break
            val next = currentOnNext
            if (next != null) {
                next.invoke()
                // New ImageViewer instance continues the session; avoid double-advance.
                return@LaunchedEffect
            } else {
                currentOnSlideshowPlayingChange(false)
                break
            }
        }
    }

    // Show resolution briefly on open; also while chrome is visible
    LaunchedEffect(hudVisible, controlsVisible, intrinsicW) {
        if (intrinsicW > 0 && (hudVisible || controlsVisible)) {
            if (hudVisible && !controlsVisible) {
                delay(2200)
                hudVisible = false
            }
        }
    }
    LaunchedEffect(controlsVisible) {
        if (controlsVisible && intrinsicW > 0) hudVisible = true
    }

    DisposableEffect(Unit) {
        onDispose {
            val staticBmp = bitmap
            val canvasBmp = gifCanvasBitmap
            // Avoid double-recycle when static display shares the GIF canvas bitmap.
            if (staticBmp != null && staticBmp !== canvasBmp && !staticBmp.isRecycled) {
                staticBmp.recycle()
            }
            if (canvasBmp != null && !canvasBmp.isRecycled) {
                canvasBmp.recycle()
            }
            bitmap = null
            gifCanvasBitmap = null
            gifMovie = null
            gifBytes?.fill(0)
            gifBytes = null
        }
    }

    fun haptic() {
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    fun pauseSlideshow() {
        if (currentSlideshowPlaying) currentOnSlideshowPlayingChange(false)
    }

    fun keepChrome() {
        currentOnControlsInteraction()
    }

    fun resetTransform(keepFit: Boolean = true) {
        scale = 1f
        offset = Offset.Zero
        rotationDeg = 0
        flipH = false
        flipV = false
        if (!keepFit) fitMode = ImageFitMode.FIT
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        when {
            error != null -> Text(error!!, color = Color.White)
            bitmap == null -> CircularProgressIndicator(color = VaultAccent)
            else -> {
                val bmp = bitmap!!
                BoxWithConstraints(
                    Modifier
                        .fillMaxSize()
                        .clipToBounds(),
                    contentAlignment = Alignment.Center,
                ) {
                    val containerW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                    val containerH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                    val density = LocalDensity.current
                    val srcW = (if (intrinsicW > 0) intrinsicW else bmp.width).toFloat()
                    val srcH = (if (intrinsicH > 0) intrinsicH else bmp.height).toFloat()
                    val display = remember(srcW, srcH, containerW, containerH, fitMode, rotationDeg) {
                        imageDisplaySize(srcW, srcH, containerW, containerH, fitMode, rotationDeg)
                    }
                    val dispW = display.first
                    val dispH = display.second
                    val imageRectInOverlay = remember(dispW, dispH, containerW, containerH) {
                        val left = ((containerW - dispW) / 2f).coerceAtLeast(0f)
                        val top = ((containerH - dispH) / 2f).coerceAtLeast(0f)
                        Rect(left, top, left + dispW, top + dispH)
                    }

                    fun clamp(raw: Offset, s: Float): Offset =
                        clampImageOffset(raw, s, dispW, dispH, containerW, containerH)

                    val sharedMod = if (itemId != null) {
                        Modifier.vaultSharedThumb(
                            sharedTransitionScope,
                            animatedVisibilityScope,
                            itemId,
                            useBounds = false,
                        )
                    } else {
                        Modifier
                    }
                    val contentModifier = Modifier
                        .size(
                            width = with(density) { dispW.toDp() },
                            height = with(density) { dispH.toDp() },
                        )
                        .then(sharedMod)
                        .graphicsLayer(
                            scaleX = scale * if (flipH) -1f else 1f,
                            scaleY = scale * if (flipV) -1f else 1f,
                            rotationZ = rotationDeg.toFloat(),
                            translationX = offset.x,
                            translationY = offset.y,
                        )
                        .pointerInput(
                                onSingleTap,
                                onPrevious,
                                onNext,
                                dispW,
                                dispH,
                                containerW,
                                containerH,
                                slideshowPlaying,
                            ) {
                                val touchSlop = viewConfiguration.touchSlop
                                val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
                                val swipeThreshold = containerW * 0.18f
                                var lastTapTime = 0L
                                var lastTapPos = Offset.Zero

                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    var zoomAcc = 1f
                                    var panAcc = Offset.Zero
                                    var pastTouchSlop = false
                                    var lockedToTransform = false
                                    var swipeCandidate = false
                                    val startScale = scale
                                    val center = Offset(size.width / 2f, size.height / 2f)
                                    // Leave system gesture-nav edge zone alone at identity zoom
                                    // so predictive / edge-back is not stolen by gallery swipe.
                                    val edgeBackZonePx = with(density) { 28.dp.toPx() }
                                    val startedInEdgeBackZone =
                                        down.position.x < edgeBackZonePx ||
                                            down.position.x > size.width - edgeBackZonePx

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.changes.any { it.isConsumed }) break

                                        val zoomChange = event.calculateZoom()
                                        val panChange = event.calculatePan()
                                        val wasPastSlop = pastTouchSlop

                                        if (!pastTouchSlop) {
                                            zoomAcc *= zoomChange
                                            panAcc += panChange
                                            val centroidSize =
                                                event.calculateCentroidSize(useCurrent = false)
                                            val zoomMotion = abs(1f - zoomAcc) * centroidSize
                                            val panMotion = panAcc.getDistance()
                                            if (zoomMotion > touchSlop ||
                                                panMotion > touchSlop ||
                                                event.changes.count { it.pressed } > 1
                                            ) {
                                                pastTouchSlop = true
                                                val maxPanX = maxImagePan(
                                                    startScale,
                                                    dispW,
                                                    containerW,
                                                )
                                                val maxPanY = maxImagePan(
                                                    startScale,
                                                    dispH,
                                                    containerH,
                                                )
                                                val canPanContent =
                                                    startScale > 1.02f || maxPanX > 1f || maxPanY > 1f
                                                val multi = event.changes.count { it.pressed } > 1
                                                val mostlyZoom = zoomMotion > panMotion
                                                if (multi || mostlyZoom || canPanContent) {
                                                    lockedToTransform = true
                                                    pauseSlideshow()
                                                } else if (!startedInEdgeBackZone) {
                                                    swipeCandidate = true
                                                }
                                                // Else: edge-origin drag at zoom≈1 — do not
                                                // consume; let system back / gesture nav win.
                                            }
                                        }

                                        if (lockedToTransform) {
                                            val z = event.calculateZoom()
                                            val p = event.calculatePan()
                                            val centroid = event.calculateCentroid(useCurrent = true)
                                            val oldScale = scale
                                            val newScale = (oldScale * z).coerceIn(1f, 5f)
                                            if (newScale <= 1.01f) {
                                                scale = 1f
                                                offset = Offset.Zero
                                            } else {
                                                // Zoom toward centroid
                                                val tapRel = centroid - center
                                                val zoomed = if (abs(z - 1f) > 0.001f) {
                                                    offset * (newScale / oldScale) +
                                                        tapRel * (1f - newScale / oldScale)
                                                } else {
                                                    offset
                                                }
                                                scale = newScale
                                                offset = clamp(zoomed + p, newScale)
                                            }
                                            event.changes.forEach {
                                                if (it.positionChanged()) it.consume()
                                            }
                                        } else if (swipeCandidate && wasPastSlop) {
                                            panAcc += panChange
                                            event.changes.forEach {
                                                if (it.positionChanged()) it.consume()
                                            }
                                        }

                                        if (event.changes.none { it.pressed }) {
                                            if (swipeCandidate && scale <= 1.02f) {
                                                val dx = panAcc.x
                                                val dy = panAcc.y
                                                if (abs(dx) > swipeThreshold &&
                                                    abs(dx) > abs(dy) * 1.2f
                                                ) {
                                                    if (dx > 0f) {
                                                        onPrevious?.invoke()
                                                    } else {
                                                        onNext?.invoke()
                                                    }
                                                    haptic()
                                                } else if (!pastTouchSlop) {
                                                    // fall through to tap handling below
                                                }
                                            }
                                            if (!lockedToTransform && !swipeCandidate) {
                                                val now = System.currentTimeMillis()
                                                val tapPos = down.position
                                                if (now - lastTapTime <= doubleTapTimeout &&
                                                    (tapPos - lastTapPos).getDistance() < touchSlop * 4
                                                ) {
                                                    // Double-tap zoom toward tap point — pause slideshow
                                                    pauseSlideshow()
                                                    if (scale > 1.2f) {
                                                        scale = 1f
                                                        offset = Offset.Zero
                                                    } else {
                                                        val target = 2.5f
                                                        val tapRel = tapPos - center
                                                        val newOff =
                                                            offset * (target / scale) +
                                                                tapRel * (1f - target / scale)
                                                        scale = target
                                                        offset = clamp(newOff, target)
                                                    }
                                                    lastTapTime = 0L
                                                    haptic()
                                                } else {
                                                    lastTapTime = now
                                                    lastTapPos = tapPos
                                                    onSingleTap?.invoke()
                                                }
                                            } else if (swipeCandidate &&
                                                abs(panAcc.x) <= swipeThreshold
                                            ) {
                                                // Weak horizontal drag — treat as tap to toggle chrome
                                                if (panAcc.getDistance() < touchSlop * 2) {
                                                    onSingleTap?.invoke()
                                                }
                                            }
                                            break
                                        }
                                    }
                                }
                            }

                    val movie = gifMovie
                    if (movie != null) {
                        GifMovieAndroidView(
                            movie = movie,
                            contentDescription = title ?: "GIF",
                            modifier = contentModifier,
                        )
                    } else {
                        val imageBitmap = remember(bmp) { bmp.asImageBitmap() }
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = title ?: "Image",
                            contentScale = ContentScale.FillBounds,
                            modifier = contentModifier,
                        )
                    }

                    if (cropping) {
                        ImageCropOverlay(
                            norm = cropNorm,
                            onNormChange = { cropNorm = it },
                            busy = cropBusy,
                            imageRectInOverlay = imageRectInOverlay,
                            onCancel = {
                                if (!cropBusy) cropping = false
                            },
                            onConfirm = {
                                val confirm = onCropConfirm ?: return@ImageCropOverlay
                                if (cropBusy) return@ImageCropOverlay
                                cropBusy = true
                                keepChrome()
                                scope.launch {
                                    val n = ImageCrop.clampNormRect(
                                        cropNorm.left, cropNorm.top, cropNorm.right, cropNorm.bottom,
                                    )
                                    val result = try {
                                        confirm(n.left, n.top, n.right, n.bottom)
                                    } catch (e: Exception) {
                                        Result.failure(e)
                                    }
                                    cropBusy = false
                                    result.fold(
                                        onSuccess = {
                                            cropping = false
                                            resetTransform(keepFit = false)
                                            reloadEpoch++
                                            haptic()
                                            Toast.makeText(view.context, "Saved", Toast.LENGTH_SHORT).show()
                                        },
                                        onFailure = { e ->
                                            Log.e("VaultImage", "crop failed", e)
                                            Toast.makeText(
                                                view.context,
                                                e.message ?: "Crop failed",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        },
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        // HUD resolution chip
        val showHud = bitmap != null && intrinsicW > 0 && (hudVisible || controlsVisible)
        AnimatedVisibility(
            visible = showHud,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 72.dp),
        ) {
            Text(
                "${intrinsicW}×$intrinsicH",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }

        // Premium bottom tool rail — single scrollable row (no IconButton clip / truncation)
        AnimatedVisibility(
            visible = controlsVisible && bitmap != null && !cropping,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .navigationBarsPadding()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ImageToolRailItem(
                    icon = Icons.Default.Rotate90DegreesCw,
                    label = "Rotate",
                    tint = VaultAccent,
                    onClick = {
                        keepChrome()
                        rotationDeg = (rotationDeg + 90) % 360
                        offset = Offset.Zero
                        scale = 1f
                        haptic()
                    },
                )
                ImageToolRailItem(
                    icon = Icons.Default.Flip,
                    label = "Flip H",
                    tint = if (flipH) VaultAccent else VaultTextMuted,
                    onClick = {
                        keepChrome()
                        flipH = !flipH
                        haptic()
                    },
                )
                ImageToolRailItem(
                    icon = Icons.Default.SwapVert,
                    label = "Flip V",
                    tint = if (flipV) VaultAccent else VaultTextMuted,
                    onClick = {
                        keepChrome()
                        flipV = !flipV
                        haptic()
                    },
                )
                ImageToolRailItem(
                    icon = Icons.Default.AspectRatio,
                    label = fitMode.label,
                    tint = VaultAccent,
                    onClick = {
                        keepChrome()
                        fitMode = when (fitMode) {
                            ImageFitMode.FIT -> ImageFitMode.FILL
                            ImageFitMode.FILL -> ImageFitMode.WIDTH
                            ImageFitMode.WIDTH -> ImageFitMode.FIT
                        }
                        scale = 1f
                        offset = Offset.Zero
                        haptic()
                    },
                )
                ImageToolRailItem(
                    icon = Icons.Default.RestartAlt,
                    label = "Reset",
                    tint = VaultTextMuted,
                    onClick = {
                        keepChrome()
                        resetTransform(keepFit = false)
                        haptic()
                    },
                )
                ImageToolRailItem(
                    icon = if (slideshowPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    label = if (slideshowPlaying) "Pause" else "Slide",
                    tint = if (slideshowPlaying) VaultAccent else VaultTextMuted,
                    onClick = {
                        keepChrome()
                        onSlideshowPlayingChange(!slideshowPlaying)
                        haptic()
                    },
                )
                ImageToolRailItem(
                    icon = Icons.Default.Timer,
                    label = interval.label,
                    tint = VaultAccent,
                    onClick = {
                        keepChrome()
                        val next = interval.next()
                        onSlideshowIntervalMsChange(next.ms)
                        haptic()
                    },
                )
                ImageToolRailItem(
                    icon = Icons.Default.Crop,
                    label = "Crop",
                    tint = if (onCropConfirm != null) VaultAccent else VaultTextMuted,
                    enabled = onCropConfirm != null,
                    onClick = {
                        keepChrome()
                        if (ImageCrop.isGifMime(mimeType) || gifMovie != null) {
                            Toast.makeText(
                                view.context,
                                "Crop not available for GIFs",
                                Toast.LENGTH_SHORT,
                            ).show()
                            Log.i("VaultImage", "crop skipped: GIF")
                        } else if (onCropConfirm != null) {
                            pauseSlideshow()
                            resetTransform(keepFit = false)
                            cropNorm = ImageCrop.defaultNormRect()
                            cropping = true
                            haptic()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ImageToolRailItem(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Column(
        modifier = Modifier
            .widthIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (enabled) tint else VaultTextMuted.copy(alpha = 0.4f),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/**
 * Draw animated GIF via [Movie] on a dedicated View so Compose does not
 * recompose the whole viewer every frame (frameEpoch thrash).
 */
@Composable
private fun GifMovieAndroidView(
    movie: Movie,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { ctx ->
            object : View(ctx) {
                private val frameBmp = Bitmap.createBitmap(
                    movie.width().coerceAtLeast(1),
                    movie.height().coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888,
                )
                private val frameCanvas = Canvas(frameBmp)
                private val startMs = SystemClock.uptimeMillis()
                private val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
                private val dest = android.graphics.Rect()

                init {
                    contentDescription.let { this.contentDescription = it }
                }

                override fun onDraw(canvas: android.graphics.Canvas) {
                    val dur = movie.duration().coerceAtLeast(1)
                    val t = ((SystemClock.uptimeMillis() - startMs) % dur).toInt()
                    movie.setTime(t)
                    frameBmp.eraseColor(android.graphics.Color.TRANSPARENT)
                    movie.draw(frameCanvas, 0f, 0f)
                    dest.set(0, 0, width, height)
                    canvas.drawBitmap(frameBmp, null, dest, paint)
                    postInvalidateOnAnimation()
                }

                override fun onDetachedFromWindow() {
                    super.onDetachedFromWindow()
                    if (!frameBmp.isRecycled) frameBmp.recycle()
                }
            }
        },
        modifier = modifier,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Display size of the bitmap inside the container for the given fit mode + rotation. */
internal fun imageDisplaySize(
    srcW: Float,
    srcH: Float,
    containerW: Float,
    containerH: Float,
    fitMode: ImageFitMode,
    rotationDeg: Int,
): Pair<Float, Float> {
    val swapped = (rotationDeg % 180 + 180) % 180 == 90
    val w = if (swapped) srcH else srcW
    val h = if (swapped) srcW else srcH
    val cw = containerW.coerceAtLeast(1f)
    val ch = containerH.coerceAtLeast(1f)
    val (dw, dh) = when (fitMode) {
        ImageFitMode.FIT -> {
            val s = min(cw / w, ch / h)
            (w * s) to (h * s)
        }
        ImageFitMode.FILL -> {
            val s = max(cw / w, ch / h)
            (w * s) to (h * s)
        }
        ImageFitMode.WIDTH -> {
            val s = cw / w
            (w * s) to (h * s)
        }
    }
    // Image layout size before rotationZ: if we swapped for fitting math, the
    // composable still lays out pre-rotation bitmap aspect — swap back so that
    // after rotationZ the visual matches [dw,dh].
    return if (swapped) dh to dw else dw to dh
}

internal fun maxImagePan(scale: Float, contentSide: Float, containerSide: Float): Float {
    if (scale <= 1.02f) {
        // At identity zoom, Fill/Width may still overflow the viewport
        return max(0f, (contentSide - containerSide) / 2f)
    }
    return max(0f, (contentSide * scale - containerSide) / 2f)
}

internal fun clampImageOffset(
    raw: Offset,
    scale: Float,
    contentW: Float,
    contentH: Float,
    containerW: Float,
    containerH: Float,
): Offset {
    val maxX = maxImagePan(scale, contentW, containerW)
    val maxY = maxImagePan(scale, contentH, containerH)
    if (maxX <= 0f && maxY <= 0f) return Offset.Zero
    return Offset(
        raw.x.coerceIn(-maxX, maxX),
        raw.y.coerceIn(-maxY, maxY),
    )
}

/** Double-tap zoom offset that keeps [tapRel] (centroid relative to center) fixed. */
internal fun zoomTowardOffset(
    currentOffset: Offset,
    currentScale: Float,
    targetScale: Float,
    tapRel: Offset,
): Offset {
    if (currentScale <= 0.001f) return Offset.Zero
    return currentOffset * (targetScale / currentScale) +
        tapRel * (1f - targetScale / currentScale)
}
