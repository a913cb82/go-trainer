package com.gotrainer.nine.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.gotrainer.nine.game.CapturedStone
import com.gotrainer.nine.game.PlaceFx
import kotlin.math.roundToInt

private val WOOD = Color(0xFFE8C07A)
private val GRID = Color(0xFF3E2B15)
private val STARS = listOf(2 to 2, 2 to 6, 6 to 2, 6 to 6, 4 to 4)

/** Place phase (settle) and shrink phase (captures) lengths, ms. */
internal const val PLACE_MS = 90
internal const val SHRINK_MS = 150

/** One settle frame: oversize scale, height above the point, ring opacity. */
internal data class PlaceFrame(val scale: Float, val dyCells: Float, val ringAlpha: Float)

/**
 * Settle motion: arrives 1.33x and 0.462 cell high, ease-out to rest; the
 * last-move ring fades in over the last third. Matches demo f_place.
 */
internal fun placeFrame(k: Float): PlaceFrame {
    val e = 1f - (1f - k) * (1f - k) * (1f - k)
    return PlaceFrame(1.33f - 0.33f * e, -0.462f * (1f - e), ((k - 0.65f) / 0.35f).coerceIn(0f, 1f))
}

/** Capture shrink: smoothstep 1 to 0 at full opacity. Matches demo e_shrink. */
internal fun shrinkScale(k: Float): Float {
    val s = k * k * (3 - 2 * k)
    return (1f - s).coerceIn(0f, 1f)
}

/** Pure Canvas 9x9 board: grid, hoshi, stones, last-move ring. Wood board in both themes. */
@Composable
fun BoardView(
    boardSignMap: List<List<Int>>,
    lastMove: Pair<Int, Int>?,
    onVertexClick: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    /** Captured stones mid-shrink (empty points now); purely visual overlay. */
    popStones: List<CapturedStone> = emptyList(),
    /** Stone currently settling (already on the logical board); drawn on top. */
    placeFx: PlaceFx? = null,
    /** 0 = arrival, 1 = rest; driven by the parent's 90/240 ms clock. */
    animProgress: Float = 1f,
    /** 150 (place only) or 300 (place then shrink); picks the phase split. */
    animTotalMs: Int = PLACE_MS,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .pointerInput(boardSignMap) {
                detectTapGestures { offset ->
                    val w = size.width
                    val pad = w * 0.08f
                    val cell = (w - pad * 2f) / 8f
                    val x = ((offset.x - pad) / cell).roundToInt().coerceIn(0, 8)
                    val y = ((offset.y - pad) / cell).roundToInt().coerceIn(0, 8)
                    onVertexClick(x, y)
                }
            }
    ) {
        val w = size.width
        val pad = w * 0.08f
        val cell = (w - pad * 2f) / 8f
        fun cx(x: Int) = pad + x * cell
        fun cy(y: Int) = pad + y * cell

        drawRect(WOOD)
        // grid
        for (i in 0 until 9) {
            val p = pad + i * cell
            drawLine(GRID, Offset(pad, p), Offset(pad + 8 * cell, p), strokeWidth = 2f)
            drawLine(GRID, Offset(p, pad), Offset(p, pad + 8 * cell), strokeWidth = 2f)
        }
        // hoshi
        for ((x, y) in STARS) drawCircle(GRID, radius = cell * 0.11f, center = Offset(cx(x), cy(y)))
        // coords (skip I)
        val letters = "ABCDEFGHJKLMNOPQRST"
        for (i in 0 until 9) {
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#5a3e1a")
                    textSize = cell * 0.32f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                drawText(letters[i].toString(), cx(i), pad - cell * 0.18f, paint)
                drawText((9 - i).toString(), pad - cell * 0.42f, cy(i) + cell * 0.12f, paint)
            }
        }
        // stones with shadow
        fun stoneAt(x: Int, y: Int, v: Int, scale: Float = 1f, alpha: Float = 1f, dyCells: Float = 0f) {
            if (scale <= 0f || alpha <= 0f) return
            val c = Offset(cx(x), cy(y) + dyCells * cell)
            drawCircle(Color.Black.copy(alpha = 0.25f * alpha), radius = cell * 0.46f * scale, center = Offset(c.x + 2f, c.y + 3f))
            // Board stones stay rimless (black) / brown-rimmed (white) as
            // always; outline rims are New Game chooser dressing only.
            drawGoStone(c, cell * 0.44f * scale, black = v == 1, alpha = alpha)
            if (v == 2)
                drawCircle(
                    Color(0xFF8A7040).copy(alpha = alpha),
                    radius = cell * 0.44f * scale, center = c, style = Stroke(width = 2f),
                )
        }
        // Stone animation: the logical board already moved on — this draws
        // nothing but pixels. The settling stone skips the static pass and
        // draws on top of its neighbours; captured stones (gone logically)
        // sit full-size through the place phase, then shrink away unfaded.
        val placing = placeFx != null && animProgress < 1f
        val elapsed = animProgress.coerceIn(0f, 1f) * animTotalMs
        for (y in 0 until 9) for (x in 0 until 9) {
            val v = boardSignMap[y][x]
            if (v == 0) continue
            if (placing && x == placeFx!!.x && y == placeFx.y) continue
            stoneAt(x, y, v)
        }
        if (placing) {
            val fr = placeFrame((elapsed / PLACE_MS).coerceIn(0f, 1f))
            stoneAt(placeFx!!.x, placeFx.y, placeFx.color, scale = fr.scale, dyCells = fr.dyCells)
            if (fr.ringAlpha > 0f) {
                drawCircle(
                    (if (placeFx.color == 1) Color.White else Color.Black).copy(alpha = fr.ringAlpha),
                    radius = cell * 0.2f, center = Offset(cx(placeFx.x), cy(placeFx.y)),
                    style = Stroke(width = 4f),
                )
            }
        }
        if (popStones.isNotEmpty() && animProgress < 1f) {
            val sc = shrinkScale(((elapsed - PLACE_MS) / SHRINK_MS).coerceIn(0f, 1f))
            if (sc > 0f) for (st in popStones) stoneAt(st.x, st.y, st.color, scale = sc)
        }
        // last-move ring. The settling stone carries its own fading ring;
        // the static one returns when the clock finishes.
        if (lastMove != null) {
            val (lx, ly) = lastMove
            val ringCovered = placing && lx == placeFx!!.x && ly == placeFx.y
            if (!ringCovered && boardSignMap[ly][lx] != 0) {
                val v = boardSignMap[ly][lx]
                drawCircle(
                    if (v == 1) Color.White else Color.Black,
                    radius = cell * 0.2f, center = Offset(cx(lx), cy(ly)),
                    style = Stroke(width = 4f),
                )
            }
        }
    }
}
