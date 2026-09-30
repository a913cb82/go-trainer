package com.gotrainer.nine

import com.gotrainer.nine.game.GoBoard
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureFxTest {
    private fun empty() = List(9) { List(9) { 0 } }
    private fun board(vararg stones: Triple<Int, Int, Int>): List<List<Int>> {
        val b = List(9) { MutableList(9) { 0 } }
        for ((x, y, v) in stones) b[y][x] = v
        return b
    }

    @Test fun `plain placement captures nothing`() {
        val before = empty()
        val after = board(Triple(4, 4, 1))
        assertEquals(emptyList<com.gotrainer.nine.game.CapturedStone>(), GoBoard.capturedBy(before, after))
    }

    @Test fun `capture reports stones with their color`() {
        val before = board(Triple(4, 4, -1), Triple(3, 4, 1), Triple(5, 4, 1), Triple(4, 3, 1))
        val after = board(
            Triple(4, 4, 1), Triple(3, 4, 1), Triple(5, 4, 1), Triple(4, 3, 1), Triple(4, 5, 1),
        ).let { b ->
            // White (4,4) is gone: it was captured, not overwritten.
            b.mapIndexed { y, row -> row.mapIndexed { x, v -> if (x == 4 && y == 4) 0 else v } }
        }
        assertEquals(
            listOf(com.gotrainer.nine.game.CapturedStone(4, 4, -1)),
            GoBoard.capturedBy(before, after),
        )
    }

    @Test fun `diff reports disappearances either direction — the VM gates undo`() {
        // Undoing a capture also removes a stone (the un-played one), so the
        // primitive reports it; no-pop-on-undo holds because the VM only
        // computes fx on real plays, never on undo/review/new-game.
        val playing = board(Triple(4, 5, 1))
        val restored = board(Triple(4, 4, -1))
        assertEquals(
            listOf(com.gotrainer.nine.game.CapturedStone(4, 5, 1)),
            GoBoard.capturedBy(playing, restored),
        )
    }
}
