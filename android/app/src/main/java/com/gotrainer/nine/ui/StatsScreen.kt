package com.gotrainer.nine.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.gotrainer.nine.game.BotRatings
import com.gotrainer.nine.game.PlayerWhr
import com.gotrainer.nine.game.RatedGame
import com.gotrainer.nine.game.Whr
import com.gotrainer.nine.game.WhrAnchors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** Whole-rank ticks spanning the data range (at least a 2-rank window). */
internal fun rankAxisTicks(minRank: Double, maxRank: Double): List<Int> {
    val lo = max(0, (ceil(minRank - 0.5) - 1).toInt())
    val hi = minOf(28, (ceil(maxRank + 0.5) + 1).toInt()).let { if (it - lo < 2) lo + 2 else it }
    val span = hi - lo
    val step = max(1, (span / 3.0).roundToInt())
    // Round the top up to the step grid: the plot maps to ticks.first/last,
    // so a thinned-away top tick silently becomes the ceiling and flattens
    // the climb onto the top gridline.
    val top = lo + ceil(span.toDouble() / step).toInt() * step
    return (lo..top step step).toList()
}

/**
 * Games-x positions. Full history keeps true proportions (game n sits at
 * n/size, start dot at 0); a window spreads across the plot so the newest N
 * games use the whole width. Labels stay true game numbers either way.
 */
/**
 * Games-x gridlines: round game numbers, never even pixel divisions.
 * Step is the smallest of 1/2/5 x 10^k holding the span to ~5 lines;
 * ticks stay absolute so a window shows the same numbers as All.
 */
internal fun gamesXTicks(firstNo: Int, lastNo: Int): List<Int> {
    val span = lastNo - firstNo
    if (span <= 0) return emptyList()
    var mag = 1
    var step = 1
    while (span / step > 5) {
        step = when (step / mag) {
            1 -> 2 * mag
            2 -> 5 * mag
            else -> { mag *= 10; mag }
        }
    }
    return ((firstNo + step - 1) / step * step..lastNo step step).toList()
}

private const val DAY_MS = 86_400_000L

/**
 * Time-x gridlines: true calendar boundaries for the span — local
 * midnights under ~12 days, Mondays under ~4 months, month starts beyond.
 * Steps widen to hold ~5 lines. Under ~1.5 days there is nothing sane to
 * draw (same-evening bursts), so no interior ticks.
 */
internal fun timeXTicks(firstTs: Long, lastTs: Long): List<Long> {
    val spanDays = (lastTs - firstTs).toDouble() / DAY_MS
    if (spanDays < 1.5) return emptyList()
    val cal = java.util.Calendar.getInstance()
    val out = mutableListOf<Long>()
    if (spanDays < 12) {
        val stepDays = maxOf(1, (spanDays / 5).roundToInt())
        cal.timeInMillis = firstTs
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        if (cal.timeInMillis < firstTs) cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
        var day = 0
        while (cal.timeInMillis <= lastTs) {
            if (day % stepDays == 0) out.add(cal.timeInMillis)
            cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
            day++
        }
    } else if (spanDays < 120) {
        val stepWeeks = maxOf(1, (spanDays / 7 / 5).roundToInt())
        cal.timeInMillis = firstTs
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        var delta = (java.util.Calendar.MONDAY - cal.get(java.util.Calendar.DAY_OF_WEEK) + 7) % 7
        if (delta == 0 && cal.timeInMillis < firstTs) delta = 7
        cal.add(java.util.Calendar.DAY_OF_MONTH, delta)
        var week = 0
        while (cal.timeInMillis <= lastTs) {
            if (week % stepWeeks == 0) out.add(cal.timeInMillis)
            cal.add(java.util.Calendar.DAY_OF_MONTH, 7)
            week++
        }
    } else {
        val stepMonths = maxOf(1, (spanDays / 30 / 5).roundToInt())
        cal.timeInMillis = firstTs
        cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        if (cal.timeInMillis < firstTs) cal.add(java.util.Calendar.MONTH, 1)
        var month = 0
        while (cal.timeInMillis <= lastTs) {
            if (month % stepMonths == 0) out.add(cal.timeInMillis)
            cal.add(java.util.Calendar.MONTH, 1)
            month++
        }
    }
    return out
}

