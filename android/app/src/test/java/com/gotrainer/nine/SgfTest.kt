package com.gotrainer.nine

import com.gotrainer.nine.game.MoveRec
import com.gotrainer.nine.game.Sgf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SgfTest {
    @Test fun `sgf format with moves and pass`() {
        val sgf = Sgf.boardToSgf(
            listOf(MoveRec(0, 0, 1), MoveRec(8, 8, -1), MoveRec(-1, -1, 1)),
            komi = 7.0,
        )
        assertEquals("(;GM[1]FF[4]SZ[9]KM[7.0]RU[Japanese];B[aa];W[ii];B[])", sgf)
    }

    @Test fun `empty game`() {
        val sgf = Sgf.boardToSgf(emptyList())
        assertTrue(sgf.startsWith("(;GM[1]FF[4]SZ[9]"))
        assertTrue(sgf.endsWith(")"))
    }
}
