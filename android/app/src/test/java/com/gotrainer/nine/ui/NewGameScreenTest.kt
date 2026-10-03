package com.gotrainer.nine.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class NewGameScreenTest {
    @Test fun `free play derives Off`() {
        assertEquals(Guidance.OFF, guidancePreset(multipleChoice = false, best = 2, worst = 3))
        assertEquals(Guidance.OFF, guidancePreset(multipleChoice = true, best = 0, worst = 0))
    }

    @Test fun `canonical triples derive their presets`() {
        assertEquals(Guidance.HINTS, guidancePreset(multipleChoice = true, best = 2, worst = 0))
        assertEquals(Guidance.FULL, guidancePreset(multipleChoice = true, best = 2, worst = 3))
    }

    @Test fun `anything else is Custom`() {
        assertEquals(Guidance.CUSTOM, guidancePreset(multipleChoice = true, best = 3, worst = 1))
        assertEquals(Guidance.CUSTOM, guidancePreset(multipleChoice = true, best = 2, worst = 1))
    }

    @Test fun `presets write canonical triples`() {
        assertEquals(Triple(false, 2, 0), Guidance.OFF.applyTo(best = 2, worst = 0))
        assertEquals(Triple(true, 2, 0), Guidance.HINTS.applyTo(best = 5, worst = 5))
        assertEquals(Triple(true, 2, 3), Guidance.FULL.applyTo(best = 0, worst = 0))
    }
}
