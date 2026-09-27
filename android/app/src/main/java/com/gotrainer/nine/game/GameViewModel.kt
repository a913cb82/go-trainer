package com.gotrainer.nine.game

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gotrainer.nine.data.SettingsRepository
import com.gotrainer.nine.engine.GoEngine
import com.gotrainer.nine.engine.KataGoGtpEngine
import com.gotrainer.nine.engine.ScoreResult
import com.gotrainer.nine.game.RatedGame
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Player-vs-bot flow on the human's chosen colour; the bot plays the other side.
 * needCandidates -> engine.candidates; onPick -> apply local + gaps + winrate + 450ms genmove.
 * Undo rewinds to the human's last move; pass x2 -> finished + engine scoring.
 *
 * KataGo is REQUIRED: every engine call throws loudly when the binary/models are
 * missing, and the error surfaces in the UI. There are no mock fallbacks.
 */
class GameViewModel(app: Application) : AndroidViewModel(app) {
    companion object {
        private const val TAG = "GameVM"
    }

    private val settings = SettingsRepository(app.applicationContext)
    private val katago = KataGoGtpEngine(app.applicationContext)
    private var fetchJob: Job? = null
    /**
     * True when the last appended winrate point is a free-choice placeholder
     * (carry-forward) waiting for the bot's root appraisal of the same board.
     * Cleared on new game / undo / setup, consumed in [botReply].
     */
    private var pendingFreePly = false
    /**
     * Game generation: incremented on newGame so a late engine score from a
     * previous game can neither upgrade nor void the current game's rated
     * record (single active game => last-record ops are safe within a gen).
     */
    private var gameSeq = 0

    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()
    /** Rated-game history for the stats screen (rating folds off this). */
    val ratedHistoryFlow: Flow<List<RatedGame>> = settings.ratedHistory

    init {
        viewModelScope.launch {
            val s = settings.settings.first()
            _state.value = _state.value.copy(
                rank = s.rank,
                multipleChoice = s.multipleChoice,
                bestCount = s.bestCount,
                worstCount = s.worstCount,
                colorChoice = s.colorChoice,
                playerColor = resolveColor(s.colorChoice),
                showFeedback = s.showFeedback,
                graphOpen = s.graphOpen,
                difficulty = s.difficulty,
                targetWinrate = s.targetWinrate,
            )
            updatePlayerRankText()
            // On-device mode needs the staged binary; remote mode needs nothing local.
            if (!katago.binaryPresent()) {
                _state.value = _state.value.copy(
                    error = "KataGo engine not found — push libkatago.so, models and gtp.cfg to the app filesDir",
                )
                return@launch
            }
            // Background preload while the human thinks: a cold start (process
            // spawn, model load, NN-cache warmup) never sits on the first
            // reply's critical path. Skipped when the bot is to move — its
            // reply self-starts the engine with the thinking bar shown.
            // The first query shares the same start mutex, so exactly one
            // engine spawns.
            if (GameFlow.preloadWanted(
                    humanToMove = _state.value.playerColor == 1,
                    engineReady = katago.engineReady.value,
                )
            ) {
                viewModelScope.launch {
                    try {
                        katago.ensureStarted()
                    } catch (e: Exception) {
                        _state.value = _state.value.copy(error = e.message ?: "Engine start failed")
                    }
                }
            }
            // The opener delegates to GameFlow so the White-second path is
            // testable; behaviour is unchanged (candidates are always
            // requested, so the bot never opens for White).
            when (GameFlow.openingAction(_state.value.playerColor)) {
                GameFlow.OpeningAction.REQUEST_CANDIDATES -> requestCandidates()
                GameFlow.OpeningAction.BOT_REPLY -> botReply()
            }
        }
    }

    /** The on-device GTP engine is the only engine — no remote, no mocks. */
    private fun engine(): GoEngine = katago

    private fun resolveColor(choice: ColorChoice): Int = when (choice) {
        ColorChoice.BLACK -> 1
        ColorChoice.WHITE -> -1
        ColorChoice.RANDOM -> if (Random.nextBoolean()) 1 else -1
    }

