package com.gotrainer.nine

import com.gotrainer.nine.game.BotRatings
import com.gotrainer.nine.game.Rank
import org.junit.Assert.assertEquals
import org.junit.Test

class BotRatingsTest {
    private val delta = 0.01

    @Test fun `ladder rungs map to OGS ranks`() {
        // rung index IS the OGS rank: 0=30k ... 29=1k, 30=1d ... 38=9d
        assertEquals(525.0, BotRatings.forRank(Rank.R30K), delta)
        assertEquals(525.0 * Math.exp(29.0 / 23.15), BotRatings.forRank(Rank.R1K), 0.5)
        assertEquals(525.0 * Math.exp(30.0 / 23.15), BotRatings.forRank(Rank.R1D), 0.5)
        assertEquals(525.0 * Math.exp(38.0 / 23.15), BotRatings.forRank(Rank.R9D), 1.0)
    }

    @Test fun `rating to rank round-trips every rung`() {
        for (rank in Rank.ALL) {
            val rung = Rank.ALL.indexOf(rank).toDouble()
            assertEquals(rung, BotRatings.ratingToRank(BotRatings.forRank(rank)), 0.001)
        }
    }

    @Test fun `start rating is exactly 30k`() {
        assertEquals(0.0, BotRatings.ratingToRank(BotRatings.START_RATING), 0.001)
        assertEquals("30k", BotRatings.rankLabel(0.0))
    }

    @Test fun `labels follow app style at boundaries`() {
        assertEquals("30k", BotRatings.rankLabel(0.0))
        assertEquals("1k", BotRatings.rankLabel(29.0))
        assertEquals("1k", BotRatings.rankLabel(29.7)) // kyu rounds toward weaker
        assertEquals("1d", BotRatings.rankLabel(30.0))
        assertEquals("9d", BotRatings.rankLabel(38.0))
        assertEquals("30k", BotRatings.rankLabel(-5.0)) // floor holds
        assertEquals("9d", BotRatings.rankLabel(99.0)) // cap holds
    }

    @Test fun `provisional marker never hides the rank`() {
        assertEquals("30k?", BotRatings.playerLabel(525.0, 350.0))
        assertEquals("8k", BotRatings.playerLabel(BotRatings.rankToRating(22.0), 100.0))
        assertEquals("2d?", BotRatings.playerLabel(BotRatings.rankToRating(31.0), 200.0))
    }

    @Test fun `uncertainty spans ranks upward`() {
        val dev = BotRatings.rankDeviation(525.0, 350.0)
        assert(dev > 5) { "fresh 350 RD must span many ranks, was $dev" }
        val tight = BotRatings.rankDeviation(1500.0, 60.0)
        assert(tight < 2) { "confident RD must span ~1 rank, was $tight" }
    }
}
