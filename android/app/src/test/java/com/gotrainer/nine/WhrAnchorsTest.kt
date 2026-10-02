package com.gotrainer.nine

import com.gotrainer.nine.game.Rank
import com.gotrainer.nine.game.WhrAnchors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WHR bot anchors: pin, frozen-table regression pins, ladder shape,
 * rank round-trips, and the WHR display helpers.
 */
class WhrAnchorsTest {
    @Test fun `rung 0 pins at 525`() {
        assertEquals(525.0, WhrAnchors.whrForRank(Rank.R30K), 0.0)
        assertEquals(39, WhrAnchors.table.size)
    }

    @Test fun `table rises with widening gaps`() {
        val t = WhrAnchors.table
        for (i in 0 until 38) {
            val gap = t[i + 1] - t[i]
            assertTrue("gap $i must be positive, was $gap", gap > 0.0)
            assertTrue("gap $i must stay ladder-shaped, was $gap", gap < 120.0)
        }
    }

    @Test fun `spot values match the OGS-derived table`() {
        assertEquals(803.641111022468, WhrAnchors.table[10], 1e-6)
        assertEquals(1232.8269671249463, WhrAnchors.table[20], 1e-6)
        assertEquals(1893.8941671604941, WhrAnchors.table[30], 1e-6)
        assertEquals(2671.8650090201986, WhrAnchors.table[38], 1e-6)
    }

    @Test fun `adjacent gaps match the frozen derivation`() {
        // Pinned Elo gaps from the probability-matched derivation. Guards
        // the literals against accidental edits.
        val t = WhrAnchors.table
        for ((i, g) in listOf(
            0 to 22.7660, 9 to 33.5836, 17 to 47.4469,
            24 to 64.1990, 29 to 79.6761, 37 to 112.5663,
        )) {
            assertEquals(g, t[i + 1] - t[i], 1e-3)
        }
    }

    @Test fun `rank round-trips exactly at every rung`() {
        for (rank in Rank.ALL) {
            val rung = Rank.ALL.indexOf(rank).toDouble()
            assertEquals(rung, WhrAnchors.whrToRank(WhrAnchors.whrForRank(rank)), 1e-9)
        }
    }

    @Test fun `player label keeps the provisional marker`() {
        assertEquals("30k?", WhrAnchors.whrPlayerLabel(525.0, 245.7))
        assertEquals("8k", WhrAnchors.whrPlayerLabel(WhrAnchors.table[22], 100.0))
        assertEquals("2d?", WhrAnchors.whrPlayerLabel(WhrAnchors.table[31], 200.0))
    }

    @Test fun `rank deviation spans ranks upward`() {
        assertTrue(WhrAnchors.whrRankDeviation(525.0, 245.7) > 5.0)
        assertTrue(WhrAnchors.whrRankDeviation(1500.0, 60.0) < 2.0)
    }

    @Test fun `midpoints interpolate linearly and extremes clamp`() {
        val t = WhrAnchors.table
        for (i in 0 until 38) {
            assertEquals(i + 0.5, WhrAnchors.whrToRank((t[i] + t[i + 1]) / 2.0), 1e-9)
        }
        assertEquals(0.0, WhrAnchors.whrToRank(524.9), 0.0)
        assertEquals(38.0, WhrAnchors.whrToRank(2671.88), 0.0)
    }
}