/** Day/month in British order (3/10), regardless of device locale. */
internal fun dayMonthFormat(): SimpleDateFormat = SimpleDateFormat("d/M", Locale.UK)

/** Fractional x-viewport over the full history (0..1 of the slice). Gestures
 * wander it; axis switches and new games reset it to full. */
internal data class XView(val start: Float, val end: Float) {
    val span: Float get() = end - start
    val isFull: Boolean get() = start <= 0f && end >= 1f
}

internal const val MIN_VIEW_SPAN = 0.02f
private val tapTimeoutMs = android.view.ViewConfiguration.getTapTimeout().toLong()
private val doubleTapTimeoutMs = android.view.ViewConfiguration.getDoubleTapTimeout().toLong()

/** Drag: shift by a plot fraction, clamped so the span never leaves 0..1. */
internal fun XView.panned(dFrac: Float): XView {
    val ns = (start + dFrac).coerceIn(0f, 1f - span)
    return XView(ns, ns + span)
}

/**
 * Pinch: rescale around the focus plot fraction (which stays put),
 * clamped to [MIN_VIEW_SPAN, full].
 */
internal fun XView.zoomed(focus: Float, factor: Float): XView {
    val f = focus.coerceIn(0f, 1f)
    val s = (span / factor).coerceIn(MIN_VIEW_SPAN, 1f)
    val ns = (start + f * span - f * s).coerceIn(0f, 1f - s)
    return XView(ns, ns + s)
}

/** Display indices to draw: in-view points plus edge neighbors (clipRect cuts the lines). */
internal fun visibleRange(n: Int, ts: List<Float>, view: XView): IntRange {
    if (n == 0) return 0..-1
    val lo = ts.indexOfFirst { it >= view.start }.takeIf { it >= 0 } ?: (n - 1)
    val hi = ts.indexOfLast { it <= view.end }.takeIf { it >= 0 } ?: 0
    return maxOf(0, lo - 1)..minOf(n - 1, hi + 1)
}

internal fun gamesXValues(gameNos: List<Int>, historySize: Int, full: Boolean): List<Float> =
    if (full) gameNos.map { it.toFloat() / historySize }
    else {
        val span = (gameNos.size - 1).takeIf { it > 0 } ?: 1
        val first = gameNos.first()
        gameNos.map { (it - first).toFloat() / span }
    }

private enum class StatsX { GAMES, TIME }

