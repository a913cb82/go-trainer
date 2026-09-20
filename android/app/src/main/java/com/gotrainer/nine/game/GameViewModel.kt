package com.gotrainer.nine.game

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gotrainer.nine.data.SettingsRepository
import com.gotrainer.nine.engine.GoEngine
import com.gotrainer.nine.engine.KataGoGtpEngine
import com.gotrainer.nine.engine.RemoteEngine
import com.gotrainer.nine.engine.ScoreResult
import kotlin.random.Random
import kotlinx.coroutines.Job
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

    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val s = settings.settings.first()
            _state.value = _state.value.copy(
                rank = s.rank, n = s.n, colorChoice = s.colorChoice,
                playerColor = resolveColor(s.colorChoice),
                engineMode = s.engineMode, serverUrl = s.serverUrl,
                strategy = s.strategy, showFeedback = s.showFeedback,
            )
            // On-device mode needs the staged binary; remote mode needs nothing local.
            if (s.engineMode == EngineMode.DEVICE && !katago.binaryPresent()) {
                _state.value = _state.value.copy(
                    error = "KataGo engine not found — push libkatago.so, models and gtp.cfg to the app filesDir",
                )
                return@launch
            }
            // Pre-warm the on-device engine while the UI renders; the first query
            // shares the same start mutex, so exactly one engine spawns.
            // Remote mode needs nothing local — don't touch the binary.
            if (s.engineMode != EngineMode.DEVICE) {
                requestCandidates()
                return@launch
            }
            viewModelScope.launch {
                try {
                    katago.ensureStarted()
                } catch (e: Exception) {
                    _state.value = _state.value.copy(error = e.message ?: "Engine start failed")
                }
            }
            requestCandidates()
        }
    }

    private fun engine(): GoEngine {
        val s = _state.value
        return if (s.engineMode == EngineMode.REMOTE) RemoteEngine(s.serverUrl) else katago
    }

    private fun resolveColor(choice: ColorChoice): Int = when (choice) {
        ColorChoice.BLACK -> 1
        ColorChoice.WHITE -> -1
        ColorChoice.RANDOM -> if (Random.nextBoolean()) 1 else -1
    }

    /** Engine winrates are BLACK-perspective; the graph and pills show the human's side. */
    private fun asPlayerWinrate(w: Double, playerColor: Int): Double =
        if (playerColor == 1) w else 1.0 - w

    private fun board(): GoBoard = GoBoard.fromHistory(_state.value.history)

    private fun syncBoard(s: GameState): GameState =
        s.copy(boardSignMap = GoBoard.fromHistory(s.history).signMap())

    // ---- actions ----
    /** One-shot engine scoring after pass-pass; chip shows the local estimate meanwhile. */
    private fun requestFinalScore() {
        val eng = engine()
        viewModelScope.launch {
            try {
                val s = _state.value
                if (s.status != "finished") return@launch
                val res: ScoreResult = eng.score(s.boardSignMap, s.history)
                val cur = _state.value
                if (cur.status == "finished") {
                    _state.value = cur.copy(finalScoreLead = res.scoreLeadBlack, finalOwnership = res.ownership)
                }
            } catch (e: Exception) {
                Log.e(TAG, "final score failed, keeping local estimate", e)
            }
        }
    }

    fun newGame() {
        fetchJob?.cancel()
        val playerColor = resolveColor(_state.value.colorChoice)
        _state.value = syncBoard(
            _state.value.copy(
                history = emptyList(), toMove = 1, playerColor = playerColor,
                candidates = null, evaluations = null,
                pastCandidates = emptyMap(), pastEvals = emptyMap(),
                finalScoreLead = null, finalOwnership = null,
                winrateHistory = emptyList(), status = "playing", passing = 0,
                isThinking = false, error = null, reviewIdx = null,
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
        n: Int,
        color: ColorChoice,
        engineMode: EngineMode,
        serverUrl: String,
        strategy: Strategy,
        feedback: Boolean,
    ) {
        val moves = if (n == 0 || n == 3 || n == 5) n else 5
        val url = serverUrl.trim().trimEnd('/').ifEmpty { RemoteEngine.DEFAULT_URL }
        viewModelScope.launch {
            settings.setRank(rank)
            settings.setN(moves)
            settings.setColorChoice(color)
            settings.setEngineMode(engineMode)
            settings.setServerUrl(url)
            settings.setStrategy(strategy)
            settings.setShowFeedback(feedback)
        }
        _state.value = _state.value.copy(
            rank = rank, n = moves, colorChoice = color,
            engineMode = engineMode, serverUrl = url,
            strategy = strategy, showFeedback = feedback,
        )
        newGame()
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
        val p = s.passing + 1
        if (p >= 2) {
            _state.value = s.copy(status = "finished", passing = p, candidates = null)
            requestFinalScore()
            return
        }
        val passColor = s.toMove
        val hist = s.history + MoveRec(-1, -1, s.toMove)
        _state.value = syncBoard(
            s.copy(history = hist, toMove = -s.toMove, passing = p, candidates = null)
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
        _state.value = syncBoard(
            s.copy(
                history = hist.toList(), toMove = s.playerColor, candidates = null, evaluations = null,
                pastCandidates = s.pastCandidates.filterKeys { it < lastPlayerIdx },
                pastEvals = s.pastEvals.filterKeys { it < lastPlayerIdx },
                finalScoreLead = null, finalOwnership = null,
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
        if (s.n == 0) {
            // Free choice (0 moves): any legal point, then bot replies.
            val b = board()
            if (b.play(s.playerColor, x, y, currentKo()) < 0) return
            applyPlayerMove(x, y, null)
            return
        }
        val cands = s.candidates ?: return
        val cand = cands.firstOrNull { it.x == x && it.y == y } ?: return
        onPick(cand.x, cand.y)
    }

    private fun currentKo(): Pair<Int, Int>? {
        // Recompute ko ban from history replay.
        val hist = _state.value.history
        var ko: Pair<Int, Int>? = null
        val b = GoBoard(9)
        for (m in hist) {
            if (m.x < 0) {
                ko = null
                continue
            }
            val before = b.copy()
            if (b.play(m.color, m.x, m.y, ko) < 0) continue
            ko = GoBoard.detectKo(before, b, m.x, m.y)
        }
        return ko
    }

    private fun onPick(x: Int, y: Int) {
        val s = _state.value
        val was = s.candidates ?: return
        val b = board()
        if (b.play(s.playerColor, x, y, currentKo()) < 0) {
            _state.value = s.copy(error = "Illegal move")
            return
        }
        val cand = was.firstOrNull { it.x == x && it.y == y }
        applyPlayerMove(x, y, cand ?: was.first())
    }

    private fun applyPlayerMove(x: Int, y: Int, picked: com.gotrainer.nine.game.Candidate?) {
        val s = _state.value
        val was = s.candidates
        val evals = if (was != null && s.n != 0) CandidateSelector.toEvaluated(was) else s.evaluations
        val hist = s.history + MoveRec(x, y, s.playerColor, picked, null)
        // Candidates are stored in human-perspective winrate (converted at fetch).
        val win = (picked?.strongWinrate) ?: 0.5
        val pastEvals = if (evals != null && s.n != 0) s.pastEvals + (s.history.size to evals) else s.pastEvals
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
                if (res.pass) {
                    val p = cur.passing + 1
                    val hist = cur.history + MoveRec(-1, -1, cur.toMove)
                    _state.value = if (p >= 2) {
                        val done = syncBoard(cur.copy(history = hist, status = "finished", passing = p, isThinking = false))
                        _state.value = done
                        requestFinalScore()
                        return@launch
                    } else {
                        val next = syncBoard(cur.copy(history = hist, toMove = -cur.toMove, passing = p, isThinking = false))
                        _state.value = next
                        requestCandidates()
                        return@launch
                    }
                } else {
                    val b = GoBoard.fromHistory(cur.history)
                    // Recompute ko for bot legality
                    var ko: Pair<Int, Int>? = null
                    val tmp = GoBoard(9)
                    for (m in cur.history) {
                        if (m.x < 0) {
                            ko = null
                            continue
                        }
                        val before = tmp.copy()
                        if (tmp.play(m.color, m.x, m.y, ko) < 0) continue
                        ko = GoBoard.detectKo(before, tmp, m.x, m.y)
                    }
                    if (b.play(cur.toMove, res.x, res.y, ko) < 0) {
                        Log.w(TAG, "bot illegal move ${res.x},${res.y} — passing")
                        val hist = cur.history + MoveRec(-1, -1, cur.toMove)
                        _state.value = syncBoard(cur.copy(history = hist, toMove = -cur.toMove, isThinking = false))
                    } else {
                        val hist = cur.history + MoveRec(res.x, res.y, cur.toMove)
                        _state.value = syncBoard(
                            cur.copy(
                                history = hist, toMove = -cur.toMove, passing = 0,
                                isThinking = false,
                                winrateHistory = cur.winrateHistory + asPlayerWinrate(res.winrate, cur.playerColor),
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
        if (s.n == 0 || s.reviewIdx != null || s.status != "playing" || s.toMove != s.playerColor) return
        if (s.candidates != null) return
        val eng = engine()
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _state.value = _state.value.copy(isThinking = true, error = null)
            try {
                val cur = _state.value
                val atPly = cur.history.size
                val raw = eng.candidates(cur.boardSignMap, cur.playerColor, cur.rank, cur.n, cur.strategy, cur.history)
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
