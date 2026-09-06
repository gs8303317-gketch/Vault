package app.vault.workspace.ui.viewer

import android.graphics.BitmapFactory
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import app.vault.workspace.ui.theme.VaultAccent
import app.vault.workspace.ui.theme.VaultTextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

/**
 * Premium Phase 1 image viewer (Aves/Simple/Fossify-style chrome):
 * decrypt via [loadBytes], pinch zoom, double-tap toward point, clamped pan,
 * rotate/flip, fit modes, swipe prev/next at scale≈1, HUD resolution chip.
 */
@Composable
fun ImageViewer(
    loadBytes: suspend () -> ByteArray,
    modifier: Modifier = Modifier,
    onSingleTap: (() -> Unit)? = null,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    title: String? = null,
    controlsVisible: Boolean = true,
) {
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
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

    LaunchedEffect(Unit) {
        try {
            val bytes = withContext(Dispatchers.IO) { loadBytes() }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            intrinsicW = bounds.outWidth
            intrinsicH = bounds.outHeight
            var sample = 1
            val maxSide = 4096
            while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) {
                sample *= 2
            }
            val decode = BitmapFactory.Options().apply { inSampleSize = sample }
            bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decode)
            if (bitmap == null) {
                error = "Cannot decode image"
            } else {
                hudVisible = true
            }
        } catch (e: Exception) {
            error = e.message ?: "Failed to load image"
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
            bitmap?.recycle()
            bitmap = null
        }
    }

    fun haptic() {
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
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
                    val srcW = bmp.width.toFloat()
                    val srcH = bmp.height.toFloat()
                    val display = remember(srcW, srcH, containerW, containerH, fitMode, rotationDeg) {
                        imageDisplaySize(srcW, srcH, containerW, containerH, fitMode, rotationDeg)
                    }
                    val dispW = display.first
                    val dispH = display.second

                    fun clamp(raw: Offset, s: Float): Offset =
                        clampImageOffset(raw, s, dispW, dispH, containerW, containerH)

                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = title ?: "Image",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .size(
                                width = with(density) { dispW.toDp() },
                                height = with(density) { dispH.toDp() },
                            )
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
                                                } else {
                                                    swipeCandidate = true
                                                }
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
                                                    // Double-tap zoom toward tap point
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
                            },
                    )
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

        // Premium bottom tool rail
        AnimatedVisibility(
            visible = controlsVisible && bitmap != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        rotationDeg = (rotationDeg + 90) % 360
                        offset = Offset.Zero
                        scale = 1f
                        haptic()
                    }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Rotate90DegreesCw,
                                contentDescription = "Rotate",
                                tint = VaultAccent,
                                modifier = Modifier.size(22.dp),
                            )
                            Text("Rotate", color = Color.White, fontSize = 10.sp)
                        }
                    }
                    IconButton(onClick = {
                        flipH = !flipH
                        haptic()
                    }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Flip,
                                contentDescription = "Flip horizontal",
                                tint = if (flipH) VaultAccent else VaultTextMuted,
                                modifier = Modifier.size(22.dp),
                            )
                            Text("Flip H", color = Color.White, fontSize = 10.sp)
                        }
                    }
                    IconButton(onClick = {
                        flipV = !flipV
                        haptic()
                    }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.SwapVert,
                                contentDescription = "Flip vertical",
                                tint = if (flipV) VaultAccent else VaultTextMuted,
                                modifier = Modifier.size(22.dp),
                            )
                            Text("Flip V", color = Color.White, fontSize = 10.sp)
                        }
                    }
                    IconButton(onClick = {
                        fitMode = when (fitMode) {
                            ImageFitMode.FIT -> ImageFitMode.FILL
                            ImageFitMode.FILL -> ImageFitMode.WIDTH
                            ImageFitMode.WIDTH -> ImageFitMode.FIT
                        }
                        scale = 1f
                        offset = Offset.Zero
                        haptic()
                    }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.AspectRatio,
                                contentDescription = "Fit mode",
                                tint = VaultAccent,
                                modifier = Modifier.size(22.dp),
                            )
                            Text(fitMode.label, color = Color.White, fontSize = 10.sp)
                        }
                    }
                    IconButton(onClick = {
                        resetTransform(keepFit = false)
                        haptic()
                    }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.RestartAlt,
                                contentDescription = "Reset",
                                tint = VaultTextMuted,
                                modifier = Modifier.size(22.dp),
                            )
                            Text("Reset", color = Color.White, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
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
