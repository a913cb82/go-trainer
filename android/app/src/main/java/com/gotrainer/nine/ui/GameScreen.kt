package com.gotrainer.nine.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gotrainer.nine.game.Candidate
import com.gotrainer.nine.game.ColorChoice
import com.gotrainer.nine.game.Difficulty
import com.gotrainer.nine.game.EvaluatedMove
import com.gotrainer.nine.game.GameState
import com.gotrainer.nine.game.GameViewModel
import com.gotrainer.nine.game.GoBoard
import com.gotrainer.nine.game.Rank
import com.gotrainer.nine.game.Scoring

/** Header subtitle: rank alone in free play, rank + total choices otherwise. */
internal fun headerSubtitle(s: GameState): String {
    val total = s.choiceCount
    val base = if (!s.multipleChoice || total == 0) s.rank.id
    else if (total == 1) "${s.rank.id} · 1 choice" else "${s.rank.id} · $total choices"
    return base
}

/** Callbacks so the pure content below is screenshot-friendly (no ViewModel). */
data class GameActions(
    val onBoardTap: (Int, Int) -> Unit = { _, _ -> },
    val onNewGame: () -> Unit = {},
    val onUndo: () -> Unit = {},
    val onPass: () -> Unit = {},
    val onShowFeedback: (Boolean) -> Unit = {},
    val onScopeAll: (Boolean) -> Unit = {},
    val onGraphOpen: (Boolean) -> Unit = {},
    val onReview: (Int?) -> Unit = {},
    val onApplySetup: (Rank, Boolean, Int, Int, ColorChoice, Boolean, Difficulty, Int, Boolean) -> Unit =
        { _, _, _, _, _, _, _, _, _ -> },
    val onShowStats: () -> Unit = {},
    val onOpenSetup: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(vm: GameViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    var showStats by remember { mutableStateOf(false) }
    var showSetup by remember { mutableStateOf(false) }
    val history by vm.ratedHistoryFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    // Staged setup: the page edits drafts; nothing applies until Start game.
    var draftRank by remember { mutableStateOf(s.rank) }
    var draftMultipleChoice by remember { mutableStateOf(s.multipleChoice) }
    var draftBest by remember { mutableStateOf(s.bestCount) }
    var draftWorst by remember { mutableStateOf(s.worstCount) }
    var draftColor by remember { mutableStateOf(s.colorChoice) }
    var draftFeedback by remember { mutableStateOf(s.showFeedback) }
    var draftRanked by remember { mutableStateOf(s.ranked) }
    var draftDifficulty by remember { mutableStateOf(s.difficulty) }
    var draftTargetWinrate by remember { mutableStateOf(s.targetWinrate) }
    fun openSetup() {
        draftRank = s.rank
        draftMultipleChoice = s.multipleChoice
        draftBest = s.bestCount
        draftWorst = s.worstCount
        draftColor = s.colorChoice
        draftFeedback = s.showFeedback
        draftRanked = s.ranked
        draftDifficulty = s.difficulty
        draftTargetWinrate = s.targetWinrate
        showSetup = true
    }

    s.error?.let { err ->
        LaunchedEffect(err) {
            snack.showSnackbar(err)
            vm.clearError()
        }
    }

    if (showStats) {
        StatsScreen(
            history = history, playerRankText = s.playerRankText,
            onBack = { showStats = false }, onReset = vm::resetHistory,
        )
        return
    }
    if (showSetup) {
        NewGameScreen(
            s = s,
            draftRank = draftRank,
            onDraftRank = { draftRank = it },
            draftMultipleChoice = draftMultipleChoice,
            onDraftMultipleChoice = { draftMultipleChoice = it },
            draftBest = draftBest,
            onDraftBest = { draftBest = it },
            draftWorst = draftWorst,
            onDraftWorst = { draftWorst = it },
            draftColor = draftColor,
            onDraftColor = { draftColor = it },
            draftFeedback = draftFeedback,
            onDraftFeedback = { draftFeedback = it },
            draftRanked = draftRanked,
            onDraftRanked = { draftRanked = it },
            draftDifficulty = draftDifficulty,
            onDraftDifficulty = { draftDifficulty = it },
            draftTargetWinrate = draftTargetWinrate,
            onDraftTargetWinrate = { draftTargetWinrate = it },
            onBack = { showSetup = false },
            onStart = {
                vm.applySetup(
                    draftRank, draftMultipleChoice, draftBest, draftWorst,
                    draftColor, draftFeedback, draftDifficulty, draftTargetWinrate,
                    draftRanked,
                )
                showSetup = false
            },
        )
        return
    }
    GameScreenContent(
        s = s,
        actions = GameActions(
            onBoardTap = vm::onBoardTap,
            onNewGame = vm::newGame,
            onUndo = vm::undo,
            onPass = vm::pass,
            onShowFeedback = vm::setShowFeedback,
            onScopeAll = vm::setFeedbackScopeAll,
            onGraphOpen = vm::setGraphOpen,
            onReview = vm::setReviewIdx,
            onApplySetup = vm::applySetup,
            onShowStats = { showStats = true },
            onOpenSetup = { openSetup() },
        ),
        snack = snack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreenContent(s: GameState, actions: GameActions, snack: SnackbarHostState = remember { SnackbarHostState() }) {

    val rIdx = s.reviewIdx
    val reviewing = rIdx != null
    val shownHistory = if (rIdx != null) s.history.take(rIdx) else s.history
    val last = shownHistory.lastOrNull()
    val lastMove = last?.takeIf { it.x >= 0 }?.let { it.x to it.y }
    val feedbackMove = shownHistory.lastOrNull { it.color == s.playerColor && it.x >= 0 }?.let { it.x to it.y }

    // Live overlays: next-turn candidates and persistent feedback share the board.
    // Review overlays come from per-ply history recorded during play.
    val boardCands: List<Candidate>? = if (rIdx != null) {
        s.pastCandidates[rIdx]
    } else {
        if (s.choiceCount == 0) null else s.candidates
    }
    // Stone animation: 90 ms settle on every placement, plus a 150 ms
    // shrink when the play captured (one 240 ms clock, same as demo
    // e_shrink; plain placements match f_place). Game state moves on
    // instantly (bot replies, undo, review all cut it by replacing/clearing
    // fx); this clock draws pixels and nothing else.
    val place = if (reviewing) null else s.placeFx
    val caps = if (reviewing) null else s.captureFx
    val animTotalMs = if (caps != null) PLACE_MS + SHRINK_MS else PLACE_MS
    // The clock is born at 0 on the same composition that delivers the
    // payload, so frame one already shows the arrival pose. (Gating the
    // payload on a clock-started flag showed the rested board for a frame —
    // stone present, captured gone — then jumped back to the start.)
    val fxClock = remember(place?.seq) { Animatable(0f) }
    LaunchedEffect(place?.seq) {
        if (place != null) fxClock.animateTo(1f, animationSpec = tween(animTotalMs, easing = LinearEasing))
    }
    val boardEvals: List<EvaluatedMove>? = if (rIdx != null) {
        if (!s.showFeedback) {
            null
        } else {
            val evals = s.pastEvals.filterKeys { it < rIdx }.maxByOrNull { it.key }?.value
            if (!s.feedbackScopeAll && feedbackMove != null) {
                evals?.filter { it.x == feedbackMove.first && it.y == feedbackMove.second }
            } else {
                evals
            }
        }
    } else if (!s.showFeedback) {
        null
    } else {
        val evals = s.evaluations
        if (!s.feedbackScopeAll && feedbackMove != null) {
            evals?.filter { it.x == feedbackMove.first && it.y == feedbackMove.second }
        } else {
            evals
        }
    }
    val reviewBoard: List<List<Int>> =
        if (reviewing) {
            GoBoard.fromHistory(shownHistory).signMap()
        } else {
            s.boardSignMap
        }

    // Passes leave no stone: the chip names the passer, live and in review.
    // The passer is the move's own color (a pass always flips the side to move).
    val passPrefix = if (last != null && last.x < 0 && s.status == "playing") {
        (if (last.color == 1) "Black" else "White") + " passed · "
    } else {
        ""
    }
    val statusText = when {
        rIdx != null -> "Reviewing move $rIdx of ${s.history.size}" + (if (passPrefix.isNotEmpty()) " · pass" else "")
        // KataGo is the only scorer: count first, then show its authoritative
        // score. No local fallback — a guess presented as the result is worse
        // than admitting the count failed.
        s.status == "finished" && s.scoring -> "Game over · scoring…"
        s.status == "finished" -> s.finalScoreLead?.let { "Game over · " + Scoring.formatLead(it) } ?: "Game over · score unavailable"
        s.isThinking -> "$passPrefix${if (s.toMove == 1) "Black" else "White"} thinking…"
        s.toMove == 1 -> "${passPrefix}Black to play"
        else -> "${passPrefix}White to play"
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("Go 9×9", style = MaterialTheme.typography.titleLarge)
                            Text(
                                headerSubtitle(s),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = actions.onShowStats) {
                            Icon(Icons.Filled.ShowChart, contentDescription = "Stats")
                        }
                        IconButton(onClick = actions.onUndo, enabled = s.history.isNotEmpty()) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                        }
                        TextButton(onClick = actions.onPass, enabled = s.status == "playing" && !reviewing) {
                            Text("Pass")
                        }
                        IconButton(onClick = actions.onOpenSetup) {
                            Icon(Icons.Filled.Refresh, contentDescription = "New game")
                        }
                    },
                )
                // Always composed (transparent when idle) so the board never
                // jumps when the engine starts/stops thinking.
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().alpha(if (s.isThinking) 1f else 0f),
                )
            }
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        Column(
            modifier = Modifier.fillMaxSize().padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AssistChip(onClick = {}, label = { Text(statusText) })

            // Full-bleed board: no card, no padding — same width as the winrate card.
            BoardView(
                boardSignMap = reviewBoard,
                candidates = boardCands,
                evaluations = boardEvals,
                lastMove = lastMove,
                feedbackMove = feedbackMove,
                onVertexClick = actions.onBoardTap,
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                popStones = caps?.stones ?: emptyList(),
                placeFx = place,
                animProgress = fxClock.value,
                animTotalMs = animTotalMs,
            )

            // Feedback scope — only while playing with feedback to scope.
            if (s.status == "playing" && s.evaluations != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = s.showFeedback,
                        onClick = { actions.onShowFeedback(!s.showFeedback) },
                        label = { Text("Feedback") },
                    )
                    FilterChip(
                        selected = !s.feedbackScopeAll,
                        enabled = s.showFeedback,
                        onClick = { actions.onScopeAll(!s.feedbackScopeAll) },
                        label = { Text("Only my move") },
                    )
                }
            }

            ElevatedCard(modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { actions.onGraphOpen(!s.graphOpen) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Winrate", style = MaterialTheme.typography.labelLarge)
                            Icon(
                                if (s.graphOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (s.graphOpen) "Collapse" else "Expand",
                            )
                        }
                        // Percentage is part of the expanded view only.
                        if (s.graphOpen) {
                            val shownW = if (rIdx != null) {
                                s.winrateHistory.getOrNull(rIdx - 1)
                            } else {
                                s.winrateHistory.lastOrNull()
                            }
                            Text(
                                if (shownW != null) "${(shownW * 100).toInt()}%" else "—",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    // The graph IS the review scrubber (drag horizontally).
                    if (s.graphOpen) WinrateGraph(s.winrateHistory, reviewIdx = rIdx, onReview = actions.onReview)
                }
            }
        }
    }

}

