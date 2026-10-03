package com.gotrainer.nine.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.gotrainer.nine.game.ColorChoice
import com.gotrainer.nine.game.Difficulty
import com.gotrainer.nine.game.GameFlow
import com.gotrainer.nine.game.GameState
import com.gotrainer.nine.game.Rank
import com.gotrainer.nine.game.label
import kotlin.math.roundToInt

/** Stone graphic for the chooser: shared stones, a "?" stone for Nigiri. */
@Composable
private fun StoneDot(kind: ColorChoice, modifier: Modifier = Modifier) {
    // onSurface is near-black in light mode, near-white in dark: the Nigiri
    // rim follows the theme without asking which one is active.
    val nigiriRim = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.size(28.dp)) {
        val r = size.minDimension / 2f
        val c = center
        if (kind == ColorChoice.BLACK) {
            drawGoStone(c, r, black = true, outline = Color.White)
        } else if (kind == ColorChoice.WHITE) {
            drawGoStone(c, r, black = false, outline = Color.Black)
        } else {
                drawCircle(Color(0xFF9A9AA0), radius = r, center = c)
                drawCircle(nigiriRim, radius = r, center = c, style = Stroke(width = r * STONE_RIM_FRAC))
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = r * 1.1f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isFakeBoldText = true
                    }
                    drawText("?", c.x, c.y + r * 0.38f, paint)
                }
            }
        }
    }

/**
 * Full-width mode toggle as selectable headings (not buttons): the active
 * heading takes the primary color with an underline, the other sits quiet.
 */
@Composable
private fun HeadingToggle(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            Column(
                modifier = Modifier.weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Tab,
                        onClick = { onSelect(i) },
                    )
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (sel) primary else quiet,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().height(2.dp)
                        .background(if (sel) primary else Color.Transparent),
                )
            }
        }
    }
}

/**
 * New-game setup as a full page (like Stats): back in the top bar, Start at
 * the bottom. Drafts stay staged; nothing applies until Start.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGameScreen(
    s: GameState,
    draftRank: Rank,
    onDraftRank: (Rank) -> Unit,
    draftColor: ColorChoice,
    onDraftColor: (ColorChoice) -> Unit,
    draftRanked: Boolean,
    onDraftRanked: (Boolean) -> Unit,
    draftDifficulty: Difficulty,
    onDraftDifficulty: (Difficulty) -> Unit,
    draftTargetWinrate: Int,
    onDraftTargetWinrate: (Int) -> Unit,
    onBack: () -> Unit,
    onStart: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New game") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            Button(
                onClick = onStart,
                // Scaffold slots the bar at the screen edge; lift it clear
                // of the system navigation buttons (no-op under gesture nav).
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                Text("Start game")
            }
        },
    ) { inner ->
        Column(
            modifier = Modifier.padding(inner).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Choose Your Stones", style = MaterialTheme.typography.titleLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ColorChoice.ALL.forEach { c ->
                    val sel = draftColor == c
                    val button: @Composable () -> Unit = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            StoneDot(c)
                            Text(c.label())
                        }
                    }
                    if (sel) {
                        Button(
                            onClick = { onDraftColor(c) },
                            modifier = Modifier.weight(1f),
                        ) { button() }
                    } else {
                        OutlinedButton(
                            onClick = { onDraftColor(c) },
                            modifier = Modifier.weight(1f),
                        ) { button() }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Your rank", style = MaterialTheme.typography.bodyMedium)
                Text(s.playerRankText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }

            Text("Choose Opponent", style = MaterialTheme.typography.titleLarge)
            HeadingToggle(
                options = Difficulty.entries.map { it.label() },
                selected = Difficulty.entries.indexOf(draftDifficulty),
                onSelect = { onDraftDifficulty(Difficulty.entries[it]) },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Automatch previews the rung Start would pick, live as the slider moves.
                val autoRank = Rank.ALL[GameFlow.automatchRung(s.playerRating, draftTargetWinrate)]
                Text(
                    if (draftDifficulty == Difficulty.AUTOMATCH) "Target winrate (plays ${autoRank.id})" else "Bot plays as",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    if (draftDifficulty == Difficulty.AUTOMATCH) "$draftTargetWinrate%" else draftRank.id,
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                )
            }
            if (draftDifficulty == Difficulty.FIXED) {
                val rankIdx = Rank.ALL.indexOf(draftRank).coerceAtLeast(0)
                Slider(
                    value = rankIdx.toFloat(),
                    onValueChange = { onDraftRank(Rank.ALL[it.roundToInt().coerceIn(0, Rank.ALL.size - 1)]) },
                    valueRange = 0f..(Rank.ALL.size - 1).toFloat(),
                    steps = (Rank.ALL.size - 2).coerceAtLeast(0),
                )
            } else {
                Slider(
                    value = draftTargetWinrate.toFloat(),
                    onValueChange = { onDraftTargetWinrate(it.roundToInt().coerceIn(10, 90)) },
                    valueRange = 10f..90f,
                    steps = 7,
                )
            }
            Text("Game Type", style = MaterialTheme.typography.titleLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(true to "Rated", false to "Unrated").forEach { (v, label) ->
                    if (draftRanked == v) {
                        Button(onClick = { onDraftRanked(v) }, modifier = Modifier.weight(1f)) {
                            Text(label)
                        }
                    } else {
                        OutlinedButton(onClick = { onDraftRanked(v) }, modifier = Modifier.weight(1f)) {
                            Text(label)
                        }
                    }
                }
            }

        }
    }
}
