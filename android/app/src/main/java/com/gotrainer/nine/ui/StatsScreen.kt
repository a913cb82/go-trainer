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
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.gotrainer.nine.game.BotRatings
import com.gotrainer.nine.game.PlayerRating
import com.gotrainer.nine.game.RatedGame
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** Whole-rank ticks spanning the data range (at least a 2-rank window). */
internal fun rankAxisTicks(minRank: Double, maxRank: Double): List<Int> {
    val lo = max(0, (ceil(minRank - 0.5) - 1).toInt())
    val hi = minOf(38, (ceil(maxRank + 0.5) + 1).toInt()).let { if (it - lo < 2) lo + 2 else it }
    val span = hi - lo
    val step = max(1, (span / 3.0).roundToInt())
    return (lo..hi step step).toList()
}

private enum class StatsX { GAMES, TIME }

/**
 * Rating history: current rank, record, and a rank-over-time graph with the
 * uncertainty band. Pure content (history in, pixels out) for screenshots.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(history: List<RatedGame>, playerRankText: String, onBack: () -> Unit, onReset: () -> Unit) {
    var xMode by remember { mutableStateOf(StatsX.GAMES) }
    var confirmReset by remember { mutableStateOf(false) }
    val rating = PlayerRating.rate(history)
    val traj = PlayerRating.trajectory(history)
    val wins = history.count { it.score == 1.0 }
    val losses = history.count { it.score == 0.0 }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset rating history?") },
            text = { Text("Clears all ${history.size} rated games. Your rank returns to 30k.") },
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
                        "${rating.rating.roundToInt()} ± ${rating.rd.roundToInt()}",
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
                        RatingGraph(history = history, traj = traj, xMode = xMode)
                    }
                }
            }
        }
    }
}

@Composable
private fun RatingGraph(
    history: List<RatedGame>,
    traj: List<com.gotrainer.nine.game.Glicko2.Rating>,
    xMode: StatsX,
) {
    val accent = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = onSurface)
    val dateFmt = remember { SimpleDateFormat("M/d", Locale.US) }
    // Game 0 is the 30k start every trajectory climbs from. Uncertainty
    // covers every point including game 0 — but game 0's ±12 ranks must not
    // stretch the axis: limits come from played games only, and yOf coerces
    // the band into them (it clips at the chart edge, which is the honest
    // picture: early uncertainty runs off the scale).
    val display = remember(traj) { listOf(PlayerRating.START) + traj }
    val ranks = display.map { BotRatings.ratingToRank(it.rating) }
    val uppers = display.map { BotRatings.ratingToRank(it.rating + it.rd) }
    val lowers = display.map { BotRatings.ratingToRank(it.rating - it.rd) }
    val ticks = remember(ranks) { rankAxisTicks(lowers.drop(1).min(), uppers.drop(1).max()) }
    val xVals: List<Float> = remember(history, xMode) {
        if (xMode == StatsX.GAMES) {
            display.indices.map { it.toFloat() / history.size }
        } else {
            val t0 = history.first().ts.toDouble()
            val span = (history.last().ts - history.first().ts).toDouble().takeIf { it > 0 } ?: 1.0
            listOf(0f) + history.map { ((it.ts - t0) / span).toFloat() }
        }
    }
    Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
        val left = 40.dp.toPx()
        val bottom = 22.dp.toPx()
        val w = size.width - left
        val h = size.height - bottom
        val lo = ticks.first().toFloat()
        val hi = ticks.last().toFloat().takeIf { it > lo } ?: (lo + 1)
        fun yOf(rank: Double): Float = h - ((rank.coerceIn(lo.toDouble(), hi.toDouble()) - lo) / (hi - lo)).toFloat() * h
        fun xOf(t: Float): Float = left + t * w
        // Uncertainty band over all points (display aligns with xVals).
        val band = Path().apply {
            uppers.forEachIndexed { i, u ->
                val x = xOf(xVals[i]); val y = yOf(u)
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            for (i in lowers.indices.reversed()) lineTo(xOf(xVals[i]), yOf(lowers[i]))
            close()
        }
        drawPath(band, accent.copy(alpha = 0.15f))
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
            // Point 0 is the unplayed start: neutral dot, no win/loss color.
            if (i == 0) drawCircle(accent, radius = 6f, center = p)
            else {
                val won = history[i - 1].score == 1.0
                drawCircle(
                    if (won) Color(0xFF27864A) else Color(0xFFC0392B),
                    radius = 6f, center = p,
                )
            }
        }
        // X endpoints: game 0 is the start, last is the latest game.
        val xLabel = { i: Int ->
            if (xMode == StatsX.GAMES) "$i"
            else dateFmt.format(Date(if (i == 0) history.first().ts else history[i - 1].ts))
        }
        val first = textMeasurer.measure(xLabel(0), labelStyle)
        drawText(first, onSurface, topLeft = Offset(left - first.size.width / 2f, h + 4.dp.toPx()))
        val last = textMeasurer.measure(xLabel(display.size - 1), labelStyle)
        drawText(last, onSurface, topLeft = Offset(left + w - last.size.width / 2f, h + 4.dp.toPx()))
    }
}
