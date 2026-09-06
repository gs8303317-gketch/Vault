package app.vault.workspace.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import app.vault.workspace.auth.LockRules
import app.vault.workspace.ui.theme.VaultAccent
import app.vault.workspace.ui.theme.VaultDanger
import app.vault.workspace.ui.theme.VaultSurface
import kotlin.math.hypot
import kotlin.math.min as minF

/**
 * Android-style 3×3 pattern lock. On finger-up, invokes [onPatternComplete] with
 * encoded secret (cell indices 0–8) when at least [LockRules.PATTERN_MIN_POINTS] cells.
 */
@Composable
fun PatternLock(
    enabled: Boolean,
    onPatternComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
    errorFlash: Boolean = false,
    compact: Boolean = false,
    maxSize: Dp = if (compact) 220.dp else 300.dp,
) {
    val selected = remember { mutableStateListOf<Int>() }
    var finger by remember { mutableStateOf<Offset?>(null) }
    var cellCenters by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val hitRadiusPx = with(LocalDensity.current) { 28.dp.toPx() }

    fun hitTest(pos: Offset): Int? {
        if (cellCenters.size != 9) return null
        var best: Int? = null
        var bestDist = hitRadiusPx
        cellCenters.forEachIndexed { i, c ->
            val d = hypot((pos.x - c.x).toDouble(), (pos.y - c.y).toDouble()).toFloat()
            if (d <= bestDist) {
                bestDist = d
                best = i
            }
        }
        return best
    }

    fun addCell(index: Int) {
        if (index in selected) return
        if (selected.isNotEmpty()) {
            val last = selected.last()
            val mid = midpointCell(last, index)
            if (mid != null && mid !in selected) {
                selected.add(mid)
            }
        }
        selected.add(index)
    }

    fun finish() {
        finger = null
        if (!enabled) {
            selected.clear()
            return
        }
        if (selected.size >= LockRules.PATTERN_MIN_POINTS) {
            onPatternComplete(LockRules.encodePattern(selected.toList()))
        }
        selected.clear()
    }

    val lineColor = if (errorFlash) VaultDanger else VaultAccent
    val dotFill = if (errorFlash) VaultDanger else VaultAccent

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        val side = min(maxWidth, maxSize)
        Canvas(
            modifier = Modifier
                .size(side)
                .aspectRatio(1f)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectDragGestures(
                        onDragStart = { offset ->
                            selected.clear()
                            finger = offset
                            hitTest(offset)?.let { addCell(it) }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val pos = change.position
                            finger = pos
                            hitTest(pos)?.let { addCell(it) }
                        },
                        onDragEnd = { finish() },
                        onDragCancel = {
                            finger = null
                            selected.clear()
                        },
                    )
                },
        ) {
            val pad = minF(size.width, size.height) * 0.12f
            val usable = minF(size.width, size.height) - pad * 2
            val step = usable / 2f
            val originX = (size.width - usable) / 2f
            val originY = (size.height - usable) / 2f
            val centers = (0 until 9).map { i ->
                val col = i % 3
                val row = i / 3
                Offset(originX + col * step, originY + row * step)
            }
            cellCenters = centers

            if (selected.size >= 2) {
                for (i in 0 until selected.lastIndex) {
                    drawLine(
                        color = lineColor.copy(alpha = 0.85f),
                        start = centers[selected[i]],
                        end = centers[selected[i + 1]],
                        strokeWidth = 6f,
                        cap = StrokeCap.Round,
                    )
                }
            }
            if (selected.isNotEmpty() && finger != null) {
                drawLine(
                    color = lineColor.copy(alpha = 0.55f),
                    start = centers[selected.last()],
                    end = finger!!,
                    strokeWidth = 5f,
                    cap = StrokeCap.Round,
                )
            }

            centers.forEachIndexed { i, c ->
                val selectedDot = i in selected
                drawCircle(color = VaultSurface, radius = 22f, center = c)
                drawCircle(
                    color = if (selectedDot) dotFill else VaultAccent.copy(alpha = 0.35f),
                    radius = if (selectedDot) 14f else 10f,
                    center = c,
                )
                if (selectedDot) {
                    drawCircle(
                        color = Color.Transparent,
                        radius = 22f,
                        center = c,
                        style = Stroke(width = 2.5f),
                    )
                    drawCircle(
                        color = dotFill.copy(alpha = 0.5f),
                        radius = 22f,
                        center = c,
                        style = Stroke(width = 2.5f),
                    )
                }
            }
        }
    }
}

private fun midpointCell(a: Int, b: Int): Int? {
    val ar = a / 3
    val ac = a % 3
    val br = b / 3
    val bc = b % 3
    if ((ar + br) % 2 != 0 || (ac + bc) % 2 != 0) return null
    val mr = (ar + br) / 2
    val mc = (ac + bc) / 2
    if (mr == ar && mc == ac) return null
    if (mr == br && mc == bc) return null
    val dist = hypot((br - ar).toDouble(), (bc - ac).toDouble())
    if (dist < 1.9) return null
    return mr * 3 + mc
}
