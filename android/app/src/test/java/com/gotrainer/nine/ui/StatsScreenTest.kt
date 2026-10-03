package com.gotrainer.nine.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsScreenTest {
    @Test fun `ticks span the data with whole ranks`() {
        val ticks = rankAxisTicks(10.0, 14.0)
        assertTrue(ticks.first() <= 10)
        assertTrue(ticks.last() >= 14)
        assertEquals(ticks.sorted(), ticks)
    }

    @Test fun `ticks pad narrow ranges to at least two ranks`() {
        val ticks = rankAxisTicks(22.0, 22.0)
        assertTrue(ticks.size >= 2)
        assertTrue(ticks.last() - ticks.first() >= 2)
    }

    @Test fun `ticks clamp to the ladder`() {
        val ticks = rankAxisTicks(-5.0, 45.0)
        assertEquals(0, ticks.first())
        // Padded top may overshoot by under one step (display clamps
        // through rankLabel, as before); it must not run away.
        assertTrue("last=${ticks.last()}", ticks.last() <= 36)
    }

    @Test fun `step thinning keeps the padded top tick`() {
        // Real 139-game history: data peaks at idx ~12.3 but the thinned
        // axis topped at 10, flattening the whole recent climb onto the top
        // gridline (axis read 20k..8k, everything stronger cut off).
        val ticks = rankAxisTicks(0.0, 12.3)
        assertTrue("last=${ticks.last()}", ticks.last() >= 14)
        assertEquals(ticks.sorted(), ticks)
    }

    @Test fun `windowed games-x spreads across the plot`() {
        // 10-game window of 139 used raw n/size, squeezing every dot into
        // the right 7% of the plot. Labels stay true numbers; positions
        // spread edge to edge like Time mode already does.
        val xs = gamesXValues((130..139).toList(), 139, full = false)
        assertEquals(0f, xs.first())
        assertEquals(1f, xs.last())
        assertTrue(xs.zipWithNext().all { (a, b) -> b - a > 0.05f })
    }

    @Test fun `full-history games-x keeps true proportions`() {
        val xs = gamesXValues(listOf(0, 1, 139), 139, full = true)
        assertEquals(listOf(0f, 1f / 139, 1f), xs)
    }

    @Test fun `single-game window does not divide by zero`() {
        assertEquals(listOf(0f), gamesXValues(listOf(139), 139, full = false))
    }

    @Test fun `games ticks land on round numbers`() {
        // 209-game history: multiples of 50, endpoints framed, not forced.
        val ticks = gamesXTicks(0, 209)
        assertEquals(listOf(0, 50, 100, 150, 200), ticks)
    }

    @Test fun `games ticks stay absolute inside windows`() {
        // 10-game window 200..209: step 2 keeps true numbers, ~5 lines.
        val ticks = gamesXTicks(200, 209)
        assertEquals(listOf(200, 202, 204, 206, 208), ticks)
    }

    @Test fun `degenerate games span yields no interior ticks`() {
        assertEquals(emptyList<Int>(), gamesXTicks(139, 139))
    }

    @Test fun `short time span ticks whole days`() {
        // 6 days of evening games: daily local midnights, strictly inside.
        val day = 86_400_000L
        val first = 1_757_000_000_000L // fixed anchor, tz-relative asserts
        val ticks = timeXTicks(first, first + 6 * day)
        assertTrue("need several dailies, got $ticks", ticks.size in 3..7)
        val cal = java.util.Calendar.getInstance()
        for (t in ticks) {
            cal.timeInMillis = t
            assertEquals(0, cal.get(java.util.Calendar.HOUR_OF_DAY))
            assertEquals(0, cal.get(java.util.Calendar.MINUTE))
            assertTrue(t in first..first + 6 * day)
        }
        assertEquals(ticks.sorted(), ticks)
    }

    @Test fun `month time span ticks mondays`() {
        val day = 86_400_000L
        val first = 1_757_000_000_000L
        val ticks = timeXTicks(first, first + 45 * day)
        assertTrue("need several weeklies, got $ticks", ticks.size in 4..8)
        val cal = java.util.Calendar.getInstance()
        for (t in ticks) {
            cal.timeInMillis = t
            assertEquals(java.util.Calendar.MONDAY, cal.get(java.util.Calendar.DAY_OF_WEEK))
        }
    }

    @Test fun `year time span ticks month starts`() {
        val day = 86_400_000L
        val first = 1_757_000_000_000L
        val ticks = timeXTicks(first, first + 400 * day)
        assertTrue("need several monthlies, got $ticks", ticks.size in 4..14)
        val cal = java.util.Calendar.getInstance()
        for (t in ticks) {
            cal.timeInMillis = t
            assertEquals(1, cal.get(java.util.Calendar.DAY_OF_MONTH))
        }
    }

    @Test fun `same-day time span yields no ticks`() {
        val first = 1_757_000_000_000L
        assertEquals(emptyList<Long>(), timeXTicks(first, first + 3_600_000L))
    }
}
