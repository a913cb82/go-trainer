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
    @Test fun `rung 0 pins at the 20k floor`() {
        assertEquals(1031.666105, WhrAnchors.whrForRank(Rank.R20K), 0.0)
        assertEquals(29, WhrAnchors.table.size)
    }

    @Test fun `table rises monotonically with S-shaped gaps`() {
        val t = WhrAnchors.table
        val gaps = (0 until 28).map { t[it + 1] - t[it] }
        for ((i, gap) in gaps.withIndex()) {
            assertTrue("gap $i must be positive, was $gap", gap > 0.0)
            assertTrue("gap $i must stay ladder-shaped, was $gap", gap < 120.0)
        }
        // Steepest climb sits mid-ladder (flat-steep-flat S, not widening).
        val peak = gaps.indices.maxByOrNull { gaps[it] }!!
        assertTrue("steepest gap must sit mid-ladder, was $peak", peak in 10..20)
    }

    @Test fun `spot values match the tournament FP2 table`() {
        assertEquals(1500.0, WhrAnchors.table[15], 1e-6)
        assertEquals(1243.937616, WhrAnchors.table[10], 1e-6)
        assertEquals(1786.179301, WhrAnchors.table[20], 1e-6)
        assertEquals(2066.187811, WhrAnchors.table[28], 1e-6)
        // 18k takes the smoothed FP2 value (its games were excluded).
        assertEquals(1039.779011, WhrAnchors.table[2], 1e-6)
    }

    @Test fun `adjacent gaps match the frozen derivation`() {
        // Pinned Elo gaps from the tournament FP2 fit. Guards
        // the literals against accidental edits.
        val t = WhrAnchors.table
        for ((i, g) in listOf(
            0 to 2.480, 16 to 58.067, 27 to 10.138,
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
        assertEquals("20k?", WhrAnchors.whrPlayerLabel(WhrAnchors.RUNG0_WHR, 245.7))
        assertEquals("8k", WhrAnchors.whrPlayerLabel(WhrAnchors.table[12], 100.0))
        assertEquals("2d?", WhrAnchors.whrPlayerLabel(WhrAnchors.table[21], 200.0))
    }

    @Test fun `rank deviation spans ranks upward`() {
        assertTrue(WhrAnchors.whrRankDeviation(WhrAnchors.RUNG0_WHR, 245.7) > 5.0)
        assertTrue(WhrAnchors.whrRankDeviation(1500.0, 60.0) < 2.0)
    }

    @Test fun `midpoints interpolate linearly and extremes clamp`() {
        val t = WhrAnchors.table
        for (i in 0 until 28) {
            assertEquals(i + 0.5, WhrAnchors.whrToRank((t[i] + t[i + 1]) / 2.0), 1e-9)
        }
        assertEquals(0.0, WhrAnchors.whrToRank(524.9), 0.0)
        assertEquals(28.0, WhrAnchors.whrToRank(2100.0), 0.0)
    }
}
