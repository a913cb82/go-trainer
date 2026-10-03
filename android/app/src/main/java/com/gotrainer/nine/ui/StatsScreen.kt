package com.gotrainer.nine.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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

internal fun gamesXValues(gameNos: List<Int>, historySize: Int, full: Boolean): List<Float> =
    if (full) gameNos.map { it.toFloat() / historySize }
    else {
        val span = (gameNos.size - 1).takeIf { it > 0 } ?: 1
        val first = gameNos.first()
        gameNos.map { (it - first).toFloat() / span }
    }

private enum class StatsX { GAMES, TIME }

/** Graph window: how many recent games to show. */
private enum class StatsWindow(val size: Int, val label: String) {
    W10(10, "10"),
    W100(100, "100"),
    W1000(1000, "1000"),
    ALL(Int.MAX_VALUE, "All"),
}

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
) {
    var xMode by remember { mutableStateOf(StatsX.GAMES) }
    var window by remember { mutableStateOf(StatsWindow.ALL) }
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
                        SingleChoiceSegmentedButtonRow {
                            StatsX.entries.forEachIndexed { i, m ->
                                SegmentedButton(
                                    selected = xMode == m,
                                    onClick = { xMode = m },
                                    shape = SegmentedButtonDefaults.itemShape(i, StatsX.entries.size),
                                    label = { Text(if (m == StatsX.GAMES) "Games" else "Time") },
                                )
                            }
                        }
                        SingleChoiceSegmentedButtonRow {
                            StatsWindow.entries.forEachIndexed { i, wsel ->
                                SegmentedButton(
                                    selected = window == wsel,
                                    onClick = { window = wsel },
                                    shape = SegmentedButtonDefaults.itemShape(i, StatsWindow.entries.size),
                                    label = { Text(wsel.label) },
                                )
                            }
                        }
                        if (trajLoading) {
                            Text(
                                "Updating rating curve…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            RatingGraph(history = history, traj = traj, xMode = xMode, window = window)
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
    window: StatsWindow,
) {
    val accent = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = onSurface)
    val dateFmt = remember { SimpleDateFormat("M/d", Locale.US) }
    // Window slices the newest games; game 0 (the 20k start dot) joins only
    // when the window covers the whole history. The y axis fits rating
    // points only — the band may run off the chart, and yOf clips it.
    val shown = remember(history, traj, window) {
        val n = minOf(window.size, history.size)
        Triple(history.takeLast(n), traj.takeLast(n), n == history.size)
    }
    val shownHist = shown.first
    val display = remember(shown) { (if (shown.third) listOf(PlayerWhr.rate(emptyList())) else emptyList()) + shown.second }
    val ranks = display.map { WhrAnchors.whrToRank(it.whr) }
    val uppers = display.map { WhrAnchors.whrToRank(it.whr + it.unc) }
    val lowers = display.map { WhrAnchors.whrToRank(it.whr - it.unc) }
    val ticks = remember(ranks) { rankAxisTicks(ranks.min(), ranks.max()) }
    // Games x keeps true game numbers so a window sits at history's right end.
    val gameNos = remember(shown) {
        val firstNo = history.size - shownHist.size + 1
        (if (shown.third) listOf(0) else emptyList()) + (firstNo..history.size).toList()
    }
    val xVals: List<Float> = remember(shown, xMode) {
        if (xMode == StatsX.GAMES) {
            gamesXValues(gameNos, history.size, shown.third)
        } else {
            val t0 = shownHist.first().ts.toDouble()
            val span = (shownHist.last().ts - shownHist.first().ts).toDouble().takeIf { it > 0 } ?: 1.0
            if (shown.third) listOf(0f) + history.map { ((it.ts - t0) / span).toFloat() }
            else shownHist.map { ((it.ts - t0) / span).toFloat() }
        }
    }
    // X gridline ticks as (position, label): round game numbers in Games
    // mode (same normalization as the dots), calendar boundaries in Time.
    val xTickTs: List<Pair<Float, String>> = remember(shown, xMode) {
        if (xMode == StatsX.GAMES) {
            val first = gameNos.first()
            val span = (gameNos.size - 1).takeIf { it > 0 } ?: 1
            gamesXTicks(first, gameNos.last()).map { g ->
                val t = if (shown.third) g.toFloat() / history.size
                else (g - first).toFloat() / span
                t to "$g"
            }
        } else {
            val t0 = shownHist.first().ts.toDouble()
            val span = (shownHist.last().ts - shownHist.first().ts).toDouble().takeIf { it > 0 } ?: 1.0
            timeXTicks(shownHist.first().ts, shownHist.last().ts).map { ts ->
                ((ts - t0) / span).toFloat() to dateFmt.format(Date(ts))
            }
        }
    }
    Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
        val left = 40.dp.toPx()
        val bottom = 22.dp.toPx()
        val w = size.width - left
        val h = size.height - bottom
        val lo = ticks.first().toFloat()
        val hi = ticks.last().toFloat().takeIf { it > lo } ?: (lo + 1)
        fun yRaw(rank: Double): Float = h - ((rank - lo) / (hi - lo)).toFloat() * h
        fun yOf(rank: Double): Float = yRaw(rank.coerceIn(lo.toDouble(), hi.toDouble()))
        fun xOf(t: Float): Float = left + t * w
        // Uncertainty band, hard-clipped to the plot: variance never moves
        // the axis, it just runs off the edge where it exceeds it.
        clipRect(left, 0f, left + w, h) {
            val band = Path().apply {
                uppers.forEachIndexed { i, u ->
                    val x = xOf(xVals[i]); val y = yRaw(u)
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                for (i in lowers.indices.reversed()) lineTo(xOf(xVals[i]), yRaw(lowers[i]))
                close()
            }
            drawPath(band, accent.copy(alpha = 0.15f))
        }
        // Y ticks.
        for (t in ticks) {
            val y = yOf(t.toDouble())
            drawLine(onSurface.copy(alpha = 0.25f), Offset(left, y), Offset(left + w, y), strokeWidth = 1f)
            val layout = textMeasurer.measure(BotRatings.rankLabel(t.toDouble()), labelStyle)
            drawText(layout, onSurface, topLeft = Offset(0f, y - layout.size.height / 2f))
        }
        // Trajectory.
        val pts = ranks.mapIndexed { i, r -> Offset(xOf(xVals[i]), yOf(r)) }
        for (i in 0 until pts.size - 1) drawLine(accent, pts[i], pts[i + 1], strokeWidth = 4f)
        pts.forEachIndexed { i, p ->
            // Full history: point 0 is the unplayed start, a neutral dot.
            // A window holds played games only, so every dot is win/loss.
            if (shown.third && i == 0) drawCircle(accent, radius = 6f, center = p)
            else {
                val game = shownHist[if (shown.third) i - 1 else i]
                drawCircle(
                    if (game.score == 1.0) Color(0xFF27864A) else Color(0xFFC0392B),
                    radius = 6f, center = p,
                )
            }
        }
        // X gridlines, fainter than the rank lines; labels yield to the
        // endpoints, then to each other left to right.
        val gridColor = onSurface.copy(alpha = 0.12f)
        for ((t, _) in xTickTs) {
            val x = xOf(t)
            drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
        }
        // X endpoints: true game numbers (a window starts mid-history).
        val xLabel = { i: Int ->
            if (xMode == StatsX.GAMES) "${gameNos[i]}"
            else dateFmt.format(Date(shownHist[if (shown.third) maxOf(i - 1, 0) else i].ts))
        }
        val gapPx = 4.dp.toPx()
        val taken = mutableListOf<Pair<Float, Float>>()
        fun claim(cx: Float, lw: Float): Boolean {
            if (taken.any { (a, b) -> cx - lw / 2 < b + gapPx && cx + lw / 2 > a - gapPx }) return false
            taken.add(cx - lw / 2 to cx + lw / 2)
            return true
        }
        val first = textMeasurer.measure(xLabel(0), labelStyle)
        drawText(first, onSurface, topLeft = Offset(left - first.size.width / 2f, h + 4.dp.toPx()))
        claim(left, first.size.width.toFloat())
        val last = textMeasurer.measure(xLabel(display.size - 1), labelStyle)
        drawText(last, onSurface, topLeft = Offset(left + w - last.size.width / 2f, h + 4.dp.toPx()))
        claim(left + w, last.size.width.toFloat())
        for ((t, s) in xTickTs) {
            if (t <= 1e-6f || t >= 1f - 1e-6f) continue
            val layout = textMeasurer.measure(s, labelStyle)
            val cx = xOf(t)
            if (claim(cx, layout.size.width.toFloat()))
                drawText(layout, onSurface, topLeft = Offset(cx - layout.size.width / 2f, h + 4.dp.toPx()))
        }
    }
}
