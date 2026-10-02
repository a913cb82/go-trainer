package com.gotrainer.nine.game

import kotlin.math.roundToInt

/**
 * Pure game-flow decisions, extracted VERBATIM from GameViewModel so they can
 * be unit-tested (the ViewModel itself needs Android and KataGo).
 *
 * Zero Android imports. Behaviour is intentionally identical to the inline
 * code it was lifted from — including the bugs, which the tests pin.
 * Fixes land here, one function at a time.
 */
object GameFlow {

    /** What the app does on first open, given the human's resolved colour. */
    enum class OpeningAction { REQUEST_CANDIDATES, BOT_REPLY }

    /**
     * What the app does on first open: candidates when the human (Black) is
     * to move, otherwise the bot opens (it holds Black and the first move).
     */
    fun openingAction(playerColor: Int): OpeningAction =
        if (playerColor == -1) OpeningAction.BOT_REPLY else OpeningAction.REQUEST_CANDIDATES

    /**
     * Whether to warm the engine in the background while the human thinks:
     * yes exactly when the human is to move and the engine is cold, so a
     * cold start never sits on the first reply's critical path. When the bot
     * is to move its reply self-starts the engine with the thinking bar shown.
     */
    fun preloadWanted(humanToMove: Boolean, engineReady: Boolean): Boolean =
        humanToMove && !engineReady

    /**
     * Whether the resumed-game engine resync (process spawn + model load +
     * board replay) must raise the thinking indicator: exactly when a
     * follow-up engine query will be issued (bot reply, or candidates the
     * human never saw). Mirrors the follow-up branch in GameViewModel init.
     * Otherwise the app sits on a dead board for seconds with no indicator.
     */
    fun resyncThinkingWanted(
        toMove: Int,
        playerColor: Int,
        choiceCount: Int,
        candidatesPresent: Boolean,
        reviewing: Boolean,
    ): Boolean {
        if (toMove != playerColor) return true // botReply() follows unconditionally
        // Human to move: requestCandidates() follows only when it won't early-return.
        return !reviewing && choiceCount != 0 && !candidatesPresent
    }

    /** One bot-reply winrate step: the appended list + the surviving flag. */
    data class WinrateStep(val winrates: List<Double>, val pendingFreePly: Boolean)

    /**
     * [GameViewModel.botReply] winrate bookkeeping, one point per ply. The
     * pass ply is appraised for free: the root already appraised the board
     * before the bot's move, so it serves for the pass ply too.
     */
    fun botReplyWinrates(
        cur: List<Double>,
        pending: Boolean,
        prevW: Double,
        playedW: Double,
        botPassed: Boolean,
    ): WinrateStep {
        var p = pending
        // A pending placeholder pairs with an appended point, but never drop
        // the fresh appraisal even if the list is unexpectedly empty.
        fun backfill(): List<Double> {
            p = false
            return if (cur.isNotEmpty()) cur.dropLast(1) + prevW else listOf(prevW)
        }
        val w = if (botPassed) {
            (if (p) backfill() else cur) + prevW
        } else {
            (if (p) backfill() else cur) + playedW
        }
        return WinrateStep(w, p)
    }

    /**
     * [GameViewModel.pass] bookkeeping: a human pass carries the last value
     * forward as a placeholder — the bot's root appraises this exact board
     * moments later and backfills it (pending flag, set by the caller).
     */
    fun humanPassWinrates(cur: List<Double>): List<Double> =
        if (cur.isEmpty()) cur else cur + cur.last()

    // ---- rated games (free-choice only; suggestions games leave no trace) ----

    /**
     * Whether this player ply writes the ahead-loss record: a ranked,
     * actually-free-choice game in progress and the player's first ply
     * (stone or pass). Suggestions games (choiceCount > 0) never rate, even
     * opted in; opted-out free games leave no trace either.
     */
    fun ratedAppendWanted(
        ranked: Boolean,
        choiceCount: Int,
        status: String,
        history: List<MoveRec>,
        playerColor: Int,
    ): Boolean =
        ranked && choiceCount == 0 && status == "playing" &&
            history.none { it.color == playerColor }

    /**
     * Upgrade for the pending loss record on a finished game: 1.0 win, 0.5
     * draw — only with no undos all game. Null = stays a loss. Draws count
     * as played, not as wins.
     */
    fun ratedFinishScore(
        finalScoreLead: Double,
        playerBlack: Boolean,
        undoUsed: Boolean,
        playerMoved: Boolean,
    ): Double? {
        if (undoUsed || !playerMoved) return null
        if (finalScoreLead == 0.0) return 0.5
        val won = (finalScoreLead > 0) == playerBlack
        return if (won) 1.0 else null
    }

    /**
     * Automatch rung for a target winrate: invert the WHR expected score,
     * snap to the nearest ladder rung, clamp at the ends. Pure; the ViewModel
     * supplies the live player rating. Fixed anchors carry no uncertainty,
     * so the inversion is exact (no g-factor).
     */
    fun automatchRung(playerRating: Double, targetWinrate: Int): Int {
        val e = targetWinrate.coerceIn(10, 90) / 100.0
        val oppWhr = playerRating + WhrAnchors.ELO_SCALE * kotlin.math.log10((1.0 - e) / e)
        return WhrAnchors.whrToRank(oppWhr).roundToInt().coerceIn(0, 38)
    }
}
