package com.gotrainer.nine

import com.gotrainer.nine.game.GameFlow
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * BUG: pass plies never append a winrate point (history grows, winrateHistory
 * does not), so moves played while the opponent passes vanish from the graph
 * and the review slider can no longer reach them one-per-ply.
 *
 * - Bot passes: backfills the human ply but adds nothing for its own pass ply.
 * - Human passes: adds nothing at all.
 * The pass ply's appraisal is free: the bot's root already appraised that
 * exact board (prevW); a human pass carries the last value forward.
 */
class PassWinrateTest {
    @Test fun `bot pass appends a point for the pass ply`() {
        // Choice mode: human ply already has its real value 0.6.
        val step = GameFlow.botReplyWinrates(
            cur = listOf(0.6), pending = false,
            prevW = 0.58, playedW = 0.0, botPassed = true,
        )
        assertEquals(listOf(0.6, 0.58), step.winrates)
    }

    @Test fun `bot pass backfills the human ply and appends the pass ply`() {
        // Free mode: placeholder 0.5 awaits the root appraisal 0.62 of the
        // same board — needed twice (human ply + pass ply).
        val step = GameFlow.botReplyWinrates(
            cur = listOf(0.5), pending = true,
            prevW = 0.62, playedW = 0.0, botPassed = true,
        )
        assertEquals(listOf(0.62, 0.62), step.winrates)
        assertEquals(false, step.pendingFreePly)
    }

    @Test fun `human pass carries the last value forward`() {
        assertEquals(listOf(0.6, 0.55, 0.55), GameFlow.humanPassWinrates(listOf(0.6, 0.55)))
    }

    @Test fun `opening pass with no history still yields one point per ply`() {
        // Human passes on the empty board (nothing to carry); the bot's root
        // appraisal seeds both plies instead of leaving a hole.
        assertEquals(emptyList<Double>(), GameFlow.humanPassWinrates(emptyList()))
        val step = GameFlow.botReplyWinrates(
            cur = emptyList(), pending = true,
            prevW = 0.55, playedW = 0.53, botPassed = false,
        )
        assertEquals(listOf(0.55, 0.53), step.winrates)
        assertEquals(false, step.pendingFreePly)
    }

    @Test fun `bot move with pending placeholder still backfills then appends`() {
        // Already-correct behaviour, locked in.
        val step = GameFlow.botReplyWinrates(
            cur = listOf(0.5), pending = true,
            prevW = 0.62, playedW = 0.60, botPassed = false,
        )
        assertEquals(listOf(0.62, 0.60), step.winrates)
        assertEquals(false, step.pendingFreePly)
    }

    @Test fun `bot move without pending just appends`() {
        // Already-correct behaviour, locked in.
        val step = GameFlow.botReplyWinrates(
            cur = listOf(0.6), pending = false,
            prevW = 0.0, playedW = 0.58, botPassed = false,
        )
        assertEquals(listOf(0.6, 0.58), step.winrates)
    }
}
