package com.gotrainer.nine

import com.gotrainer.nine.game.RatingCurve
import com.gotrainer.nine.game.Whr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Versioned causal-rating cache: per-point versions, planner, codec. */
class RatingCurveTest {
    private fun pt(ver: Int = Whr.CURVE_VERSION, whr: Double = 700.0, unc: Double = 200.0) =
        RatingCurve.CurvePoint(ver, whr, unc)

    @Test fun `version constant starts at one`() {
        assertEquals(1, Whr.CURVE_VERSION)
    }

    @Test fun `no cache means every index needs computing`() {
        assertEquals(listOf(0, 1, 2), RatingCurve.missingIndices(3, emptyList()))
    }

    @Test fun `complete current cache needs nothing`() {
        val cache = listOf(pt(), pt(), pt())
        assertEquals(emptyList<Int>(), RatingCurve.missingIndices(3, cache))
    }

    @Test fun `short cache needs only the suffix`() {
        // Steady state: game flow appended r_G each game, Stats owes nothing.
        // Fresh history with a partial cache owes just the tail.
        val cache = listOf(pt(), pt())
        assertEquals(listOf(2, 3), RatingCurve.missingIndices(4, cache))
    }

    @Test fun `stale-version points need recompute, current suffix is kept`() {
        // Post-bump shape: old-version prefix invalid, new-version suffix kept.
        val cache = listOf(pt(ver = 0), pt(ver = 0), pt(), pt())
        assertEquals(listOf(0, 1), RatingCurve.missingIndices(4, cache))
    }

    @Test fun `extra points beyond history need no compute`() {
        // Caller truncates (drop/reset path); the planner never invents work.
        val cache = listOf(pt(), pt(), pt(), pt(), pt())
        assertEquals(emptyList<Int>(), RatingCurve.missingIndices(3, cache))
    }

    @Test fun `encoding round-trips`() {
        val pts = listOf(pt(whr = 751.95, unc = 228.1), pt(whr = 660.123456, unc = 199.5))
        assertEquals(pts, RatingCurve.decode(RatingCurve.encode(pts)))
    }

    @Test fun `empty encodes to blank and back`() {
        assertEquals(emptyList<RatingCurve.CurvePoint>(), RatingCurve.decode(""))
        assertEquals(emptyList<RatingCurve.CurvePoint>(), RatingCurve.decode(RatingCurve.encode(emptyList())))
    }

    @Test fun `decoding skips garbage and keeps good points`() {
        val good = listOf(pt(), pt(whr = 800.0))
        val mixed = RatingCurve.encode(good) + ";garbage;1|2;1,notanum,3;,,,;1,inf,3"
        assertEquals(good, RatingCurve.decode(mixed))
    }

    @Test fun `decoded versions gate the planner`() {
        val old = RatingCurve.encode(listOf(pt(ver = 0), pt(ver = 0)))
        assertEquals(listOf(0, 1), RatingCurve.missingIndices(2, RatingCurve.decode(old)))
    }

    @Test fun `aligned pads short caches with stale placeholders`() {
        val cache = listOf(pt(), pt())
        val out = RatingCurve.aligned(cache, 4)
        assertEquals(4, out.size)
        assertEquals(cache, out.take(2))
        assertEquals(listOf(2, 3), RatingCurve.missingIndices(4, out))
    }

    @Test fun `aligned truncates extras beyond history`() {
        val cache = listOf(pt(), pt(), pt())
        assertEquals(cache.take(2), RatingCurve.aligned(cache, 2))
    }

    @Test fun `appended pads gaps then appends the latest point`() {
        // Pre-cache history (5 games, empty cache): game flow stores only r_G.
        val r6 = pt(whr = 800.0)
        val out = RatingCurve.appended(emptyList(), historySizeBefore = 5, r6)
        assertEquals(6, out.size)
        assertEquals(r6, out[5])
        assertEquals(listOf(0, 1, 2, 3, 4), RatingCurve.missingIndices(6, out))
    }

    @Test fun `appended keeps lockstep on the fast path`() {
        val cache = listOf(pt(), pt())
        val r3 = pt(whr = 810.0)
        assertEquals(cache + r3, RatingCurve.appended(cache, historySizeBefore = 2, r3))
    }

    @Test fun `lastReplaced overwrites the pending point in place`() {
        // Write-ahead loss cached at append; clean finish upgrades history[1]
        // and its point together — length never changes.
        val cache = listOf(pt(whr = 600.0), pt(whr = 610.0))
        val final = pt(whr = 750.0)
        val out = RatingCurve.lastReplaced(cache, historySize = 2, final)
        assertEquals(listOf(cache[0], final), out)
        assertEquals(emptyList<Int>(), RatingCurve.missingIndices(2, out))
    }
}