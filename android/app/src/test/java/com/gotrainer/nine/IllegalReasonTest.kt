package com.gotrainer.nine

import com.gotrainer.nine.game.GoBoard
import com.gotrainer.nine.game.MoveRec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Player-facing illegal-move reasons (see GameViewModel.onBoardTap).
 * TDD pin for the message split: Occupied / Suicide / Ko.
 */
class IllegalReasonTest {
    @Test fun `occupied point reports occupied`() {
        val b = GoBoard()
        b.play(1, 3, 3)
        assertEquals(GoBoard.IllegalReason.OCCUPIED, b.illegalReason(-1, 3, 3))
        assertEquals("Occupied.", GoBoard.messageFor(GoBoard.IllegalReason.OCCUPIED))
    }

    @Test fun `surrounded point reports suicide`() {
        val b = GoBoard()
        b.play(-1, 0, 1)
        b.play(-1, 1, 0)
        b.play(-1, 1, 2)
        b.play(-1, 2, 1)
        assertEquals(GoBoard.IllegalReason.SUICIDE, b.illegalReason(1, 1, 1))
        assertEquals("Suicide.", GoBoard.messageFor(GoBoard.IllegalReason.SUICIDE))
    }

    @Test fun `immediate ko retake reports ko with threat hint`() {
        // Classic ko from KoSnapbackTest: white captures at (2,1), black retake repeats.
        val b = GoBoard()
        val seen = HashSet<Long>()
        seen.add(b.positionHash())
        fun mv(color: Int, x: Int, y: Int): Int {
            val c = b.play(color, x, y, seen)
            if (c >= 0) seen.add(b.positionHash())
            return c
        }
        mv(1, 1, 1)
        mv(-1, 0, 1)
        mv(-1, 1, 0)
        mv(-1, 1, 2)
        mv(1, 3, 1)
        mv(1, 2, 0)
        mv(1, 2, 2)
        assertEquals(1, mv(-1, 2, 1))
        assertEquals(GoBoard.IllegalReason.SUPERKO, b.illegalReason(1, 1, 1, seen))
        assertEquals("Ko. Play elsewhere.", GoBoard.messageFor(GoBoard.IllegalReason.SUPERKO))
    }

    @Test fun `reported E9 snapback reports ko not suicide`() {
        // E9-first + pass history from SnapbackIssueTest: retake repeats E9-alone.
        val hist = mutableListOf<MoveRec>()
        fun add(color: Int, x: Int, y: Int) { hist.add(MoveRec(x, y, color)) }
        add(1, 2, 0); add(1, 2, 1); add(1, 3, 1); add(1, 4, 1)
        add(-1, 1, 0); add(-1, 5, 1); add(-1, 6, 0); add(-1, 6, 1)
        add(-1, 4, 0)
        hist.add(MoveRec(-1, -1, 1))
        add(-1, 3, 0)
        add(1, 5, 0)
        val rep = GoBoard.replay(hist)
        assertEquals(GoBoard.IllegalReason.SUPERKO, rep.board.illegalReason(-1, 4, 0, rep.hashes))
    }

    @Test fun `legal move has no reason`() {
        val b = GoBoard()
        assertNull(b.illegalReason(1, 4, 4))
    }
}
