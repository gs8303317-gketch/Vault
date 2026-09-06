package app.vault.workspace.ui.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vault.workspace.image.ImageCrop
import app.vault.workspace.ui.theme.VaultAccent
import kotlin.math.hypot

/**
 * Simple in-viewer crop UI: dimmed outside, draggable rect, corner handles.
 * [norm] is left/top/right/bottom in 0..1 relative to the displayed image box.
 */
@Composable
fun ImageCropOverlay(
    norm: ImageCrop.NormRect,
    onNormChange: (ImageCrop.NormRect) -> Unit,
    busy: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            val h = constraints.maxHeight.toFloat().coerceAtLeast(1f)
            val density = LocalDensity.current
            val handlePx = with(density) { 28.dp.toPx() }
            var dragMode by remember { mutableStateOf<DragMode?>(null) }

            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(w, h, busy) {
                        if (busy) return@pointerInput
                        detectDragGestures(
                            onDragStart = { start ->
                                val rect = normToPx(norm, w, h)
                                dragMode = hitTest(start, rect, handlePx)
                            },
                            onDragEnd = { dragMode = null },
                            onDragCancel = { dragMode = null },
                            onDrag = { change, amount ->
                                change.consume()
                                val mode = dragMode ?: return@detectDragGestures
                                val dx = amount.x / w
                                val dy = amount.y / h
                                val next = when (mode) {
                                    DragMode.MOVE -> ImageCrop.moveNormRect(norm, dx, dy)
                                    DragMode.TOP_LEFT ->
                                        ImageCrop.resizeFromCorner(norm, ImageCrop.Corner.TOP_LEFT, dx, dy)
                                    DragMode.TOP_RIGHT ->
                                        ImageCrop.resizeFromCorner(norm, ImageCrop.Corner.TOP_RIGHT, dx, dy)
                                    DragMode.BOTTOM_LEFT ->
                                        ImageCrop.resizeFromCorner(norm, ImageCrop.Corner.BOTTOM_LEFT, dx, dy)
                                    DragMode.BOTTOM_RIGHT ->
                                        ImageCrop.resizeFromCorner(norm, ImageCrop.Corner.BOTTOM_RIGHT, dx, dy)
                                }
                                onNormChange(next)
                            },
                        )
                    },
            ) {
                val rect = normToPx(norm, size.width, size.height)
                // Dim outside crop
                val dim = Color.Black.copy(alpha = 0.55f)
                drawRect(dim, Offset.Zero, Size(size.width, rect.top))
                drawRect(dim, Offset(0f, rect.bottom), Size(size.width, size.height - rect.bottom))
                drawRect(dim, Offset(0f, rect.top), Size(rect.left, rect.height))
                drawRect(
                    dim,
                    Offset(rect.right, rect.top),
                    Size(size.width - rect.right, rect.height),
                )
                // Crop border + rule of thirds
                drawRect(
                    VaultAccent,
                    Offset(rect.left, rect.top),
                    Size(rect.width, rect.height),
                    style = Stroke(width = 3f),
                )
                val thirdW = rect.width / 3f
                val thirdH = rect.height / 3f
                drawLine(
                    VaultAccent.copy(alpha = 0.5f),
                    Offset(rect.left + thirdW, rect.top),
                    Offset(rect.left + thirdW, rect.bottom),
                    strokeWidth = 1.5f,
                )
                drawLine(
                    VaultAccent.copy(alpha = 0.5f),
                    Offset(rect.left + 2 * thirdW, rect.top),
                    Offset(rect.left + 2 * thirdW, rect.bottom),
                    strokeWidth = 1.5f,
                )
                drawLine(
                    VaultAccent.copy(alpha = 0.5f),
                    Offset(rect.left, rect.top + thirdH),
                    Offset(rect.right, rect.top + thirdH),
                    strokeWidth = 1.5f,
                )
                drawLine(
                    VaultAccent.copy(alpha = 0.5f),
                    Offset(rect.left, rect.top + 2 * thirdH),
                    Offset(rect.right, rect.top + 2 * thirdH),
                    strokeWidth = 1.5f,
                )
                // Corner handles
                val hs = handlePx * 0.35f
                for (c in listOf(
                    Offset(rect.left, rect.top),
                    Offset(rect.right, rect.top),
                    Offset(rect.left, rect.bottom),
                    Offset(rect.right, rect.bottom),
                )) {
                    drawCircle(VaultAccent, radius = hs, center = c)
                    drawCircle(Color.White, radius = hs, center = c, style = Stroke(width = 2f))
                }
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.7f))
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel, enabled = !busy) {
                Text("Cancel", color = Color.White)
            }
            if (busy) {
                CircularProgressIndicator(
                    color = VaultAccent,
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp,
                )
            } else {
                Text("Drag corners to crop", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            }
            Button(
                onClick = onConfirm,
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = VaultAccent),
            ) {
                Text("Crop", color = Color.Black)
            }
        }
    }
}

private enum class DragMode {
    MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
}

private fun normToPx(norm: ImageCrop.NormRect, w: Float, h: Float): Rect =
    Rect(
        left = norm.left * w,
        top = norm.top * h,
        right = norm.right * w,
        bottom = norm.bottom * h,
    )

private fun hitTest(pos: Offset, rect: Rect, handlePx: Float): DragMode {
    fun near(c: Offset) = hypot((pos.x - c.x).toDouble(), (pos.y - c.y).toDouble()) <= handlePx
    return when {
        near(Offset(rect.left, rect.top)) -> DragMode.TOP_LEFT
        near(Offset(rect.right, rect.top)) -> DragMode.TOP_RIGHT
        near(Offset(rect.left, rect.bottom)) -> DragMode.BOTTOM_LEFT
        near(Offset(rect.right, rect.bottom)) -> DragMode.BOTTOM_RIGHT
        pos.x in rect.left..rect.right && pos.y in rect.top..rect.bottom -> DragMode.MOVE
        else -> DragMode.MOVE
    }
}
