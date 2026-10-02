package com.gotrainer.nine

import com.gotrainer.nine.game.GameFlow
import com.gotrainer.nine.game.MoveRec
import com.gotrainer.nine.game.WhrAnchors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rated-game decisions: write-ahead loss, clean-finish upgrade, automatch. */
class RatedGameTest {
    private fun hist(vararg colors: Int) = colors.map { MoveRec(0, 0, it) }

    @Test fun `append on first player ply of a rated game only`() {
        assertTrue(GameFlow.ratedAppendWanted(true, 0, "playing", emptyList(), 1))
        // Suggestions games never rate, even opted in…
        assertFalse(GameFlow.ratedAppendWanted(true, 3, "playing", emptyList(), 1))
        // …and opted-out free games leave no trace.
        assertFalse(GameFlow.ratedAppendWanted(false, 0, "playing", emptyList(), 1))
        assertFalse(GameFlow.ratedAppendWanted(true, 0, "finished", emptyList(), 1))
        assertFalse(GameFlow.ratedAppendWanted(true, 0, "playing", hist(1), 1))
        assertTrue(GameFlow.ratedAppendWanted(true, 0, "playing", hist(-1), 1))
        // Bot plies don't count: still the player's first ply pending.
        assertTrue(GameFlow.ratedAppendWanted(true, 0, "playing", hist(-1, -1), 1))
    }

    @Test fun `finish upgrades clean wins and draws only`() {
        // Black wins, no undos -> win; White wins from Black's seat -> stays loss.
        assertEquals(1.0, GameFlow.ratedFinishScore(5.5, playerBlack = true, undoUsed = false, playerMoved = true))
        assertNull(GameFlow.ratedFinishScore(-5.5, playerBlack = true, undoUsed = false, playerMoved = true))
        assertEquals(1.0, GameFlow.ratedFinishScore(-5.5, playerBlack = false, undoUsed = false, playerMoved = true))
        // Any undo poisons the upgrade, even a later clean win.
        assertNull(GameFlow.ratedFinishScore(5.5, playerBlack = true, undoUsed = true, playerMoved = true))
        // Draws upgrade to 0.5, never to a win.
        assertEquals(0.5, GameFlow.ratedFinishScore(0.0, playerBlack = true, undoUsed = false, playerMoved = true))
        // Nobody played -> nothing to upgrade.
        assertNull(GameFlow.ratedFinishScore(5.5, playerBlack = true, undoUsed = false, playerMoved = false))
    }

    @Test fun `automatch centers near-even players and clamps at the ends`() {
        // 1500 WHR at 50% -> rung 25 (the WHR table maps 1500 to rung 24.57).
        assertEquals(25, GameFlow.automatchRung(1500.0, 50))
        // Demanding 90% off a mid player -> much weaker bot.
        val weak = GameFlow.automatchRung(1500.0, 90)
        assert(weak <= 19) { "90% target must drop many rungs, got $weak" }
        // Demanding 10% -> much stronger bot.
        val strong = GameFlow.automatchRung(1500.0, 10)
        assert(strong >= 29) { "10% target must climb many rungs, got $strong" }
        // Fresh 30k start can never undercut the ladder or overrun it.
        assertEquals(0, GameFlow.automatchRung(525.0, 90))
        assertEquals(38, GameFlow.automatchRung(6000.0, 10))
    }

    @Test fun `automatch pick reproduces the target winrate`() {
        for (target in listOf(10, 30, 50, 70, 90)) {
            val rung = GameFlow.automatchRung(1500.0, target)
            val e = WhrAnchors.expectedScore(1500.0, WhrAnchors.whrForRank(com.gotrainer.nine.game.Rank.ALL[rung]))
            // Within one rung step (~4% winrate) except at the clamps.
            if (rung in 1..37) assertEquals(target / 100.0, e, 0.05)
        }
    }
}