/**
 * Rating history: current rank, record, and a rank-over-time graph with the
 * uncertainty band. Pure content (history in, pixels out) for screenshots.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    history: List<RatedGame>,
    traj: List<Whr.Rating>,
    trajLoading: Boolean,
    playerRankText: String,
    onBack: () -> Unit,
    onReset: () -> Unit,
    initialXView: ClosedFloatingPointRange<Float>? = null,
) {
    var xMode by remember { mutableStateOf(StatsX.GAMES) }
    // Pan/zoom viewport over the full history; axis switches reset it.
    var xview by remember(history, xMode) {
        mutableStateOf(initialXView?.let { XView(it.start, it.endInclusive) } ?: XView(0f, 1f))
    }
    var confirmReset by remember { mutableStateOf(false) }
    val rating = PlayerWhr.rate(history)
    // traj is the cached causal curve (Stats backfills gaps off-thread;
    // trajLoading covers the rare full rebuild, e.g. after a version bump).
    val wins = history.count { it.score == 1.0 }
    val losses = history.count { it.score == 0.0 }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset rating history?") },
            text = { Text("Clears all ${history.size} rated games. Your rank returns to 20k.") },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; onReset() }) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stats") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { confirmReset = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Reset history")
                        }
                    }
                },
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier.fillMaxSize().padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(playerRankText, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "${history.size} games · ${wins}W ${losses}L",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${rating.whr.roundToInt()} ± ${rating.unc.roundToInt()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (history.isEmpty()) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Play rated free-play games to earn a rank.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (trajLoading) {
                            Text(
                                "Updating rating curve…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            RatingGraph(
                                history = history, traj = traj, xMode = xMode,
                                view = xview, onView = { xview = it },
                                onToggleX = { xMode = if (xMode == StatsX.GAMES) StatsX.TIME else StatsX.GAMES },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RatingGraph(
    history: List<RatedGame>,
    traj: List<Whr.Rating>,
    xMode: StatsX,
    view: XView,
    onView: (XView) -> Unit,
    onToggleX: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = onSurface)
    val dateFmt = remember { dayMonthFormat() }
    // The graph always covers the full history; game 0 (the 20k start dot)
    // joins it. Pan/zoom moves the viewport, never the slice. The y axis
    // fits rating points only — the band may run off the chart, and yOf
    // clips it.
    val shown = remember(history, traj) {
        Triple(history, traj, true)
    }
    val shownHist = shown.first
    val display = remember(shown) { (if (shown.third) listOf(PlayerWhr.rate(emptyList())) else emptyList()) + shown.second }
    // Full-slice normalization (0..1); the viewport picks a sub-range.
    val fullT: List<Float> = remember(shown, xMode) {
        if (xMode == StatsX.GAMES) {
            val firstNo = history.size - shownHist.size + 1
            val gameNos = (if (shown.third) listOf(0) else emptyList()) + (firstNo..history.size).toList()
            gamesXValues(gameNos, history.size, shown.third)
        } else {
            val t0 = shownHist.first().ts.toDouble()
            val span = (shownHist.last().ts - shownHist.first().ts).toDouble().takeIf { it > 0 } ?: 1.0
            if (shown.third) listOf(0f) + history.map { ((it.ts - t0) / span).toFloat() }
            else shownHist.map { ((it.ts - t0) / span).toFloat() }
        }
    }
    // Visible display indices (+ edge neighbors); everything below draws these.
    val vis = remember(fullT, view) { visibleRange(display.size, fullT, view) }
    val off = if (shown.third) 1 else 0
    val ranks = display.map { WhrAnchors.whrToRank(it.whr) }
    val visRanks = remember(ranks, vis) { ranks.slice(vis) }
    val visUppers = remember(display, vis) { display.slice(vis).map { WhrAnchors.whrToRank(it.whr + it.unc) } }
    val visLowers = remember(display, vis) { display.slice(vis).map { WhrAnchors.whrToRank(it.whr - it.unc) } }
    // Y domain freezes while fingers are down (rescaling mid-gesture is
    // seasickness); it follows the viewport again shortly after release.
    var yView by remember(history, xMode) { mutableStateOf<Pair<Double, Double>?>(null) }
    val yViewRef = rememberUpdatedState(yView)
    val setYViewRef = rememberUpdatedState({ y: Pair<Double, Double>? -> yView = y })
    val visMinMax = remember(visRanks) { visRanks.min() to visRanks.max() }
    val visMinMaxRef = rememberUpdatedState(visMinMax)
    val yMinMax = yView ?: visMinMax
    val ticks = remember(yMinMax) { rankAxisTicks(yMinMax.first, yMinMax.second) }
    // Games x keeps true game numbers.
    val gameNos = remember(shown) {
        val firstNo = history.size - shownHist.size + 1
        (if (shown.third) listOf(0) else emptyList()) + (firstNo..history.size).toList()
    }
    val span = (view.span.takeIf { it > 0 } ?: 1f)
    fun xp(t: Float): Float = (t - view.start) / span
    // X gridline ticks as (position, label): round game numbers in Games
    // mode, calendar boundaries in Time. Ticks outside the viewport drop.
    val xTickTs: List<Pair<Float, String>> = remember(shown, xMode, vis) {
        if (xMode == StatsX.GAMES) {
            val visNos = gameNos.slice(vis)
            gamesXTicks(visNos.first(), visNos.last()).mapNotNull { g ->
                val idx = gameNos.indexOf(g).takeIf { it >= 0 } ?: return@mapNotNull null
                val t = xp(fullT[idx])
                if (t in 0f..1f) t to "$g" else null
            }
        } else {
            val gLo = maxOf(vis.first - off, 0)
            val gHi = minOf(vis.last - off, shownHist.size - 1)
            if (gLo > gHi) emptyList()
            else {
                val t0 = shownHist.first().ts.toDouble()
                val tspan = (shownHist.last().ts - shownHist.first().ts).toDouble().takeIf { it > 0 } ?: 1.0
                timeXTicks(shownHist[gLo].ts, shownHist[gHi].ts).mapNotNull { ts ->
                    val t = xp(((ts - t0) / tspan).toFloat())
                    if (t in 0f..1f) t to dateFmt.format(Date(ts)) else null
                }
            }
        }
    }
    val density = LocalDensity.current
    val leftPx = with(density) { 40.dp.toPx() }
    // Tall axis strip: tick labels on its top row, the axis unit below —
    // the whole strip toggles Games/Time, so the unit doubles the hitbox.
    val axisStripPx = with(density) { 40.dp.toPx() }
    var plotW by remember { mutableStateOf(0f) }
    // Gesture detectors must NOT restart on every viewport change (that
    // amputated each pinch/drag after one step); refs stay fresh instead.
    val viewRef = rememberUpdatedState(view)
    val onViewRef = rememberUpdatedState(onView)
    val onToggleXRef = rememberUpdatedState(onToggleX)
    val slopRef = rememberUpdatedState(LocalViewConfiguration.current.touchSlop)
    val stripRef = rememberUpdatedState(axisStripPx)
    var lastStripTap by remember(history, xMode) { mutableStateOf(0L) }
    val lastStripTapRef = rememberUpdatedState(lastStripTap)
    val setLastStripTapRef = rememberUpdatedState({ t: Long -> lastStripTap = t })
    // Y freezes on first finger down and releases on last finger up.
    // A press-tracking loop owns that truth; the transform loop below only
    // moves the viewport. (The first attempt never even called its freezer —
    // and a quiescence timer would leak on held-still pinches anyway.)
    // Neither loop consumes, so both observe the same stream.
    Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)
        .onSizeChanged { plotW = it.width.toFloat() }
        // One loop owns press truth (y-freeze) AND taps. detectTapGestures
        // holds every tap for the double-tap timeout, which made the axis
        // toggle feel laggy; here a tap fires on finger-up, instantly.
        // Double-tap = two instant toggles (net mode unchanged) + view reset.
        .pointerInput(xMode) {
            awaitEachGesture {
                // Tap detection consumes the down; we only observe, so take it consumed or not.
                val down = awaitFirstDown(requireUnconsumed = false)
                if (yViewRef.value == null) setYViewRef.value(visMinMaxRef.value)
                var upPos = down.position
                var upTime = down.uptimeMillis
                var multi = false
                var moved = false
                do {
                    val event = awaitPointerEvent()
                    if (event.changes.size > 1) multi = true
                    for (c in event.changes) {
                        if (c.pressed) {
                            if ((c.position - down.position).getDistance() > slopRef.value) moved = true
                        } else {
                            upPos = c.position
                            upTime = c.uptimeMillis
                        }
                    }
                } while (event.changes.any { it.pressed })
                setYViewRef.value(null)
                if (!multi && !moved && upTime - down.uptimeMillis < tapTimeoutMs &&
                    upPos.y >= size.height - stripRef.value
                ) {
                    onToggleXRef.value()
                    val now = upTime
                    if (now - lastStripTapRef.value < doubleTapTimeoutMs) {
                        setYViewRef.value(null)
                        onViewRef.value(XView(0f, 1f))
                    }
                    setLastStripTapRef.value(now)
                }
            }
        }
        .pointerInput(xMode, axisStripPx, leftPx, plotW) {
            detectTransformGestures { centroid, pan, zoom, _ ->
                val w = plotW - leftPx
                if (w <= 0f) return@detectTransformGestures
                val focus = ((centroid.x - leftPx) / w).coerceIn(0f, 1f)
                // Drag tracks 1:1 with the finger: the shift is a fraction
                // of the CURRENT span, not the full range. The zoom focus
                // point stays put by construction (see XView.zoomed).
                val v = viewRef.value
                onViewRef.value(v.zoomed(focus, zoom).panned(-pan.x / w * v.span))
            }
        }
    ) {
        val left = 40.dp.toPx()
        val bottom = 40.dp.toPx()
        val w = size.width - left
        val h = size.height - bottom
        val lo = ticks.first().toFloat()
        val hi = ticks.last().toFloat().takeIf { it > lo } ?: (lo + 1)
        fun yRaw(rank: Double): Float = h - ((rank - lo) / (hi - lo)).toFloat() * h
        fun yOf(rank: Double): Float = yRaw(rank.coerceIn(lo.toDouble(), hi.toDouble()))
        fun xOf(t: Float): Float = left + t * w
        // Y ticks (axis furniture draws unclipped).
        for (t in ticks) {
            val y = yOf(t.toDouble())
            drawLine(onSurface.copy(alpha = 0.25f), Offset(left, y), Offset(left + w, y), strokeWidth = 1f)
            val layout = textMeasurer.measure(BotRatings.rankLabel(t.toDouble()), labelStyle)
            drawText(layout, onSurface, topLeft = Offset(0f, y - layout.size.height / 2f))
        }
        // Band, trajectory and dots, hard-clipped to the plot: zoomed edge
        // neighbors live outside 0..1 and must not paint over the axes.
        clipRect(left, 0f, left + w, h) {
            val band = Path().apply {
                visUppers.forEachIndexed { i, u ->
                    val x = xOf(xp(fullT[vis.first + i])); val y = yRaw(u)
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                for (i in visLowers.indices.reversed()) lineTo(xOf(xp(fullT[vis.first + i])), yRaw(visLowers[i]))
                close()
            }
            drawPath(band, accent.copy(alpha = 0.15f))
            val pts = visRanks.mapIndexed { i, r -> Offset(xOf(xp(fullT[vis.first + i])), yOf(r)) }
            for (i in 0 until pts.size - 1) drawLine(accent, pts[i], pts[i + 1], strokeWidth = 4f)
            pts.forEachIndexed { i, p ->
                // Point 0 is the unplayed start, a neutral dot; every game
                // dot is win/loss.
                val di = vis.first + i
                if (shown.third && di == 0) drawCircle(accent, radius = 6f, center = p)
                else {
                    val game = shownHist[di - off]
                    drawCircle(
                        if (game.score == 1.0) Color(0xFF27864A) else Color(0xFFC0392B),
                        radius = 6f, center = p,
                    )
                }
            }
        }
        // X gridlines, fainter than the rank lines; labels yield to the
        // endpoints, then to each other left to right.
        val gridColor = onSurface.copy(alpha = 0.12f)
        for ((t, _) in xTickTs) {
            val x = xOf(t)
            drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
        }
        // X endpoints: first/last visible (true game numbers, or dates).
        val xLabel = { di: Int ->
            if (xMode == StatsX.GAMES) "${gameNos[di]}"
            // The start dot predates game 1, so it borrows its date.
            else dateFmt.format(Date(shownHist[maxOf(di - off, 0)].ts))
        }
        val gapPx = 4.dp.toPx()
        val taken = mutableListOf<Pair<Float, Float>>()
        fun claim(cx: Float, lw: Float): Boolean {
            if (taken.any { (a, b) -> cx - lw / 2 < b + gapPx && cx + lw / 2 > a - gapPx }) return false
            taken.add(cx - lw / 2 to cx + lw / 2)
            return true
        }
        val first = textMeasurer.measure(xLabel(vis.first), labelStyle)
        drawText(first, onSurface, topLeft = Offset(left - first.size.width / 2f, h + 4.dp.toPx()))
        claim(left, first.size.width.toFloat())
        val last = textMeasurer.measure(xLabel(vis.last), labelStyle)
        drawText(last, onSurface, topLeft = Offset(left + w - last.size.width / 2f, h + 4.dp.toPx()))
        claim(left + w, last.size.width.toFloat())
        for ((t, s) in xTickTs) {
            if (t <= 1e-6f || t >= 1f - 1e-6f) continue
            val layout = textMeasurer.measure(s, labelStyle)
            val cx = xOf(t)
            if (claim(cx, layout.size.width.toFloat()))
                drawText(layout, onSurface, topLeft = Offset(cx - layout.size.width / 2f, h + 4.dp.toPx()))
        }
        // Axis unit, own row under the ticks: the toggle's visible face.
        val unit = textMeasurer.measure(if (xMode == StatsX.GAMES) "Games" else "Time", labelStyle)
        drawText(
            unit, onSurface,
            topLeft = Offset(size.width - unit.size.width.toFloat(), h + 20.dp.toPx()),
        )
    }
}
