package com.gotrainer.nine

import com.gotrainer.nine.game.GoBoard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Positional superko (Chinese rules): a placement is illegal iff its board
 * has occurred before. No ban points, no liberty checks.
 *
 * - Snapback retake produces a NEW board (three stones stay dead) -> legal.
 * - Immediate ko retake reproduces the previous board -> illegal.
 * - Delayed ko retake (moves played since) produces a new board -> legal.
 */
class KoSnapbackTest {
    /** A position under construction: board plus every hash since game start. */
    private class Pos {
        val b = GoBoard()
        val seen = HashSet<Long>()

        init {
            seen.add(b.positionHash())
        }

        /** Play, recording the new position; returns captured count (-1 illegal). */
        fun mv(color: Int, x: Int, y: Int): Int {
            val c = b.play(color, x, y, seen)
            if (c >= 0) seen.add(b.positionHash())
            return c
        }
    }

    /**
     * Snapback (top-left corner, White to play):
     *   |.BW...  White throws in at (0,0), taking one black stone ...
     *   |WWB...
     *   |BBB...
     * ... Black retakes (1,0) at once, taking THREE white stones. New board.
     */
    @Test fun `snapback recapture is legal immediately`() {
        val p = Pos()
        p.mv(1, 1, 0)
        p.mv(-1, 2, 0)
        p.mv(-1, 0, 1)
        p.mv(-1, 1, 1)
        p.mv(1, 2, 1)
        p.mv(1, 0, 2)
        p.mv(1, 1, 2)
        p.mv(1, 2, 2)
        // White throws in, capturing one black stone ...
        assertEquals(1, p.mv(-1, 0, 0))
        // ... Black retakes at once, capturing three white stones.
        assertEquals(3, p.mv(1, 1, 0))
        assertEquals(0, p.b[0, 0])
        assertEquals(0, p.b[0, 1])
        assertEquals(0, p.b[1, 1])
        assertEquals(1, p.b[1, 0])
    }

    /**
     * Classic ko shape: the White wall never touches the mouth (2,1), so the
     * capturing stone is left LONE with one liberty:
     *   . W B .
     *   W B . B
     *   . W B .
     */
    private fun classicKo(p: Pos) {
        p.mv(1, 1, 1) // victim
        p.mv(-1, 0, 1)
        p.mv(-1, 1, 0)
        p.mv(-1, 1, 2)
        p.mv(1, 3, 1)
        p.mv(1, 2, 0)
        p.mv(1, 2, 2)
        // White captures the lone black stone ...
        assertEquals(1, p.mv(-1, 2, 1))
    }

    /** Genuine ko: instant B replay repeats the board. */
    @Test fun `true ko recapture stays illegal`() {
        val p = Pos()
        classicKo(p)
        // ... Black may NOT take back at once (repeats the position).
        assertEquals(-1, p.mv(1, 1, 1))
    }

    /** Corner ko: retaking (0,0) would restore the previous board. */
    @Test fun `corner ko recapture stays illegal`() {
        val p = Pos()
        p.mv(-1, 0, 0) // victim
        p.mv(1, 0, 1)
        p.mv(-1, 2, 0)
        p.mv(-1, 1, 1)
        // Black captures the lone white stone ...
        assertEquals(1, p.mv(1, 1, 0))
        // ... White may NOT take back at once (repeats the position).
        assertEquals(-1, p.mv(-1, 0, 0))
    }

    /**
     * Connected-corner snapback chain (top-left corner, White to play):
     *   WW.BW  White takes 1  WWW.W  Black snapback takes 3  ...BW
     *   BBBW.  --------------> BBBW.  ----------------------> BBBW.
     *   White retakes 1: ..W.W / BBBW. — every board is new, so all legal.
     *
     * The sting: after Black's snapback, B(3,0) is a LONE stone with ONE
     * liberty — a lone-stone-one-liberty heuristic would ban White's retake.
     * Board-state comparison allows it: P3 matches nothing before it.
     */
    @Test fun `connected corner snapback chain stays legal`() {
        val p = Pos()
        p.mv(-1, 0, 0)
        p.mv(1, 3, 0)
        p.mv(-1, 1, 0)
        p.mv(1, 0, 1)
        p.mv(1, 1, 1)
        p.mv(1, 2, 1)
        p.mv(-1, 4, 0)
        p.mv(-1, 3, 1)
        // White takes the black stone in atari ...
        assertEquals(1, p.mv(-1, 2, 0))
        assertEquals(-1, p.b[2, 0])
        assertEquals(0, p.b[3, 0])
        // ... Black snapback takes the three-white group ...
        assertEquals(3, p.mv(1, 3, 0))
        assertEquals(0, p.b[0, 0])
        assertEquals(0, p.b[1, 0])
        assertEquals(0, p.b[2, 0])
        assertEquals(1, p.b[3, 0])
        // ... White retakes the lone black stone. New board, no repeat.
        assertEquals(1, p.mv(-1, 2, 0))
        assertEquals(-1, p.b[2, 0])
        assertEquals(0, p.b[3, 0])
    }

    /** Ko fight: after moves elsewhere, the retake is a new board, so legal. */
    @Test fun `delayed ko retake is legal`() {
        val p = Pos()
        classicKo(p) // White captures ...
        assertEquals(-1, p.mv(1, 1, 1)) // ... instant retake banned ...
        assertEquals(0, p.mv(1, 0, 8)) // ... ko threats elsewhere ...
        assertEquals(0, p.mv(-1, 8, 8))
        assertEquals(1, p.mv(1, 1, 1)) // ... delayed retake takes one, legal.
        assertTrue(p.seen.contains(p.b.positionHash()))
    }
}
