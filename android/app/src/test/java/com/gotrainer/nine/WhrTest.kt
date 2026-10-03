package com.gotrainer.nine

import com.gotrainer.nine.game.Whr
import com.gotrainer.nine.game.WhrAnchors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 2 of the Glicko-2 -> WHR migration: the WHR solver over the human's
 * single time-chain with fixed bot anchors. Reference values come from an
 * independent Python port of the same Newton/Thomas math (cross-checked
 * there against direct matrix inversion to 1e-13).
 *
 * The headline property under test: a sustained overperformance streak must
 * move the estimate fast and keep uncertainty up (no Glicko-style collapse).
 */
class WhrTest {
    private val rung10 = WhrAnchors.table[10]

    private fun wins(n: Int, spacing: Int = 1) =
        List(n) { i -> Whr.Game(day = i * spacing, oppWhr = rung10, score = 1.0) }

    @Test fun `empty history returns the prior exactly`() {
        val r = Whr.rate(emptyList())
        assertEquals(WhrAnchors.RUNG0_WHR, r.whr, 1e-9)
        assertEquals(245.67, r.unc, 0.05)
    }

    @Test fun `one win jumps toward the stronger opponent`() {
        val r = Whr.rate(listOf(Whr.Game(0, rung10, 1.0)))
        assertEquals(1096.27, r.whr, 0.5)
        assertEquals(250.99, r.unc, 0.5)
    }

    @Test fun `ten straight wins cross the bot and stay uncertain`() {
        val r = Whr.rate(wins(10))
        assertEquals(1618.65, r.whr, 1.0)
        // The anti-crawl proof: the estimate passes the beaten bot's rating.
        assertTrue("rating ${r.whr} must cross rung10 $rung10", r.whr > rung10)
        // Uncertainty shrinks with evidence but never collapses toward zero.
        assertTrue("unc ${r.unc} must stay responsive", r.unc > 150.0 && r.unc < 220.0)
    }

    @Test fun `time gaps widen uncertainty`() {
        val dense = Whr.rate(wins(10, spacing = 1))
        val spread = Whr.rate(wins(10, spacing = 10))
        assertEquals(1623.80, spread.whr, 1.0)
        assertTrue("spread ${spread.unc} must exceed dense ${dense.unc}", spread.unc > dense.unc)
    }

    @Test fun `mixed evidence sharpens the estimate`() {
        val mixed = Whr.rate(wins(5) + List(5) { i -> Whr.Game(5 + i, rung10, 0.0) })
        assertEquals(1175.56, mixed.whr, 1.0)
        assertEquals(108.13, mixed.unc, 1.0)
        assertTrue(mixed.unc < Whr.rate(wins(10)).unc)
    }

    @Test fun `loss draw win order correctly`() {
        val loss = Whr.rate(listOf(Whr.Game(0, rung10, 0.0))).whr
        val draw = Whr.rate(listOf(Whr.Game(0, rung10, 0.5)))
        val win = Whr.rate(listOf(Whr.Game(0, rung10, 1.0))).whr
        assertEquals(930.81, draw.whr, 1.0)
        assertTrue(loss < draw.whr && draw.whr < win)
    }

    @Test fun `ten straight losses sink below the ladder and clamp on display`() {
        val r = Whr.rate(List(10) { i -> Whr.Game(i, rung10, 0.0) })
        assertEquals(667.36, r.whr, 1.0)
        assertEquals(0.0, WhrAnchors.whrToRank(r.whr), 0.0)
    }

    @Test fun `long streaks converge past the beaten bot`() {
        // Regression: cold-start Newton diverged here (saturated likelihood,
        // singular Hessian). The backtracking line search must converge.
        val opp = WhrAnchors.table[20]
        val games = List(20) { i -> Whr.Game(day = i, oppWhr = opp, score = 1.0) }
        val r = Whr.rate(games)
        assertEquals(2406.58, r.whr, 1.0)
        assertTrue(r.whr.isFinite() && r.unc.isFinite())
        assertTrue("rating ${r.whr} must cross rung20 $opp", r.whr > opp)
        assertTrue("unc ${r.unc} must stay responsive", r.unc > 150.0 && r.unc < 220.0)
    }

    @Test fun `causal trajectory follows the games and ends at the current rating`() {
        val games = wins(10)
        val traj = Whr.causalTrajectory(games)
        val cur = Whr.rate(games)
        assertEquals(10, traj.size)
        assertEquals(cur.whr, traj.last().whr, 1e-9)
        assertEquals(cur.unc, traj.last().unc, 1e-9)
        assertTrue(traj.first().whr < traj.last().whr)
    }

    @Test fun `custom prior centers empty history`() {
        assertEquals(1000.0, Whr.rate(emptyList(), priorWhr = 1000.0).whr, 1e-9)
    }

    @Test fun `causal trajectory is empty on empty history`() {
        assertEquals(emptyList<Whr.Rating>(), Whr.causalTrajectory(emptyList()))
    }

    @Test fun `causal point g equals the prefix rate`() {
        // Independence: each point sees only its own prefix, so Stats can
        // recompute any subset (backfill) in any order with identical results.
        val games = wins(10)
        val causal = Whr.causalTrajectory(games)
        assertEquals(10, causal.size)
        for (i in games.indices) {
            val prefix = Whr.rate(games.take(i + 1))
            assertEquals(prefix.whr, causal[i].whr, 1e-9)
            assertEquals(prefix.unc, causal[i].unc, 1e-9)
        }
        assertEquals(Whr.rate(games).whr, causal.last().whr, 1e-9)
    }

    @Test fun `causal moves per game inside the same day`() {
        // Regression for the flat-burst report: same-day games used to share
        // one day node (flat for 5-20 games); causal accumulates each game's
        // likelihood in its own prefix.
        val causal = Whr.causalTrajectory(listOf(Whr.Game(0, rung10, 1.0), Whr.Game(0, rung10, 0.0)))
        assertEquals(1096.27, causal[0].whr, 0.5)
        assertTrue("second game must move its own point, got ${causal[1].whr}", causal[1].whr < causal[0].whr)
        assertTrue("balanced evidence stays above the prior, got ${causal[1].whr}", causal[1].whr > WhrAnchors.RUNG0_WHR)
    }
}
