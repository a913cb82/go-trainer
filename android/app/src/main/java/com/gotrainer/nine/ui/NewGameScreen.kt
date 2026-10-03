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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
    ) { inner ->
        Column(
            modifier = Modifier.padding(inner).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 32.dp),
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

            Text("Opponent", style = MaterialTheme.typography.labelLarge)
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
            Text("Rating", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow {
                listOf(true to "Ranked", false to "Unranked").forEachIndexed { i, (v, label) ->
                    SegmentedButton(
                        selected = draftRanked == v,
                        onClick = { onDraftRanked(v) },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                        label = { Text(label) },
                    )
                }
            }

            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                Text("Start game")
            }
        }
    }
}
