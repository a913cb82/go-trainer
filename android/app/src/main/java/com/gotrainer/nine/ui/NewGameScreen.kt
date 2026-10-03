package com.gotrainer.nine.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gotrainer.nine.game.ColorChoice
import com.gotrainer.nine.game.Difficulty
import com.gotrainer.nine.game.GameFlow
import com.gotrainer.nine.game.GameState
import com.gotrainer.nine.game.Rank
import com.gotrainer.nine.game.label
import kotlin.math.roundToInt

/** Guidance preset: one tap writes a canonical (MC, best, worst) triple. */
internal enum class Guidance {
    OFF, HINTS, FULL, CUSTOM;

    fun label(): String = when (this) {
        OFF -> "Off"
        HINTS -> "Hints"
        FULL -> "Full"
        CUSTOM -> "Custom"
    }

    /** (multipleChoice, best, worst); Off/Custom keep the counts in hand. */
    fun applyTo(best: Int, worst: Int): Triple<Boolean, Int, Int> = when (this) {
        OFF -> Triple(false, best, worst)
        HINTS -> Triple(true, 2, 0)
        FULL -> Triple(true, 2, 3)
        CUSTOM -> Triple(true, best, worst)
    }
}

/** Derive the preset shown for current drafts; zero-count MC plays free. */
internal fun guidancePreset(multipleChoice: Boolean, best: Int, worst: Int): Guidance =
    if (!multipleChoice || best + worst == 0) Guidance.OFF
    else if (best == 2 && worst == 0) Guidance.HINTS
    else if (best == 2 && worst == 3) Guidance.FULL
    else Guidance.CUSTOM

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
    draftMultipleChoice: Boolean,
    onDraftMultipleChoice: (Boolean) -> Unit,
    draftBest: Int,
    onDraftBest: (Int) -> Unit,
    draftWorst: Int,
    onDraftWorst: (Int) -> Unit,
    draftColor: ColorChoice,
    onDraftColor: (ColorChoice) -> Unit,
    draftFeedback: Boolean,
    onDraftFeedback: (Boolean) -> Unit,
    draftRanked: Boolean,
    onDraftRanked: (Boolean) -> Unit,
    draftDifficulty: Difficulty,
    onDraftDifficulty: (Difficulty) -> Unit,
    draftTargetWinrate: Int,
    onDraftTargetWinrate: (Int) -> Unit,
    onBack: () -> Unit,
    onStart: () -> Unit,
) {
    // Guidance preset shown for the current drafts; Fine-tune opens the
    // counts below without moving anything else on screen.
    var tuned by remember { mutableStateOf(false) }
    val preset = guidancePreset(draftMultipleChoice, draftBest, draftWorst)
    fun applyPreset(g: Guidance) {
        val (mc, b, w) = g.applyTo(draftBest, draftWorst)
        onDraftMultipleChoice(mc)
        onDraftBest(b)
        onDraftWorst(w)
        if (g == Guidance.HINTS || g == Guidance.FULL) onDraftFeedback(true)
        tuned = g == Guidance.CUSTOM
    }
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp),
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
            Text("You play as", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow {
                ColorChoice.ALL.forEachIndexed { i, c ->
                    SegmentedButton(
                        selected = draftColor == c,
                        onClick = { onDraftColor(c) },
                        shape = SegmentedButtonDefaults.itemShape(i, ColorChoice.ALL.size),
                        label = { Text(c.label()) },
                    )
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

            Text("Difficulty", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow {
                Difficulty.entries.forEachIndexed { i, d ->
                    SegmentedButton(
                        selected = draftDifficulty == d,
                        onClick = { onDraftDifficulty(d) },
                        shape = SegmentedButtonDefaults.itemShape(i, Difficulty.entries.size),
                        label = { Text(d.label()) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Automatch previews the rung Start would pick, live as the slider moves.
                val autoRank = Rank.ALL[GameFlow.automatchRung(s.playerRating, draftTargetWinrate)]
                Text("Opponent plays as", style = MaterialTheme.typography.bodyMedium)
                Text(
                    (if (draftDifficulty == Difficulty.AUTOMATCH) autoRank else draftRank).id,
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Target winrate", style = MaterialTheme.typography.bodyMedium)
                    Text("$draftTargetWinrate%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = draftTargetWinrate.toFloat(),
                    onValueChange = { onDraftTargetWinrate(it.roundToInt().coerceIn(10, 90)) },
                    valueRange = 10f..90f,
                    steps = 7,
                )
            }
            Text("Guidance", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow {
                Guidance.entries.forEachIndexed { i, g ->
                    SegmentedButton(
                        selected = preset == g,
                        onClick = { applyPreset(g) },
                        shape = SegmentedButtonDefaults.itemShape(i, Guidance.entries.size),
                        label = { Text(g.label()) },
                    )
                }
            }
            Text(
                when (preset) {
                    Guidance.OFF -> "Play anywhere, no candidate moves"
                    Guidance.HINTS -> "2 best moves to choose from"
                    Guidance.FULL -> "2 best and 3 worst, points after each pick"
                    Guidance.CUSTOM -> "Your own mix, tuned below"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (tuned || preset == Guidance.CUSTOM) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Best moves to show", style = MaterialTheme.typography.bodyMedium)
                    Text("$draftBest", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = draftBest.toFloat(),
                    onValueChange = { onDraftBest(it.roundToInt().coerceIn(0, 5)) },
                    valueRange = 0f..5f,
                    steps = 4,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Worst moves to show", style = MaterialTheme.typography.bodyMedium)
                    Text("$draftWorst", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = draftWorst.toFloat(),
                    onValueChange = { onDraftWorst(it.roundToInt().coerceIn(0, 5)) },
                    valueRange = 0f..5f,
                    steps = 4,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Instant feedback", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Show points lost after each pick",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = draftFeedback, onCheckedChange = onDraftFeedback)
                }
            }
            // Ranked needs real free play (no candidate moves); suggestions
            // games are always unrated, so the toggle locks to Unranked there.
            // The opt-in survives: flip back to free play and Ranked returns.
            Text("Rating", style = MaterialTheme.typography.labelLarge)
            val canRank = !draftMultipleChoice || draftBest + draftWorst == 0
            SingleChoiceSegmentedButtonRow {
                listOf(true to "Ranked", false to "Unranked").forEachIndexed { i, (v, label) ->
                    SegmentedButton(
                        selected = (if (canRank) draftRanked else false) == v,
                        onClick = { onDraftRanked(v) },
                        enabled = canRank,
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                        label = { Text(label) },
                    )
                }
            }
            if (!canRank) Text(
                "Guidance games are always unrated.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
