package com.gotrainer.nine

import com.gotrainer.nine.game.Strategy
import com.gotrainer.nine.game.availableStrategies
import com.gotrainer.nine.ui.tagStyle
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Count-based training styles: names, availability per n, and group colors.
 */
class StrategyFamiliesTest {
    @Test fun `five choices offers all six styles in order`() {
        assertEquals(
            listOf(
                Strategy.TESUJI,
                Strategy.GOOD_VS_TEMPTING,
                Strategy.SPLIT_3_2,
                Strategy.BLUNDER_CHECK,
                Strategy.HUMAN_ONLY,
                Strategy.STRONG_ONLY,
            ),
            availableStrategies(5),
        )
    }

    @Test fun `three choices offers four styles, degenerate splits dropped`() {
        assertEquals(
            listOf(
                Strategy.TESUJI,
                Strategy.GOOD_VS_TEMPTING,
                Strategy.HUMAN_ONLY,
                Strategy.STRONG_ONLY,
            ),
            availableStrategies(3),
        )
    }

    @Test fun `labels count good and bad for five`() {
        assertEquals("1 Good, 4 Bad", Strategy.TESUJI.label(5))
        assertEquals("2 Good, 3 Bad", Strategy.GOOD_VS_TEMPTING.label(5))
        assertEquals("3 Good, 2 Bad", Strategy.SPLIT_3_2.label(5))
        assertEquals("4 Good, 1 Bad", Strategy.BLUNDER_CHECK.label(5))
        assertEquals("5 Human-like", Strategy.HUMAN_ONLY.label(5))
        assertEquals("5 Strongest", Strategy.STRONG_ONLY.label(5))
    }

    @Test fun `labels count good and bad for three`() {
        assertEquals("1 Good, 2 Bad", Strategy.TESUJI.label(3))
        assertEquals("2 Good, 1 Bad", Strategy.GOOD_VS_TEMPTING.label(3))
        assertEquals("3 Human-like", Strategy.HUMAN_ONLY.label(3))
        assertEquals("3 Strongest", Strategy.STRONG_ONLY.label(3))
    }

    @Test fun `group colors are green for good, red for bad, yellow for middle`() {
        assertEquals("#27864a", tagStyle("good").hex)
        assertEquals("#c0392b", tagStyle("overconcentrated").hex)
        assertEquals("#b7791f", tagStyle("ok").hex)
    }
}