    /** Engine winrates are BLACK-perspective; the graph and pills show the human's side. */
    private fun asPlayerWinrate(w: Double, playerColor: Int): Double =
        if (playerColor == 1) w else 1.0 - w


    private fun syncBoard(s: GameState): GameState =
        s.copy(boardSignMap = GoBoard.fromHistory(s.history).signMap())

    // ---- actions ----
    /** One-shot engine scoring after pass-pass; chip shows "scoring…" meanwhile. */
    private fun requestFinalScore() {
        val eng = engine()
        val seq = gameSeq
        _state.value = _state.value.copy(scoring = true)
        viewModelScope.launch {
            try {
                val s = _state.value
                if (s.status != "finished") return@launch
                val res: ScoreResult = eng.score(s.boardSignMap, s.history)
                if (seq != gameSeq) return@launch // stale: a new game owns its records
                val cur = _state.value
                if (cur.status == "finished") {
                    _state.value = cur.copy(
                        finalScoreLead = res.scoreLeadBlack,
                        finalOwnership = res.ownership,
                        scoring = false,
                    )
                    // Clean rated finish upgrades the write-ahead loss (win=1,
                    // draw=0.5); undos, losses and suggestions games stay losses.
                    if (!cur.multipleChoice && cur.history.any { it.color == cur.playerColor }) {
                        val upgrade = GameFlow.ratedFinishScore(
                            res.scoreLeadBlack, cur.playerColor == 1, cur.undoUsed, playerMoved = true,
                        )
                        if (upgrade != null) settings.updateLastRated(upgrade)
                        updatePlayerRankText()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "final score failed", e)
                if (seq != gameSeq) return@launch
                // No result, no record: void the pending loss (errors, never losses).
                settings.dropLastRated()
                updatePlayerRankText()
                _state.value = _state.value.copy(scoring = false, error = "Final scoring failed")
            }
        }
    }

    fun newGame() {
        fetchJob?.cancel()
        pendingFreePly = false
        gameSeq++
        viewModelScope.launch { updatePlayerRankText() }
        val playerColor = resolveColor(_state.value.colorChoice)
        _state.value = syncBoard(
            _state.value.copy(
                history = emptyList(), toMove = 1, playerColor = playerColor,
                candidates = null, evaluations = null,
                pastCandidates = emptyMap(), pastEvals = emptyMap(),
                finalScoreLead = null, finalOwnership = null, scoring = false,
                winrateHistory = emptyList(), status = "playing", passing = 0,
                isThinking = false, error = null, reviewIdx = null,
                undoUsed = false,
            )
        )
        // Reset the persistent engine board first (GTP tree starts fresh), then
        // play: human-Black fetches choices, human-White lets the bot open.
        viewModelScope.launch {
            try {
                engine().newGame(_state.value.rank)
            } catch (e: Exception) {
                Log.e(TAG, "engine newGame failed", e)
                _state.value = _state.value.copy(error = e.message ?: "Engine error")
                return@launch
            }
            if (playerColor == 1) requestCandidates() else botReply()
        }
    }

    /**
     * The New game sheet stages everything and applies it here in one go —
     * starting a fresh game on the chosen side. The only live setting is the
     * Feedback chip (setShowFeedback), which never restarts anything.
     */
    fun applySetup(
        rank: Rank,
        multipleChoice: Boolean,
        best: Int,
        worst: Int,
        color: ColorChoice,
        feedback: Boolean,
        difficulty: Difficulty,
        targetWinrate: Int,
    ) {
        val b = best.coerceIn(0, 5)
        val w = worst.coerceIn(0, 5)
        val t = targetWinrate.coerceIn(10, 90)
        viewModelScope.launch {
            settings.setMultipleChoice(multipleChoice)
            settings.setBestCount(b)
            settings.setWorstCount(w)
            settings.setColorChoice(color)
            settings.setShowFeedback(feedback)
            settings.setDifficulty(difficulty)
            settings.setTargetWinrate(t)
            // Automatch overrides the draft rung: nearest rung to the target
            // winrate off the live player rating. The pick persists as the
            // rung, so reopening the sheet in Fixed shows what was played.
            var picked = rank
            if (difficulty == Difficulty.AUTOMATCH) {
                val rating = PlayerRating.rate(settings.ratedHistory.first())
                val rung = GameFlow.automatchRung(rating.rating, t)
                picked = Rank.ALL[rung]
            }
            settings.setRank(picked)
            pendingFreePly = false
            _state.value = _state.value.copy(
                rank = picked, multipleChoice = multipleChoice,
                bestCount = b, worstCount = w,
                colorChoice = color, showFeedback = feedback,
                difficulty = difficulty, targetWinrate = t,
            )
            updatePlayerRankText()
            newGame()
        }
    }

    /** Live player rank text (rating folds are milliseconds; recompute freely). */
    private suspend fun updatePlayerRankText() {
        val r = PlayerRating.rate(settings.ratedHistory.first())
        val dev = BotRatings.rankDeviation(r.rating, r.rd).roundToInt()
        _state.value = _state.value.copy(
            playerRankText = "${BotRatings.playerLabel(r.rating, r.rd)} ±$dev",
        )
    }

    fun setGraphOpen(v: Boolean) {
        viewModelScope.launch { settings.setGraphOpen(v) }
        _state.value = _state.value.copy(graphOpen = v)
    }

    fun setShowFeedback(v: Boolean) {
        viewModelScope.launch { settings.setShowFeedback(v) }
        _state.value = _state.value.copy(showFeedback = v)
    }

    fun setFeedbackScopeAll(v: Boolean) {
        _state.value = _state.value.copy(feedbackScopeAll = v)
    }

    fun setReviewIdx(v: Int?) {
        _state.value = _state.value.copy(reviewIdx = v)
    }

    fun clearEvaluations() {
        _state.value = _state.value.copy(evaluations = null)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun pass() {
        val s = _state.value
        if (s.status != "playing" || s.reviewIdx != null) return
        if (s.toMove == s.playerColor) appendRatedRecord(s)
        val p = s.passing + 1
        if (p >= 2) {
            _state.value = s.copy(status = "finished", passing = p, candidates = null)
            requestFinalScore()
            return
        }
        val passColor = s.toMove
        val hist = s.history + MoveRec(-1, -1, s.toMove)
        // The carried-forward point is a placeholder: the bot's root appraises
        // this exact board moments later and backfills it (see botReply).
        pendingFreePly = true
        _state.value = syncBoard(
            s.copy(
                history = hist, toMove = -s.toMove, passing = p, candidates = null,
                winrateHistory = GameFlow.humanPassWinrates(s.winrateHistory),
            )
        )
        // Sync the pass into the persistent board, then the bot replies
        // (with its own move, or a pass back -> finished).
        viewModelScope.launch {
            try {
                engine().playMove(passColor, -1, -1)
            } catch (e: Exception) {
                Log.e(TAG, "engine pass sync failed", e)
            }
            botReply()
        }
    }

    fun undo() {
        val s = _state.value
        if (s.history.isEmpty()) return
        val lastPlayerIdx = s.history.indexOfLast { it.color == s.playerColor && it.x >= 0 }
        if (lastPlayerIdx < 0) return
        val undone = s.history.size - lastPlayerIdx
        val removedPlies = undone
        val hist = s.history.subList(0, lastPlayerIdx)
        pendingFreePly = false
        _state.value = syncBoard(
            s.copy(
                history = hist.toList(), toMove = s.playerColor, candidates = null, evaluations = null,
                undoUsed = true,
                pastCandidates = s.pastCandidates.filterKeys { it < lastPlayerIdx },
                pastEvals = s.pastEvals.filterKeys { it < lastPlayerIdx },
                finalScoreLead = null, finalOwnership = null, scoring = false,
                status = "playing", passing = 0,
                winrateHistory = s.winrateHistory.dropLast(undone),
                reviewIdx = null,
            )
        )
        // Rewind the persistent engine board (preserves its search tree), then refetch.
        viewModelScope.launch {
            try {
                engine().undoMoves(removedPlies)
            } catch (e: Exception) {
                Log.e(TAG, "engine undo failed", e)
            }
            requestCandidates()
        }
    }

    fun onBoardTap(x: Int, y: Int) {
        val s = _state.value
        if (s.status != "playing" || s.toMove != s.playerColor || s.reviewIdx != null || s.isThinking) return
        if (s.choiceCount == 0) {
            // Free choice (0 moves): any legal point, then bot replies.
            val pos = currentPosition()
            if (pos.board.play(s.playerColor, x, y, pos.hashes) < 0) return
            applyPlayerMove(x, y, null)
            return
        }
        val cands = s.candidates ?: return
        val cand = cands.firstOrNull { it.x == x && it.y == y } ?: return
        onPick(cand.x, cand.y)
    }

    /** Board + every position hash in one replay (superko legality, one pass). */
    private fun currentPosition(): GoBoard.ReplayResult = GoBoard.replay(_state.value.history)

    private fun onPick(x: Int, y: Int) {
        val s = _state.value
        val was = s.candidates ?: return
        val pos = currentPosition()
        if (pos.board.play(s.playerColor, x, y, pos.hashes) < 0) {
            _state.value = s.copy(error = "Illegal move")
            return
        }
        val cand = was.firstOrNull { it.x == x && it.y == y }
        applyPlayerMove(x, y, cand ?: was.first())
    }

    /**
     * Write-ahead loss record on the player's first ply of a rated
     * (free-choice) game. Abandons need no end-of-game hook: the loss is
     * already stored; clean finishes upgrade it (see requestFinalScore).
     */
    private fun appendRatedRecord(s: GameState) {
        if (!GameFlow.ratedAppendWanted(s.multipleChoice, s.status, s.history, s.playerColor)) return
        val rec = RatedGame(
            System.currentTimeMillis(), s.rank.id, s.playerColor == 1, 0.0,
        )
        viewModelScope.launch { settings.appendRated(rec) }
    }

    private fun applyPlayerMove(x: Int, y: Int, picked: com.gotrainer.nine.game.Candidate?) {
        val s = _state.value
        appendRatedRecord(s)
        val was = s.candidates
        val evals = if (was != null && s.choiceCount != 0) CandidateSelector.toEvaluated(was) else s.evaluations
        val hist = s.history + MoveRec(x, y, s.playerColor, picked, null)
        // Candidates are stored in human-perspective winrate (converted at fetch).
        // Free choice has no per-move analysis: carry the last known value forward
        // as a placeholder — the bot's genmove_analyze root appraises this exact
        // board moments later and backfills it (same search, zero extra latency).
        // A fabricated 0.5 made the graph read 50% on every black move.
        val win = picked?.strongWinrate ?: (s.winrateHistory.lastOrNull() ?: 0.5)
        pendingFreePly = picked == null
        val pastEvals = if (evals != null && s.choiceCount != 0) s.pastEvals + (s.history.size to evals) else s.pastEvals
        _state.value = syncBoard(
            s.copy(
                history = hist, toMove = -s.playerColor, passing = 0, candidates = null,
                evaluations = evals,
                pastEvals = pastEvals,
                winrateHistory = s.winrateHistory + win,
            )
        )
        // Sync the move into the persistent engine board, then reply immediately
        // (no UI delay — BadukAI has none; the visit+time caps bound the reply).
        viewModelScope.launch {
            try {
                engine().playMove(s.playerColor, x, y)
            } catch (e: Exception) {
                Log.e(TAG, "engine move sync failed", e)
            }
            botReply()
        }
    }

    private fun botReply() {
        val eng = engine()
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _state.value = _state.value.copy(isThinking = true, error = null)
            try {
                val s = _state.value
                if (s.status != "playing") return@launch
                val res = eng.genMove(s.boardSignMap, s.toMove, s.rank, s.history)
                val cur = _state.value
                // Same search, two appraisals (BLACK -> human perspective): the root
                // is the board after the human's move, the played value after the bot's.
                val prevW = asPlayerWinrate(res.rootWinrate, cur.playerColor)
                val playedW = asPlayerWinrate(res.winrate, cur.playerColor)
                if (res.pass) {
                    val p = cur.passing + 1
                    val hist = cur.history + MoveRec(-1, -1, cur.toMove)
                    // Even when the bot passes, its root appraises our last move.
                    val passStep = GameFlow.botReplyWinrates(cur.winrateHistory, pendingFreePly, prevW, 0.0, botPassed = true)
                    pendingFreePly = passStep.pendingFreePly
                    val winrates = passStep.winrates
                    _state.value = if (p >= 2) {
                        val done = syncBoard(cur.copy(history = hist, status = "finished", passing = p, isThinking = false, winrateHistory = winrates))
                        _state.value = done
                        requestFinalScore()
                        return@launch
                    } else {
                        val next = syncBoard(cur.copy(history = hist, toMove = -cur.toMove, passing = p, isThinking = false, winrateHistory = winrates))
                        _state.value = next
                        requestCandidates()
                        return@launch
                    }
                } else {
                    // Bot legality under the same positional superko (KataGo
                    // enforces it too under Chinese rules; this is the backstop).
                    val pos = GoBoard.replay(cur.history)
                    if (pos.board.play(cur.toMove, res.x, res.y, pos.hashes) < 0) {
                        Log.w(TAG, "bot illegal move ${res.x},${res.y} — passing")
                        val hist = cur.history + MoveRec(-1, -1, cur.toMove)
                        _state.value = syncBoard(cur.copy(history = hist, toMove = -cur.toMove, isThinking = false))
                    } else {
                        val hist = cur.history + MoveRec(res.x, res.y, cur.toMove)
                        // Backfill a free-choice placeholder with the root appraisal,
                        // then append the played-move appraisal for the bot's ply.
                        val moveStep = GameFlow.botReplyWinrates(cur.winrateHistory, pendingFreePly, prevW, playedW, botPassed = false)
                        pendingFreePly = moveStep.pendingFreePly
                        val winrates = moveStep.winrates
                        _state.value = syncBoard(
                            cur.copy(
                                history = hist, toMove = -cur.toMove, passing = 0,
                                isThinking = false,
                                winrateHistory = winrates,
                            )
                        )
                    }
                }
                requestCandidates()
            } catch (e: Exception) {
                Log.e(TAG, "bot reply failed", e)
                _state.value = _state.value.copy(isThinking = false, error = e.message ?: "Engine error")
            }
        }
    }

    private fun requestCandidates() {
        val s = _state.value
        if (s.choiceCount == 0 || s.reviewIdx != null || s.status != "playing" || s.toMove != s.playerColor) return
        if (s.candidates != null) return
        val eng = engine()
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _state.value = _state.value.copy(isThinking = true, error = null)
            try {
                val cur = _state.value
                val atPly = cur.history.size
                val raw = eng.candidates(cur.boardSignMap, cur.playerColor, cur.rank, cur.bestCount, cur.worstCount, cur.history)
                // Pills and the graph speak for the human; the engine reports BLACK.
                val moves = if (cur.playerColor == 1) raw else raw.map { it.copy(strongWinrate = 1.0 - it.strongWinrate) }
                _state.value = _state.value.copy(
                    candidates = moves,
                    pastCandidates = _state.value.pastCandidates + (atPly to moves),
                    isThinking = false,
                )
            } catch (e: Exception) {
                Log.e(TAG, "candidates failed", e)
                _state.value = _state.value.copy(isThinking = false, error = e.message ?: "Engine error")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        katago.stop()
    }
}
