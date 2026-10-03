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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Player-vs-bot free play on the human's chosen colour; the bot plays the
 * other side. Tap anywhere -> apply local + winrate placeholder -> bot
 * replies (its root appraisal backfills the placeholder). Undo rewinds to
 * the human's last move; pass x2 -> finished + engine scoring.
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
     * Game generation: incremented on newGame so a late engine score from a
     * previous game can neither upgrade nor void the current game's rated
     * record (single active game => last-record ops are safe within a gen).
     */
    private var gameSeq = 0

    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()
    /** Rated-game history for the stats screen (rating folds off this). */
    val ratedHistoryFlow: Flow<List<RatedGame>> = settings.ratedHistory
    /** Cached causal rating curve (per-point versions; Stats backfills gaps). */
    val ratedCurveFlow: Flow<List<RatingCurve.CurvePoint>> = settings.ratedCurve

    init {
        viewModelScope.launch {
            val s = settings.settings.first()
            // Cold start resumes the saved game exactly (board rebuilds from
            // history); corrupt or absent blobs start fresh from settings.
            val restored = settings.savedGame.first()?.let { GameStateSerde.decode(it) }
            if (restored != null) {
                _state.value = syncBoard(
                    restored.copy(isThinking = false, scoring = false, error = null)
                )
            } else {
                _state.value = _state.value.copy(
                    rank = s.rank,
                    colorChoice = s.colorChoice,
                    playerColor = resolveColor(s.colorChoice),
                    ranked = s.ranked,
                    graphOpen = s.graphOpen,
                    difficulty = s.difficulty,
                    targetWinrate = s.targetWinrate,
                )
            }
            updatePlayerRankText()
            // Every state change persists (cheap blob); launched after the
            // restore so the first emission writes back the restored game.
            viewModelScope.launch {
                _state.collect { settings.saveGame(GameStateSerde.encode(it)) }
            }
            // On-device mode needs the staged binary; remote mode needs nothing local.
            if (!katago.binaryPresent()) {
                _state.value = _state.value.copy(
                    error = "KataGo engine not found — push libkatago.so, models and gtp.cfg to the app filesDir",
                )
                return@launch
            }
            val resumed = restored != null
            val cur = _state.value
            // Resumed games pick up exactly where they died: finished games
            // recount only when the kill beat the score (the write-ahead loss
            // is already in history, so scoring still settles it); live games
            // re-issue the in-flight bot reply. A kill mid-game is an abandon:
            // the pending record stays a loss, undo poison stays poisoned.
            if (resumed && cur.status == "finished") {
                if (cur.finalScoreLead == null) requestFinalScore()
                return@launch
            }
            // Background preload while the human thinks: a cold start (process
            // spawn, model load, NN-cache warmup) never sits on the first
            // reply's critical path. Skipped when the bot is to move — its
            // reply self-starts the engine with the thinking bar shown.
            // The first query shares the same start mutex, so exactly one
            // engine spawns.
            val humanToMove = cur.toMove == cur.playerColor
            if (GameFlow.preloadWanted(
                    humanToMove = humanToMove,
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
            if (!resumed) {
                // The opener delegates to GameFlow so the White-second path
                // is testable; a human-Black game waits for a tap instead.
                if (GameFlow.botOpens(_state.value.playerColor)) botReply()
                return@launch
            }
            // The persistent engine board died with the process: replay the
            // moves back in, then re-issue the in-flight query. Bails if the
            // user started a new game while the engine was still warming.
            // The replay includes the cold-start load, so raise the thinking
            // indicator now when a query will follow — otherwise the board
            // sits dead for seconds with no feedback.
            val seq = gameSeq
            if (GameFlow.resyncThinkingWanted(toMove = cur.toMove, playerColor = cur.playerColor)
            ) {
                _state.value = cur.copy(isThinking = true)
            }
            viewModelScope.launch {
                if (seq != gameSeq) return@launch
                try {
                    engine().newGame(cur.rank)
                    // Re-check per move: a new game started mid-replay must
                    // stop the stale loop before it stamps old stones onto
                    // the new board (see StaleResyncTest). resyncMoves covers
                    // stale-at-start; the live check covers mid-loop.
                    for (m in GameFlow.resyncMoves(cur.history, seq, gameSeq)) {
                        if (seq != gameSeq) return@launch
                        engine().playMove(m.color, m.x, m.y)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "engine resync failed", e)
                    if (seq == gameSeq)
                        _state.value = _state.value.copy(isThinking = false, error = e.message ?: "Engine error")
                    return@launch
                }
                if (seq != gameSeq) return@launch
                if (cur.toMove != cur.playerColor) botReply()
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


    private fun syncBoard(s: GameState, fx: CaptureFx? = null, place: PlaceFx? = null): GameState =
        s.copy(boardSignMap = GoBoard.fromHistory(s.history).signMap(), captureFx = fx, placeFx = place)

    /** Pop payload for a real placement: diff the board, null when nothing was taken. */
    private fun captureFxFor(before: List<List<Int>>, hist: List<MoveRec>): CaptureFx? =
        GoBoard.capturedBy(before, GoBoard.fromHistory(hist).signMap())
            .takeIf { it.isNotEmpty() }
            ?.let { CaptureFx(it, hist.size) }

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
                    // draw=0.5); undos, losses and unranked games stay losses
                    // (or never wrote a record at all).
                    if (cur.ranked && cur.history.any { it.color == cur.playerColor }) {
                        val upgrade = GameFlow.ratedFinishScore(
                            res.scoreLeadBlack, cur.playerColor == 1, cur.undoUsed, playerMoved = true,
                        )
                        if (upgrade != null) {
                            val hist = settings.ratedHistory.first()
                            val newHist = hist.dropLast(1) + hist.last().copy(score = upgrade)
                            val r = PlayerWhr.rate(newHist)
                            settings.updateLastRated(upgrade, curvePointFor(newHist, r))
                            updatePlayerRankText(r)
                        } else updatePlayerRankText()
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
        gameSeq++
        viewModelScope.launch { updatePlayerRankText() }
        val playerColor = resolveColor(_state.value.colorChoice)
        _state.value = syncBoard(
            _state.value.copy(
                history = emptyList(), toMove = 1, playerColor = playerColor,
                finalScoreLead = null, finalOwnership = null, scoring = false,
                winrateHistory = emptyList(), status = "playing", passing = 0,
                isThinking = false, error = null, reviewIdx = null,
                undoUsed = false, pendingFreePly = false,
            )
        )
        // Reset the persistent engine board first (GTP tree starts fresh), then
        // play: human-White lets the bot open, human-Black waits for a tap.
        viewModelScope.launch {
            try {
                engine().newGame(_state.value.rank)
            } catch (e: Exception) {
                Log.e(TAG, "engine newGame failed", e)
                _state.value = _state.value.copy(error = e.message ?: "Engine error")
                return@launch
            }
            if (playerColor != 1) botReply()
        }
    }

    /**
     * The New game screen stages everything and applies it here in one go —
     * starting a fresh game on the chosen side.
     */
    fun applySetup(
        rank: Rank,
        color: ColorChoice,
        difficulty: Difficulty,
        targetWinrate: Int,
        ranked: Boolean,
    ) {
        val t = targetWinrate.coerceIn(10, 90)
        viewModelScope.launch {
            settings.setColorChoice(color)
            settings.setRanked(ranked)
            settings.setDifficulty(difficulty)
            settings.setTargetWinrate(t)
            // Automatch overrides the draft rung: nearest rung to the target
            // winrate off the live player rating. The pick persists as the
            // rung, so reopening the sheet in Fixed shows what was played.
            var picked = rank
            if (difficulty == Difficulty.AUTOMATCH) {
                val rating = PlayerWhr.rate(settings.ratedHistory.first())
                val rung = GameFlow.automatchRung(rating.whr, t)
                picked = Rank.ALL[rung]
            }
            settings.setRank(picked)
            _state.value = _state.value.copy(
                pendingFreePly = false,
                rank = picked,
                colorChoice = color,
                difficulty = difficulty, targetWinrate = t,
                ranked = ranked,
            )
            updatePlayerRankText()
            newGame()
        }
    }

    /** Live player rank text (single WHR refits are milliseconds; recompute freely). */
    private suspend fun updatePlayerRankText(known: Whr.Rating? = null) {
        val r = known ?: PlayerWhr.rate(settings.ratedHistory.first())
        val dev = WhrAnchors.whrRankDeviation(r.whr, r.unc).roundToInt()
        _state.value = _state.value.copy(
            playerRankText = "${WhrAnchors.whrPlayerLabel(r.whr, r.unc)} ±$dev",
            playerRating = r.whr,
        )
    }

    /**
     * Stats backfill (lazy, once per stale cache): recompute exactly the
     * missing/stale causal points off the main thread, then publish. Game
     * flow keeps the latest point fresh, so this usually owes nothing.
     * A concurrent game end discards the result; the Stats effect retriggers.
     */
    private var backfillJob: Job? = null
    fun backfillCurve() {
        if (backfillJob?.isActive == true) return
        backfillJob = viewModelScope.launch {
            val hist = settings.ratedHistory.first()
            val cache = settings.ratedCurve.first()
            val missing = RatingCurve.missingIndices(hist.size, cache)
            if (missing.isEmpty()) return@launch
            val games = PlayerWhr.games(hist)
            val filled = withContext(Dispatchers.Default) {
                val arr = RatingCurve.aligned(cache, hist.size).toMutableList()
                for (g in missing) {
                    val r = Whr.rate(games.take(g + 1))
                    arr[g] = RatingCurve.CurvePoint(Whr.CURVE_VERSION, r.whr, r.unc)
                }
                arr.toList()
            }
            if (settings.ratedHistory.first() != hist) return@launch
            settings.setCurve(filled)
        }
    }

    /** Stats-screen reset: wipe history, rank text folds back to the start. */
    fun resetHistory() {
        viewModelScope.launch {
            settings.clearRated()
            updatePlayerRankText()
        }
    }

    fun setGraphOpen(v: Boolean) {
        viewModelScope.launch { settings.setGraphOpen(v) }
        _state.value = _state.value.copy(graphOpen = v)
    }

    fun setReviewIdx(v: Int?) {
        _state.value = _state.value.copy(reviewIdx = v, captureFx = null, placeFx = null)
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
            _state.value = s.copy(status = "finished", passing = p)
            requestFinalScore()
            return
        }
        val passColor = s.toMove
        val hist = s.history + MoveRec(-1, -1, s.toMove)
        // The carried-forward point is a placeholder: the bot's root appraises
        // this exact board moments later and backfills it (see botReply).
        _state.value = syncBoard(
            s.copy(
                history = hist, toMove = -s.toMove, passing = p,
                pendingFreePly = true,
                winrateHistory = GameFlow.humanPassWinrates(s.winrateHistory),
                isThinking = GameFlow.replyFollowsHumanMove(gameFinished = false),
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
        _state.value = syncBoard(
            s.copy(
                history = hist.toList(), toMove = s.playerColor,
                pendingFreePly = false,
                undoUsed = true,
                finalScoreLead = null, finalOwnership = null, scoring = false,
                status = "playing", passing = 0,
                winrateHistory = s.winrateHistory.dropLast(undone),
                reviewIdx = null,
            )
        )
        // Rewind the persistent engine board (preserves its search tree).
        // The human replays from here; no query follows.
        viewModelScope.launch {
            try {
                engine().undoMoves(removedPlies)
            } catch (e: Exception) {
                Log.e(TAG, "engine undo failed", e)
            }
        }
    }

    fun onBoardTap(x: Int, y: Int) {
        val s = _state.value
        if (s.status != "playing" || s.toMove != s.playerColor || s.reviewIdx != null || s.isThinking) return
        // Any legal point, then the bot replies.
        val pos = currentPosition()
        if (pos.board.play(s.playerColor, x, y, pos.hashes) < 0) {
            _state.value = s.copy(error = "Illegal move")
            return
        }
        applyPlayerMove(x, y)
    }

    /** Board + every position hash in one replay (superko legality, one pass). */
    private fun currentPosition(): GoBoard.ReplayResult = GoBoard.replay(_state.value.history)

    /**
     * Write-ahead loss record on the player's first ply of a rated game.
     * Abandons need no end-of-game hook: the loss is already stored; clean
     * finishes upgrade it (see requestFinalScore).
     */
    /** Latest causal point for a history (game flow caches only r_G). */
    private fun curvePointFor(hist: List<RatedGame>, r: Whr.Rating? = null): RatingCurve.CurvePoint {
        val rating = r ?: PlayerWhr.rate(hist)
        return RatingCurve.CurvePoint(Whr.CURVE_VERSION, rating.whr, rating.unc)
    }

    private fun appendRatedRecord(s: GameState) {
        if (!GameFlow.ratedAppendWanted(s.ranked, s.status, s.history, s.playerColor)) return
        val rec = RatedGame(
            System.currentTimeMillis(), s.rank.id, s.playerColor == 1, 0.0,
        )
        viewModelScope.launch { settings.appendRated(rec, curvePointFor(settings.ratedHistory.first() + rec)) }
    }

    private fun applyPlayerMove(x: Int, y: Int) {
        val s = _state.value
        appendRatedRecord(s)
        val hist = s.history + MoveRec(x, y, s.playerColor)
        // Carry the last known value forward as a placeholder — the bot's
        // genmove_analyze root appraises this exact board moments later and
        // backfills it (same search, zero extra latency). A fabricated 0.5
        // made the graph read 50% on every black move.
        val win = s.winrateHistory.lastOrNull() ?: 0.5
        _state.value = syncBoard(
            s.copy(
                history = hist, toMove = -s.playerColor, passing = 0,
                pendingFreePly = true,
                winrateHistory = s.winrateHistory + win,
                // Bar up now: the engine sync + cold start below can stall
                // ~2s before botReply would raise it (see HumanMoveThinkingTest).
                isThinking = GameFlow.replyFollowsHumanMove(gameFinished = false),
            ),
            fx = captureFxFor(s.boardSignMap, hist),
            place = PlaceFx(x, y, s.playerColor, hist.size),
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
                    val passStep = GameFlow.botReplyWinrates(cur.winrateHistory, cur.pendingFreePly, prevW, 0.0, botPassed = true)
                    val winrates = passStep.winrates
                    _state.value = if (p >= 2) {
                        val done = syncBoard(cur.copy(history = hist, status = "finished", passing = p, isThinking = false, winrateHistory = winrates, pendingFreePly = passStep.pendingFreePly))
                        _state.value = done
                        requestFinalScore()
                        return@launch
                    } else {
                        val next = syncBoard(cur.copy(history = hist, toMove = -cur.toMove, passing = p, isThinking = false, winrateHistory = winrates, pendingFreePly = passStep.pendingFreePly))
                        _state.value = next
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
                        val moveStep = GameFlow.botReplyWinrates(cur.winrateHistory, cur.pendingFreePly, prevW, playedW, botPassed = false)
                        val winrates = moveStep.winrates
                        _state.value = syncBoard(
                            cur.copy(
                                history = hist, toMove = -cur.toMove, passing = 0,
                                isThinking = false,
                                winrateHistory = winrates,
                                pendingFreePly = moveStep.pendingFreePly,
                            ),
                            fx = captureFxFor(cur.boardSignMap, hist),
                            // Bot passes leave no stone and animate nothing.
                            place = hist.lastOrNull()?.takeIf { it.x >= 0 }?.let { PlaceFx(it.x, it.y, it.color, hist.size) },
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "bot reply failed", e)
                _state.value = _state.value.copy(isThinking = false, error = e.message ?: "Engine error")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        katago.stop()
    }
}
