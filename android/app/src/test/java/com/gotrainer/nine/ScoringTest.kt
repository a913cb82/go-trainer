package com.gotrainer.nine

import com.gotrainer.nine.game.Scoring
import org.junit.Assert.assertEquals
import org.junit.Test

class ScoringTest {
    @Test fun `formatLead trims decimals`() {
        assertEquals("Black +5", Scoring.formatLead(5.0))
        assertEquals("White +4.5", Scoring.formatLead(-4.5))
        assertEquals("Draw", Scoring.formatLead(0.0))
    }
}
