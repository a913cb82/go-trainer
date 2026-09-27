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
}
