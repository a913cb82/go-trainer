package com.gotrainer.nine.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoneAnimTest {
    @Test fun `place starts big, high and ringless`() {
        val f = placeFrame(0f)
        assertEquals(1.33f, f.scale, 0.001f)
        assertEquals(-0.462f, f.dyCells, 0.001f)
        assertEquals(0f, f.ringAlpha, 0.001f)
    }

    @Test fun `place lands at rest with full ring`() {
        val f = placeFrame(1f)
        assertEquals(1f, f.scale, 0.001f)
        assertEquals(0f, f.dyCells, 0.001f)
        assertEquals(1f, f.ringAlpha, 0.001f)
    }

    @Test fun `place eases out and ring waits for the last third`() {
        val f = placeFrame(0.5f)
        assertEquals(1.33f - 0.33f * 0.875f, f.scale, 0.001f)
        assertEquals(0f, f.ringAlpha, 0.001f)
        assertEquals(0.5f, placeFrame(0.825f).ringAlpha, 0.01f)
    }

    @Test fun `shrink is a smoothstep to nothing`() {
        assertEquals(1f, shrinkScale(0f), 0.001f)
        assertEquals(0.5f, shrinkScale(0.5f), 0.001f)
        assertEquals(0f, shrinkScale(1f), 0.001f)
    }

    @Test fun `shrink never reverses`() {
        var prev = 2f
        for (i in 0..20) {
            val s = shrinkScale(i / 20f)
            assertTrue(s <= prev)
            prev = s
        }
    }
}
