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
        assertTrue(ticks.last() <= 40)
    }

    @Test fun `step thinning keeps the padded top tick`() {
        // Real 139-game history: data peaks at idx ~12.3 but the thinned
        // axis topped at 10, flattening the whole recent climb onto the top
        // gridline (axis read 30k..20k, everything stronger cut off).
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
}
