package com.gotrainer.nine.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test fun `narrow zoom keeps every rank as a gridline`() {
        // Padded (10.2, 12.8) used to thin to [9, 11, 13, 15], leaving a
        // lone 11k line on screen. Covering step-1 keeps 10k through 13k.
        assertEquals(listOf(10, 11, 12, 13), rankAxisTicks(10.2, 12.8))
    }

    @Test fun `covering grid never overshoots far`() {
        val ticks = rankAxisTicks(9.5, 20.0)
        assertEquals(listOf(9, 12, 15, 18, 21), ticks)
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

    @Test fun `dates render day before month`() {
        // British order whatever the device locale; field-derived so it
        // holds in every timezone (no single instant is the 3rd worldwide).
        val ts = 1_759_465_200_000L // 2025-10-03 12:00 UTC
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = ts
        val expected = "${cal.get(java.util.Calendar.DAY_OF_MONTH)}/${cal.get(java.util.Calendar.MONTH) + 1}"
        assertEquals(expected, dayMonthFormat().format(java.util.Date(ts)))
    }

    @Test fun `pan preserves span and clamps at the ends`() {
        val v = XView(0.2f, 0.6f).panned(0.1f)
        assertEquals(0.3f, v.start, 1e-6f)
        assertEquals(0.7f, v.end, 1e-6f)
        val lo = XView(0.2f, 0.6f).panned(-0.5f)
        assertEquals(0f, lo.start, 1e-6f)
        assertEquals(0.4f, lo.end, 1e-4f)
        val hi = XView(0.2f, 0.6f).panned(0.9f)
        assertEquals(0.6f, hi.start, 1e-4f)
        assertEquals(1f, hi.end, 1e-6f)
    }

    @Test fun `full viewport matches its preset, partial matches none`() {
        assertTrue(XView(0f, 1f).isFull)
        assertFalse(XView(0f, 0.5f).isFull)
    }

    @Test fun `zoom keeps the focus point under the fingers`() {
        // Focus at plot-center of a full view, 2x in: view halves around it.
        val v = XView(0f, 1f).zoomed(focus = 0.5f, factor = 2f)
        assertEquals(0.25f, v.start, 1e-6f)
        assertEquals(0.75f, v.end, 1e-6f)
        // Zooming out past full clamps back to full.
        val back = XView(0.25f, 0.75f).zoomed(focus = 0.5f, factor = 0.25f)
        assertEquals(0f, back.start, 1e-6f)
        assertEquals(1f, back.end, 1e-6f)
        // Minimum span stops runaway zoom.
        val tiny = XView(0.5f, 0.52f).zoomed(focus = 0.51f, factor = 10f)
        assertEquals(MIN_VIEW_SPAN, tiny.span, 1e-6f)
    }

    @Test fun `double tap zooms 2x centered on the tap`() {
        val v = XView(0f, 1f).zoomAt(fraction = 0.75f)
        assertEquals(0.5f, v.span, 1e-6f)
        assertEquals(0.75f, v.start + v.span / 2, 1e-6f)
    }

    @Test fun `double tap near an edge clamps inside the range`() {
        val left = XView(0f, 1f).zoomAt(fraction = 0.05f)
        assertEquals(0f, left.start, 1e-6f)
        val right = XView(0f, 1f).zoomAt(fraction = 0.97f)
        assertEquals(1f, right.end, 1e-6f)
    }

    @Test fun `y holds when the visible range stays inside with margin`() {
        // Small pan, small zoom-out, small zoom-in: no rescale either way.
        val frozen = 10.0 to 14.0
        assertFalse(yRescaleWanted(frozen, 10.2 to 13.8))
        assertFalse(yRescaleWanted(frozen, 9.8 to 14.2))
        assertFalse(yRescaleWanted(frozen, 10.5 to 13.5))
    }

    @Test fun `y rescales on overflow and on big zoom-in`() {
        val frozen = 10.0 to 14.0
        assertTrue(yRescaleWanted(frozen, 9.0 to 13.0))
        assertTrue(yRescaleWanted(frozen, 11.0 to 15.0))
        assertTrue(yRescaleWanted(frozen, 11.0 to 13.0))
    }

    @Test fun `padded domain covers the visible range with headroom`() {
        val (lo, hi) = paddedDomain(10.2 to 13.8)
        assertTrue("lo=$lo", lo <= 10.2)
        assertTrue("hi=$hi", hi >= 13.8)
        assertTrue("headroom", (hi - lo) > (13.8 - 10.2))
    }

    @Test fun `furniture keeps in-range ticks`() {
        assertTrue(yTickVisible(10, 9.5f, 12.5f))
        assertFalse(yTickVisible(9, 9.5f, 12.5f))
    }

    @Test fun `furniture drops ticks outside the map domain`() {
        // Zoomed landing: ticks 9 and 10 below a (10.5, 11.5) map used to
        // coerce onto the top edge and superimpose (user screenshot).
        assertFalse(yTickVisible(9, 10.5f, 11.5f))
        assertFalse(yTickVisible(10, 10.5f, 11.5f))
        assertFalse(yTickVisible(12, 10.5f, 11.5f))
    }

    @Test fun `visible range keeps edge neighbors for line continuity`() {
        val ts = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        assertEquals(0..4, visibleRange(5, ts, XView(0f, 1f)))
        assertEquals(0..3, visibleRange(5, ts, XView(0f, 0.5f)))
        assertEquals(1..4, visibleRange(5, ts, XView(0.5f, 1f)))
        assertEquals(0..-1, visibleRange(0, emptyList(), XView(0f, 1f)))
    }
}
