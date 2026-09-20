package com.gotrainer.nine.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The winrate graph doubles as the review scrubber. These pin the drag
 * contract so UI fiddling can't silently break reviewing.
 */
class WinrateGraphTest {
    @Test fun `left edge reviews from move zero`() {
        assertEquals(0, reviewIndexAt(0f, 1000, 10))
    }

    @Test fun `middle maps to the move shown under the finger`() {
        // 50% of an n=10 game = after the 5th move.
        assertEquals(5, reviewIndexAt(500f, 1000, 10))
    }

    @Test fun `right edge means live play`() {
        assertNull(reviewIndexAt(1000f, 1000, 10))
        assertNull(reviewIndexAt(1200f, 1000, 10))
    }

    @Test fun `short games and empty histories never review`() {
        assertNull(reviewIndexAt(50f, 1000, 0))
        assertNull(reviewIndexAt(50f, 1000, 1))
    }
}
