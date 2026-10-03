package com.gotrainer.nine.engine

import com.gotrainer.nine.game.MoveRec
import com.gotrainer.nine.game.Rank

/**
 * Bot reply: [winrate]/[scoreLead] appraise the position AFTER the bot's move
 * (the played move's analyze values); [rootWinrate]/[rootScoreLead] appraise
 * the position BEFORE it (== after the human's move). Both come from the one
 * genmove_analyze search, so backfilling the human ply costs zero extra latency.
 */
data class EngineMove(
    val x: Int,
    val y: Int,
    val pass: Boolean = false,
    val winrate: Double = 0.5,
    val scoreLead: Double = 0.0,
    val rootWinrate: Double = 0.5,
    val rootScoreLead: Double = 0.0,
)
/**
 * Final-score answer. scoreLeadBlack follows KataGo's BLACK-perspective
 * convention (forced per-query via reportAnalysisWinratesAs): positive means
 * Black leads, komi already included. ownership[y][x] in [-1, 1] (+1 = black)
 * when the engine provides it; null for the local estimator.
 */
data class ScoreResult(val scoreLeadBlack: Double, val ownership: List<List<Double>>? = null)

/** Engine abstraction: bot replies, scoring, persistent-board sync. */
interface GoEngine {
    suspend fun genMove(
        board: List<List<Int>>,
        toMove: Int,
        rank: Rank,
        history: List<MoveRec>,
    ): EngineMove

    /** Score a finished game: engine estimate when available, local fallback otherwise. */
    suspend fun score(
        board: List<List<Int>>,
        history: List<MoveRec>,
    ): ScoreResult

    /**
     * Persistent-board sync (GTP tree reuse). Stateless engines (remote) use
     * the default no-ops — every query carries full history anyway.
     */
    /** Reset for a fresh game (GTP: clear_board + rules/komi/profile). */
    suspend fun newGame(rank: Rank) {}
    /** Record an already-applied move (GTP: play). x < 0 = pass. */
    suspend fun playMove(color: Int, x: Int, y: Int) {}
    /** Rewind n plies (GTP: undo x n). */
    suspend fun undoMoves(n: Int) {}
}
