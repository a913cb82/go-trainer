package com.gotrainer.nine

import com.gotrainer.nine.game.BotRatings
import org.junit.Assert.assertEquals
import org.junit.Test

/** Rank display labels (scale-free survivors of the WHR migration). */
class BotRatingsTest {
    @Test fun `labels follow app style at boundaries`() {
        assertEquals("30k", BotRatings.rankLabel(0.0))
        assertEquals("1k", BotRatings.rankLabel(29.0))
        assertEquals("1k", BotRatings.rankLabel(29.7)) // kyu rounds toward weaker
        assertEquals("1d", BotRatings.rankLabel(30.0))
        assertEquals("9d", BotRatings.rankLabel(38.0))
        assertEquals("30k", BotRatings.rankLabel(-5.0)) // floor holds
        assertEquals("9d", BotRatings.rankLabel(99.0)) // cap holds
    }
}
