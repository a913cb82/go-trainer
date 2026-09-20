package com.gotrainer.nine.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.gotrainer.nine.game.EvaluatedMove
import com.gotrainer.nine.game.GameState
import com.gotrainer.nine.game.label
import com.gotrainer.nine.game.GameViewModel
import com.gotrainer.nine.game.GoBoard
import com.gotrainer.nine.game.Rank
import com.gotrainer.nine.game.availableStrategies
import com.gotrainer.nine.game.Scoring
import com.gotrainer.nine.game.Strategy

private fun choicesLabel(n: Int): String = if (n == 0) "free choice" else "$n choices"

/** Callbacks so the pure content below is screenshot-friendly (no ViewModel). */
data class GameActions(
    val onBoardTap: (Int, Int) -> Unit = { _, _ -> },
    val onNewGame: () -> Unit = {},
    val onUndo: () -> Unit = {},
    val onPass: () -> Unit = {},
    val onShowFeedback: (Boolean) -> Unit = {},
    val onScopeAll: (Boolean) -> Unit = {},
    val onReview: (Int?) -> Unit = {},
    val onApplySetup: (Rank, Int, ColorChoice, Strategy, Boolean) -> Unit =
        { _, _, _, _, _ -> },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(vm: GameViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }

    s.error?.let { err ->
        LaunchedEffect(err) {
            snack.showSnackbar(err)
            vm.clearError()
        }
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
            onReview = vm::setReviewIdx,
            onApplySetup = vm::applySetup,
        ),
        snack = snack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreenContent(s: GameState, actions: GameActions, snack: SnackbarHostState = remember { SnackbarHostState() }) {
    var sheetOpen by remember { mutableStateOf(false) }
    var graphOpen by remember { mutableStateOf(true) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Staged setup: the sheet edits drafts; nothing applies until Start game.
    var draftRank by remember { mutableStateOf(s.rank) }
    var draftN by remember { mutableStateOf(s.n) }
    var draftColor by remember { mutableStateOf(s.colorChoice) }
    var draftStrategy by remember { mutableStateOf(s.strategy) }
    var draftFeedback by remember { mutableStateOf(s.showFeedback) }
    fun openSetup() {
        draftRank = s.rank
        draftN = s.n
        draftColor = s.colorChoice
        draftStrategy = s.strategy
        draftFeedback = s.showFeedback
        sheetOpen = true
    }

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
        if (s.n == 0) null else s.candidates
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

    val statusText = when {
        rIdx != null -> "Reviewing move $rIdx of ${s.history.size}"
        // Never flash the local estimate as if it were the result: count first,
        // then show the engine's authoritative score (local only if scoring fails).
        s.status == "finished" && s.scoring -> "Game over · scoring…"
        s.status == "finished" -> "Game over · " + Scoring.formatLead(s.finalScoreLead ?: Scoring.estimate(reviewBoard).diff)
        s.isThinking -> "${if (s.toMove == 1) "Black" else "White"} thinking…"
        s.toMove == 1 -> "Black to play"
        else -> "White to play"
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("Go 9×9", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "${s.rank.id} · ${choicesLabel(s.n)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = actions.onUndo, enabled = s.history.isNotEmpty()) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                        }
                        TextButton(onClick = actions.onPass, enabled = s.status == "playing" && !reviewing) {
                            Text("Pass")
                        }
                        IconButton(onClick = { openSetup() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "New game")
                        }
                    },
                )
                if (s.isThinking) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
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

            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.widthIn(max = 560.dp),
            ) {
                BoardView(
                    boardSignMap = reviewBoard,
                    candidates = boardCands,
                    evaluations = boardEvals,
                    lastMove = lastMove,
                    feedbackMove = feedbackMove,
                    onVertexClick = actions.onBoardTap,
                    modifier = Modifier.padding(10.dp),
                )
            }

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
                        modifier = Modifier.fillMaxWidth().clickable { graphOpen = !graphOpen },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Winrate", style = MaterialTheme.typography.labelLarge)
                            Icon(
                                if (graphOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (graphOpen) "Collapse" else "Expand",
                            )
                        }
                        // Percentage is part of the expanded view only.
                        if (graphOpen) {
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
                    if (graphOpen) WinrateGraph(s.winrateHistory, reviewIdx = rIdx, onReview = actions.onReview)
                }
            }
        }
    }

    if (sheetOpen) {
        ModalBottomSheet(onDismissRequest = { sheetOpen = false }, sheetState = sheetState) {
            OpponentSheetContent(
                s = s,
                draftRank = draftRank,
                onDraftRank = { draftRank = it },
                draftN = draftN,
                onDraftN = { draftN = it },
                draftColor = draftColor,
                onDraftColor = { draftColor = it },
                draftStrategy = draftStrategy,
                onDraftStrategy = { draftStrategy = it },
                draftFeedback = draftFeedback,
                onDraftFeedback = { draftFeedback = it },
                onStart = {
                    actions.onApplySetup(draftRank, draftN, draftColor, draftStrategy, draftFeedback)
                    sheetOpen = false
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OpponentSheetContent(
    s: GameState,
    draftRank: Rank,
    onDraftRank: (Rank) -> Unit,
    draftN: Int,
    onDraftN: (Int) -> Unit,
    draftColor: ColorChoice,
    onDraftColor: (ColorChoice) -> Unit,
    draftStrategy: Strategy,
    onDraftStrategy: (Strategy) -> Unit,
    draftFeedback: Boolean,
    onDraftFeedback: (Boolean) -> Unit,
    onStart: () -> Unit,
) {
    val freeChoice = draftN == 0
    var rankOpen by remember { mutableStateOf(false) }
    val botSide = when (draftColor) {
        ColorChoice.BLACK -> "White"
        ColorChoice.WHITE -> "Black"
        ColorChoice.RANDOM -> "White or Black"
    }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("New game", style = MaterialTheme.typography.titleLarge)
        Text(
            if (freeChoice) {
                "The bot plays $botSide like a ${draftRank.id} human. You play anywhere — no candidate choices."
            } else {
                "The bot plays $botSide like a ${draftRank.id} human. You get $draftN candidate moves — some good, some tempting."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

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

        ExposedDropdownMenuBox(expanded = rankOpen, onExpandedChange = { rankOpen = it }) {
            OutlinedTextField(
                value = draftRank.id,
                onValueChange = {},
                readOnly = true,
                label = { Text("Rank") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = rankOpen) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                singleLine = true,
            )
            ExposedDropdownMenu(expanded = rankOpen, onDismissRequest = { rankOpen = false }) {
                for (r in Rank.ALL) {
                    DropdownMenuItem(
                        text = { Text(r.id) },
                        onClick = {
                            onDraftRank(r)
                            rankOpen = false
                        },
                    )
                }
            }
        }

        Text("Choices per move", style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow {
            listOf(0, 3, 5).forEachIndexed { i, v ->
                SegmentedButton(
                    selected = draftN == v,
                    onClick = { onDraftN(v) },
                    shape = SegmentedButtonDefaults.itemShape(i, 3),
                    label = { Text(if (v == 0) "0" else "$v") },
                )
            }
        }
        Text(
            "0 means free choice: play anywhere, no candidate moves.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Training style and instant feedback only exist when the game offers
        // candidate moves; in free choice they disappear instead of greying out.
        if (!freeChoice) Column {
            Text("Training style", style = MaterialTheme.typography.labelLarge)
            Column {
                // Count-based options adapt to n (n=3 offers four, n=5 six).
                val options = availableStrategies(draftN)
                val shownN = maxOf(draftN, 5)
                for (st in options) {
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = draftStrategy == st, onClick = { onDraftStrategy(st) }, enabled = !freeChoice)
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(st.label(shownN), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                st.blurb(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        if (!freeChoice) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
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

        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Text("Start game")
        }
    }
}
