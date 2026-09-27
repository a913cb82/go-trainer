package com.gotrainer.nine.game

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
}
