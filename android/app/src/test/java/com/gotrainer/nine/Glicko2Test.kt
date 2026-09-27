package com.gotrainer.nine

import com.gotrainer.nine.game.Glicko2
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Glickman's own paper example: 1500/200/0.06 vs 1400/30 (win),
 * 1550/100 (loss), 1700/300 (loss) -> ~1464.1 / ~151.5 / ~0.06.
 */
class Glicko2Test {
    private val delta = 0.05

    @Test fun `paper example rating and deviation`() {
        val out = Glicko2.update(
            Glicko2.Rating(1500.0, 200.0, 0.06),
            listOf(
                Glicko2.Opponent(1400.0, 30.0, 1.0),
                Glicko2.Opponent(1550.0, 100.0, 0.0),
                Glicko2.Opponent(1700.0, 300.0, 0.0),
            ),
        )
        assertEquals(1464.06, out.rating, delta)
        assertEquals(151.52, out.rd, delta)
        assertEquals(0.06, out.vol, 0.001)
    }

    @Test fun `beating a stronger bot raises rating`() {
        val before = Glicko2.Rating(525.0, 350.0, 0.06)
        val after = Glicko2.update(before, listOf(Glicko2.Opponent(1000.0, 60.0, 1.0)))
        assert(after.rating > before.rating) { "win must raise rating" }
        assert(after.rd < before.rd) { "a game must reduce uncertainty" }
    }

    @Test fun `losing to a weaker bot lowers rating`() {
        val before = Glicko2.Rating(1500.0, 150.0, 0.06)
        val after = Glicko2.update(before, listOf(Glicko2.Opponent(800.0, 60.0, 0.0)))
        assert(after.rating < before.rating) { "upset loss must lower rating" }
    }

    @Test fun `draw moves rating toward opponent`() {
        val before = Glicko2.Rating(1200.0, 150.0, 0.06)
        val after = Glicko2.update(before, listOf(Glicko2.Opponent(1600.0, 60.0, 0.5)))
        assert(after.rating > before.rating) { "draw vs stronger must raise rating" }
    }

    @Test fun `expected score favors the stronger side`() {
        val weak = Glicko2.Rating(800.0, 100.0, 0.06)
        val strong = Glicko2.Rating(1600.0, 100.0, 0.06)
        val eWeak = Glicko2.expectedScore(weak, 1600.0, 60.0)
        val eStrong = Glicko2.expectedScore(strong, 800.0, 60.0)
        assert(eWeak < 0.5) { "weaker side below 50%, was $eWeak" }
        assert(eStrong > 0.5) { "stronger side above 50%, was $eStrong" }
        assertEquals(1.0 - eWeak, Glicko2.expectedScore(strong, 800.0, 60.0), 0.02)
    }
}
