package com.gotrainer.nine

import com.gotrainer.nine.game.GoBoard
import com.gotrainer.nine.game.MoveRec
import org.junit.Assert.assertEquals
import org.junit.Test

class GoBoardTest {
    @Test fun `empty board has no stones`() {
        val b = GoBoard()
        assertEquals(0, b[4, 4])
        assertEquals(81, b.legalMoves(1).size)
    }

    @Test fun `play and capture`() {
        val b = GoBoard()
        // Surround a white stone at (1,1): black at 0,1 1,0 2,1 then capture at 1,2
        b.play(1, 0, 1)
        b.play(-1, 1, 1)
        b.play(1, 1, 0)
        b.play(1, 2, 1)
        assertEquals(-1, b[1, 1])
        val captured = b.play(1, 1, 2)
        assertEquals(1, captured)
        assertEquals(0, b[1, 1])
    }

    @Test fun `suicide is illegal`() {
        val b = GoBoard()
        b.play(-1, 0, 1)
        b.play(-1, 1, 0)
        b.play(-1, 1, 2)
        b.play(-1, 2, 1)
        assertEquals(-1, b.play(1, 1, 1)) // surrounded empty point
    }

    @Test fun `occupied point is illegal`() {
        val b = GoBoard()
        b.play(1, 3, 3)
        assertEquals(-1, b.play(-1, 3, 3))
    }

    @Test fun `replay rebuilds board and position hashes`() {
        // Passes never change the board, so they add no new hash.
        val hist = listOf(
            MoveRec(1, 0, 1),
            MoveRec(1, 1, -1),
            MoveRec(-1, -1, 1),
        )
        val rep = GoBoard.replay(hist)
        assertEquals(1, rep.board[1, 0])
        assertEquals(-1, rep.board[1, 1])
        assertEquals(3, rep.hashes.size) // empty + 2 stone plies
        assertEquals(rep.board.positionHash(), GoBoard.fromHistory(hist).positionHash())
    }

    @Test fun `gtp coord skips I`() {
        assertEquals("A9", GoBoard.gtpCoord(0, 0))
        assertEquals("J9", GoBoard.gtpCoord(8, 0))
        assertEquals("A1", GoBoard.gtpCoord(0, 8))
    }
}
