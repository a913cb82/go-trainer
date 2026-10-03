package com.gotrainer.nine

import com.gotrainer.nine.game.GameFlow
import com.gotrainer.nine.game.MoveRec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BUG (user report): fresh app, quick new game, quick first stone: just
 * before White replies, a popup `KataGo GTP error on 'play B E5':
 * ? illegal move`. Game continues fine.
 *
 * Cause: the launch-time resync replays the OLD game's history onto the
 * shared engine board with no staleness check inside the loop. New game
 * clears the board, the user plays E5, then the stale replay's own
 * `play B E5` hits the occupied point. The `seq != gameSeq` bail only ran
 * after the full replay — and the catch surfaced the stale error. A stale
 * resync must send nothing and report nothing.
 */
class StaleResyncTest {
    private val hist = listOf(MoveRec(4, 4, 1))

    @Test fun `current resync replays the full history`() {
        assertEquals(hist, GameFlow.resyncMoves(hist, seq = 3, gameSeq = 3))
    }

    @Test fun `stale resync replays nothing`() {
        assertTrue(GameFlow.resyncMoves(hist, seq = 3, gameSeq = 4).isEmpty())
    }
}
