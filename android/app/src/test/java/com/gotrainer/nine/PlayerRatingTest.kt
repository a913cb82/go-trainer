package com.gotrainer.nine

import com.gotrainer.nine.game.BotRatings
import com.gotrainer.nine.game.Glicko2
import com.gotrainer.nine.game.PlayerRating
import com.gotrainer.nine.game.RatedGame
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerRatingTest {
    private fun game(bot: String, score: Double, ts: Long = 1L, black: Boolean = true) =
        RatedGame(ts, bot, black, score)

    @Test fun `no history is the 30k start`() {
        val r = PlayerRating.rate(emptyList())
        assertEquals(525.0, r.rating, 0.001)
        assertEquals(350.0, r.rd, 0.001)
        assertEquals("30k?", BotRatings.playerLabel(r.rating, r.rd))
    }

    @Test fun `single step matches a direct glicko update`() {
        val hist = listOf(game("30k", 1.0))
        val folded = PlayerRating.rate(hist)
        val direct = Glicko2.update(
            PlayerRating.START,
            listOf(Glicko2.Opponent(BotRatings.forRank(com.gotrainer.nine.game.Rank.R30K), BotRatings.BOT_RD, 1.0)),
        )
        assertEquals(direct.rating, folded.rating, 1e-9)
        assertEquals(direct.rd, folded.rd, 1e-9)
    }

    @Test fun `wins climb and losses fall back`() {
        val climb = PlayerRating.rate(listOf(game("30k", 1.0), game("29k", 1.0), game("28k", 1.0)))
        assert(climb.rating > 525.0) { "three wins must leave 30k" }
        val fall = PlayerRating.rate(
            listOf(game("30k", 1.0), game("29k", 1.0), game("28k", 1.0), game("1k", 0.0)),
        )
        assert(fall.rating < climb.rating) { "upset loss must undo progress" }
        assert(fall.rd < 350.0) { "games must reduce uncertainty" }
    }

    @Test fun `bot rank resolves through the table, not the record`() {
        // Same game, different table values -> different outcomes. Recalibration
        // edits BotRatings.forRank; history needs no migration.
        val hist = listOf(game("1d", 1.0))
        assert(PlayerRating.rate(hist).rating > 525.0) { "beating 1d from 30k must jump" }
    }

    @Test fun `trajectory has one point per game`() {
        val hist = listOf(game("30k", 1.0, ts = 1L), game("30k", 0.0, ts = 2L))
        val traj = PlayerRating.trajectory(hist)
        assertEquals(2, traj.size)
        assertEquals(PlayerRating.rate(hist.take(1)).rating, traj[0].rating, 1e-9)
        assertEquals(PlayerRating.rate(hist).rating, traj[1].rating, 1e-9)
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
