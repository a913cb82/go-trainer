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

    @Test fun `review line sits on the last shown dot`() {
        // Dots at (i + 0.5) * step: the line belongs on the dot, not half a
        // step right of it (the old shown * step put it between dots).
        assertEquals(950f, reviewLineX(10, 1000f, 10))
        assertEquals(450f, reviewLineX(5, 1000f, 10))
        assertEquals(0f, reviewLineX(0, 1000f, 10))
    }
}
