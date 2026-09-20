package com.gotrainer.nine

import com.gotrainer.nine.game.Scoring
import org.junit.Assert.assertEquals
import org.junit.Test

class ScoringTest {
    private fun empty(): List<List<Int>> = List(9) { List(9) { 0 } }

    private fun withStones(vararg stones: Triple<Int, Int, Int>): List<List<Int>> {
        val b = List(9) { MutableList(9) { 0 } }
        for ((x, y, v) in stones) b[y][x] = v
        return b
    }

    @Test fun `empty board is white plus komi`() {
        val r = Scoring.estimate(empty())
        assertEquals(0.0, r.black, 1e-9)
        assertEquals(7.0, r.white, 1e-9)
        assertEquals("Game over · White +7", Scoring.gameOverText(empty()))
    }

    @Test fun `stones count, open board has no territory`() {
        val r = Scoring.estimate(withStones(Triple(4, 4, 1), Triple(2, 2, -1)))
        assertEquals(1.0, r.black, 1e-9)
        assertEquals(8.0, r.white, 1e-9)
        assertEquals("Game over · White +7", Scoring.gameOverText(withStones(Triple(4, 4, 1), Triple(2, 2, -1))))
    }

    @Test fun `sealed corners count as territory, open middle is neutral`() {
        // Black seals (0,0), White seals (8,8); everything else touches both -> neutral.
        val b = withStones(Triple(0, 1, 1), Triple(1, 0, 1), Triple(8, 7, -1), Triple(7, 8, -1))
        val r = Scoring.estimate(b)
        assertEquals(3.0, r.black, 1e-9) // 2 stones + 1 corner point
        assertEquals(10.0, r.white, 1e-9) // 2 stones + 1 corner point + komi
        assertEquals("Game over · White +7", Scoring.gameOverText(b))
    }

    @Test fun `shared region counts for no one`() {
        // Single empty point between a black and a white stone: neutral.
        val b = withStones(Triple(3, 3, 1), Triple(5, 3, -1))
        val r = Scoring.estimate(b)
        // Black: 1 stone; the vast open region touches both -> neutral.
        assertEquals(1.0, r.black, 1e-9)
        assertEquals(8.0, r.white, 1e-9)
    }

    @Test fun `formatLead trims decimals`() {
        assertEquals("Black +5", Scoring.formatLead(5.0))
        assertEquals("White +4.5", Scoring.formatLead(-4.5))
        assertEquals("Draw", Scoring.formatLead(0.0))
    }

    @Test fun `black win and draw text`() {
        val full = List(9) { List(9) { 1 } }
        assertEquals("Game over · Black +74", Scoring.gameOverText(full))
    }
}
