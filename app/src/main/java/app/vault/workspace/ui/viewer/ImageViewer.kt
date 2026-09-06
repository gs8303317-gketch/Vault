package app.vault.workspace.ui.viewer

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import app.vault.workspace.ui.theme.VaultAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

@Composable
fun ImageViewer(
    loadBytes: suspend () -> ByteArray,
    modifier: Modifier = Modifier,
    onSingleTap: (() -> Unit)? = null,
) {
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(Unit) {
        try {
            val bytes = withContext(Dispatchers.IO) { loadBytes() }
            val opts = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            var sample = 1
            val maxSide = 4096
            while (opts.outWidth / sample > maxSide || opts.outHeight / sample > maxSide) {
                sample *= 2
            }
            val decode = BitmapFactory.Options().apply { inSampleSize = sample }
            bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decode)
            if (bitmap == null) error = "Cannot decode image"
        } catch (e: Exception) {
            error = e.message ?: "Failed to load image"
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            bitmap?.recycle()
            bitmap = null
        }
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
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y,
                        )
                        .pointerInput(onSingleTap) {
                            val touchSlop = viewConfiguration.touchSlop
                            val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
                            var lastTapTime = 0L

                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                var zoomAcc = 1f
                                var panAcc = Offset.Zero
                                var pastTouchSlop = false
                                var lockedToTransform = false

                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.changes.any { it.isConsumed }) break

                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()

                                    if (!pastTouchSlop) {
                                        zoomAcc *= zoomChange
                                        panAcc += panChange
                                        val centroidSize = event.calculateCentroidSize(useCurrent = false)
                                        val zoomMotion = abs(1f - zoomAcc) * centroidSize
                                        val panMotion = panAcc.getDistance()
                                        if (zoomMotion > touchSlop ||
                                            panMotion > touchSlop ||
                                            event.changes.count { it.pressed } > 1
                                        ) {
                                            pastTouchSlop = true
                                            lockedToTransform = true
                                        }
                                    }

                                    if (lockedToTransform) {
                                        val z = event.calculateZoom()
                                        val p = event.calculatePan()
                                        scale = (scale * z).coerceIn(1f, 5f)
                                        offset = if (scale <= 1.01f) {
                                            scale = 1f
                                            Offset.Zero
                                        } else {
                                            offset + p
                                        }
                                        event.changes.forEach {
                                            if (it.positionChanged()) it.consume()
                                        }
                                    }

                                    if (event.changes.none { it.pressed }) {
                                        if (!lockedToTransform) {
                                            val now = System.currentTimeMillis()
                                            if (now - lastTapTime <= doubleTapTimeout) {
                                                if (scale > 1.2f) {
                                                    scale = 1f
                                                    offset = Offset.Zero
                                                } else {
                                                    scale = 2.5f
                                                }
                                                lastTapTime = 0L
                                            } else {
                                                lastTapTime = now
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
}
