package com.gotrainer.nine.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Map a horizontal graph position to a review index ("moves shown"):
 * 0 = empty board, n-1 = after the second-to-last move, n or beyond = live
 * (null). Pure so the drag contract is unit-tested, not eyeballed.
 */
internal fun reviewIndexAt(x: Float, width: Int, n: Int): Int? {
    if (n < 2 || width <= 0) return null
    val k = ((x / width) * n).roundToInt().coerceIn(0, n)
    return if (k >= n) null else k
}

/**
 * Winrate polyline + 50% dashed midline, doubling as the review scrubber:
 * tap or drag horizontally to step through the game, drag to the right edge
 * (or past it) to return to live play. The vertical line marks the reviewed
 * position; reviewIdx semantics = "number of moves shown" (null = live).
 */
@Composable
fun WinrateGraph(
    history: List<Double>,
    reviewIdx: Int? = null,
    onReview: (Int?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val n = history.size
    val accent = MaterialTheme.colorScheme.primary
    fun idxAt(x: Float, width: Int): Int? = reviewIndexAt(x, width, n)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .pointerInput(n) {
                detectTapGestures { onReview(idxAt(it.x, size.width)) }
            }
            .pointerInput(n) {
                detectHorizontalDragGestures { change, _ ->
                    onReview(idxAt(change.position.x, size.width))
                }
            },
    ) {
        val w = size.width
        val h = size.height
        // 50% dashed midline
        drawLine(
            Color.Gray.copy(alpha = 0.5f), Offset(0f, h / 2f), Offset(w, h / 2f),
            strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
        )
        if (n == 0) return@Canvas
        // Points sit mid-cell so the review boundary (k moves shown) lands on the
        // line between point k-1 and point k.
        val step = w / n
        val pts = history.mapIndexed { i, v ->
            Offset((i + 0.5f) * step, h - (v.toFloat().coerceIn(0f, 1f) * h))
        }
        for (i in 0 until pts.size - 1) {
            drawLine(Color(0xFF27864A), pts[i], pts[i + 1], strokeWidth = 4f)
        }
        val shown = (reviewIdx ?: n).coerceIn(0, n)
        // Vertical review line + dot at the last shown move.
        val vx = shown * step
        drawLine(accent.copy(alpha = 0.9f), Offset(vx, 0f), Offset(vx, h), strokeWidth = 3f)
        if (shown >= 1) {
            drawCircle(accent, radius = 6f, center = pts[shown - 1])
        }
    }
}
