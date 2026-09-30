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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.gotrainer.nine.game.Candidate
import com.gotrainer.nine.game.CapturedStone
import com.gotrainer.nine.game.PlaceFx
import com.gotrainer.nine.game.EvaluatedMove
import com.gotrainer.nine.game.GapStyle
import kotlin.math.roundToInt

private val WOOD = Color(0xFFE8C07A)
private val GRID = Color(0xFF3E2B15)
private val STARS = listOf(2 to 2, 2 to 6, 6 to 2, 6 to 6, 4 to 4)

/** Feedback colors by selection GROUP: good = green, bad = red, middle = yellow. */
internal fun tagStyle(tag: String): GapStyle = when (tag) {
    "good" -> GapStyle("#27864a", "#c8f0c8")
    "overconcentrated" -> GapStyle("#c0392b", "#ffcccc")
    else -> GapStyle("#b7791f", "#fff6b0")
}

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

/** Pure Canvas 9x9 board: grid, hoshi, stones, candidates, feedback. Wood board in both themes. */
@Composable
fun BoardView(
    boardSignMap: List<List<Int>>,
    candidates: List<Candidate>?,
    evaluations: List<EvaluatedMove>?,
    lastMove: Pair<Int, Int>?,
    feedbackMove: Pair<Int, Int>?,
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
            .pointerInput(candidates, boardSignMap) {
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
            if (v == 1) {
                drawCircle(Color(0xFF111111).copy(alpha = alpha), radius = cell * 0.44f * scale, center = c)
                drawCircle(Color(0xFF3A3A3A).copy(alpha = alpha), radius = cell * 0.13f * scale, center = Offset(c.x - cell * 0.12f, c.y - cell * 0.12f))
            } else {
                drawCircle(Color(0xFFFDf8EC).copy(alpha = alpha), radius = cell * 0.44f * scale, center = c)
                drawCircle(Color(0xFF8A7040).copy(alpha = alpha), radius = cell * 0.44f * scale, center = c, style = Stroke(width = 2f))
            }
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
        // last-move ring — skipped ONLY when a feedback halo is actually drawn at
        // that point (the halo is what would collide). In free-choice review the
        // feedback list is empty, so the ring must still show on the player's
        // own (black) stone: the old coordinate-only check hid it there.
        val feedbackHasHalo = feedbackMove != null &&
            boardSignMap[feedbackMove.second][feedbackMove.first] != 0 &&
            evaluations?.any { it.x == feedbackMove.first && it.y == feedbackMove.second } == true
        if (lastMove != null) {
            val (lx, ly) = lastMove
            // The settling stone carries its own fading ring; the static one
            // returns when the clock finishes.
            val ringCovered = placing && lx == placeFx!!.x && ly == placeFx.y
            if (!ringCovered && boardSignMap[ly][lx] != 0 && !(feedbackHasHalo && lx == feedbackMove!!.first && ly == feedbackMove.second)) {
                val v = boardSignMap[ly][lx]
                drawCircle(
                    if (v == 1) Color.White else Color.Black,
                    radius = cell * 0.2f, center = Offset(cx(lx), cy(ly)),
                    style = Stroke(width = 4f),
                )
            }
        }
        // faint dashed candidates
        if (candidates != null) {
            for (c in candidates) {
                drawCircle(
                    Color(0xFFFFF7CC).copy(alpha = 0.32f), radius = cell * 0.42f,
                    center = Offset(cx(c.x), cy(c.y)),
                )
                drawCircle(
                    Color(0xFF7A5A1A).copy(alpha = 0.45f), radius = cell * 0.42f,
                    center = Offset(cx(c.x), cy(c.y)),
                    style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
                )
            }
        }
        // feedback: picked halo + pts/win% pill, colored by the move's GROUP
        // (good = green, bad = red, middle = yellow) — not by point cutoffs.
        if (feedbackMove != null) {
            val (lx, ly) = feedbackMove
            if (boardSignMap[ly][lx] != 0) {
                val ev = evaluations?.firstOrNull { it.x == lx && it.y == ly }
                if (ev != null) {
                    val style = tagStyle(ev.tag)
                    drawCircle(style.color, radius = cell * 0.53f, center = Offset(cx(lx), cy(ly)), style = Stroke(width = 7f))
                    pill(cx(lx), cy(ly), cell, ev.strongScore, ev.strongWinrate, style.hex, style.bg)
                }
            }
        }
        // alternative badges on empty points
        if (evaluations != null) {
            for (e in evaluations) {
                if (boardSignMap[e.y][e.x] != 0) continue
                if (feedbackMove != null && e.x == feedbackMove.first && e.y == feedbackMove.second) continue
                val style = tagStyle(e.tag)
                pill(cx(e.x), cy(e.y), cell, e.strongScore, e.strongWinrate, style.hex, style.bg)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.pill(
    x: Float, y: Float, cell: Float, pts: Double, win: Double, hex: String, bg: String,
) {
    val fg = Color(android.graphics.Color.parseColor(hex))
    val fill = Color(android.graphics.Color.parseColor(bg))
    drawCircle(fill, radius = cell * 0.33f, center = Offset(x, y))
    drawCircle(fg, radius = cell * 0.33f, center = Offset(x, y), style = Stroke(width = 4f))
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor(hex)
            textSize = cell * 0.24f
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
        }
        val ptsStr = (if (pts >= 0) "+" else "") + String.format("%.1f", pts)
        drawText(ptsStr, x, y + cell * 0.03f, paint)
        val small = android.graphics.Paint(paint).apply { textSize = cell * 0.18f }
        drawText("${(win * 100).roundToInt()}%", x, y + cell * 0.22f, small)
    }
}
