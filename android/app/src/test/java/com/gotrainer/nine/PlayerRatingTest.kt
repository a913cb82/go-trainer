package com.gotrainer.nine

import com.gotrainer.nine.game.PlayerRating
import com.gotrainer.nine.game.PlayerWhr
import com.gotrainer.nine.game.RatedGame
import com.gotrainer.nine.game.WhrAnchors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerRatingTest {
    private fun game(bot: String, score: Double, ts: Long = 1L, black: Boolean = true) =
        RatedGame(ts, bot, black, score)

    @Test fun `no history is the 20k start`() {
        val r = PlayerWhr.rate(emptyList())
        assertEquals(WhrAnchors.RUNG0_WHR, r.whr, 1e-9)
        assertEquals("20k?", WhrAnchors.whrPlayerLabel(r.whr, r.unc))
    }

    @Test fun `wins climb and losses fall back`() {
        val climb = PlayerWhr.rate(listOf(game("20k", 1.0), game("19k", 1.0), game("18k", 1.0)))
        assert(climb.whr > WhrAnchors.RUNG0_WHR) { "three wins must leave 20k" }
        val fall = PlayerWhr.rate(
            listOf(game("20k", 1.0), game("19k", 1.0), game("18k", 1.0), game("1k", 0.0)),
        )
        assert(fall.whr < climb.whr) { "upset loss must undo progress" }
    }

    @Test fun `retired sub-20k records count as the 20k bot faced`() {
        // Correction, not migration: those games were played against the
        // rank_20k config, so "30k" and "20k" labels rate identically.
        val old = PlayerWhr.rate(listOf(game("30k", 1.0)))
        val now = PlayerWhr.rate(listOf(game("20k", 1.0)))
        assertEquals(now.whr, old.whr, 1e-9)
    }

    @Test fun `bot rank resolves through the table, not the record`() {
        // Same game, different table values -> different outcomes. Recalibration
        // edits WhrAnchors; history needs no migration.
        val hist = listOf(game("1d", 1.0))
        assert(PlayerWhr.rate(hist).whr > WhrAnchors.RUNG0_WHR) { "beating 1d from 20k must jump" }
    }

    @Test fun `causal trajectory is one point per game, ending at the current rating`() {
        // Causal (each point sees only its own prefix), so every win/loss
        // moves its own dot — unlike the old smoothed full-history refit.
        val hist = listOf(game("20k", 1.0, ts = 1L), game("20k", 0.0, ts = 2L))
        val traj = PlayerWhr.causalTrajectory(hist)
        assertEquals(2, traj.size)
        assertEquals(PlayerWhr.rate(hist).whr, traj[1].whr, 1e-9)
    }

    @Test fun `encoding round-trips and skips garbage`() {
        val hist = listOf(
            game("8k", 1.0, ts = 1727000000000L, black = true),
            game("1d", 0.5, ts = 1727000001000L, black = false),
        )
        assertEquals(hist, PlayerRating.decode(PlayerRating.encode(hist)))
        assertEquals(emptyList<RatedGame>(), PlayerRating.decode(""))
        // Corrupt entries dropped, good ones kept.
        val mixed = PlayerRating.encode(hist) + ";garbage;1|2|3|4|5;0|x|1|9.9"
        assertEquals(hist, PlayerRating.decode(mixed))
    }
}
