package com.gotrainer.nine

import com.gotrainer.nine.game.GoBoard
import com.gotrainer.nine.game.MoveRec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bug report: snapback at E9 flagged illegal.
 * Screenshot: White to play, Black just played F9 (5,0) capturing White D9 (3,0) + E9 (4,0).
 * White E9 retake should capture F9 (snapback) but app says "Illegal move".
 *
 * Diagnosis aid: with the visible board, E9 is NOT suicide (captures F9, keeps D9 liberty),
 * so the only remaining ban is positional superko (trial board seen before).
 */
class SnapbackIssueTest {
    /** Position helper mirroring KoSnapbackTest.Pos. */
    private class Pos {
        val b = GoBoard()
        val seen = HashSet<Long>()
        init { seen.add(b.positionHash()) }
        fun mv(color: Int, x: Int, y: Int): Int {
            val c = b.play(color, x, y, seen)
            if (c >= 0) seen.add(b.positionHash())
            return c
        }
    }

    /** Build the screenshot surround (top edge) without D9/E9/F9, then drive the capture. */
    private fun surround(p: Pos) {
        // Black wall
        p.mv(1, 2, 0) // C9
        p.mv(1, 2, 1) // C8
        p.mv(1, 3, 1) // D8
        p.mv(1, 4, 1) // E8
        // White surround
        p.mv(-1, 1, 0) // B9
        p.mv(-1, 5, 1) // F8
        p.mv(-1, 6, 0) // G9
        p.mv(-1, 6, 1) // G8
        p.mv(-1, 7, 1) // H8
        p.mv(-1, 8, 1) // J8
        p.mv(-1, 4, 2) // E7
        p.mv(-1, 5, 2) // F7
        p.mv(-1, 6, 2) // G7
    }

    @Test fun `E9 retake is not suicide - captures F9 with fresh history`() {
        // Direct board (no history): prove the shape itself is legal.
        val b = GoBoard()
        // surround
        b.play(1, 2, 0); b.play(1, 2, 1); b.play(1, 3, 1); b.play(1, 4, 1)
        b.play(-1, 1, 0); b.play(-1, 5, 1); b.play(-1, 6, 0); b.play(-1, 6, 1)
        // Black F9 lone stone
        b.play(1, 5, 0)
        // White E9 should capture F9 (1 stone), D9 stays empty -> legal.
        val c = b.play(-1, 4, 0, emptySet())
        assertEquals(1, c)
    }

    @Test fun `E9-first then D9 then F9 capture then E9 retake repeats - superko bans`() {
        val p = Pos()
        surround(p)
        // White E9 first ...
        assertEquals(0, p.mv(-1, 4, 0))
        // Black passes elsewhere (no board change, rest identical) ...
        // (pass = no mv call; positional superko ignores passes)
        // White D9 ...
        assertEquals(0, p.mv(-1, 3, 0))
        // Black F9 captures D9+E9 ...
        assertEquals(2, p.mv(1, 5, 0))
        // White E9 retake repeats the E9-alone board -> banned under positional superko.
        assertEquals(-1, p.mv(-1, 4, 0))
    }

    @Test fun `E9-first with tenuki between then retake is legal - rest differs`() {
        val p = Pos()
        surround(p)
        assertEquals(0, p.mv(-1, 4, 0)) // White E9
        assertEquals(0, p.mv(1, 0, 8)) // Black tenuki elsewhere (changes rest)
        assertEquals(0, p.mv(-1, 8, 8)) // White tenuki elsewhere
        assertEquals(0, p.mv(-1, 3, 0)) // White D9 (needs White move; use two-step: black elsewhere first)
        // NOTE: turn order above is illustrative; the point is rest changed,
        // so the retake board is new. Full sequence below is turn-correct:
    }

    @Test fun `turn-correct E9-first with tenuki retake is legal`() {
        val p = Pos()
        surround(p)
        p.mv(-1, 4, 0) // W E9
        p.mv(1, 0, 8) // B elsewhere
        p.mv(-1, 3, 0) // W D9
        p.mv(1, 7, 4) // B elsewhere (instead of immediate F9)
        // White moves elsewhere to flip turn, black captures, white retakes:
        p.mv(-1, 8, 8) // W elsewhere
        assertEquals(2, p.mv(1, 5, 0)) // B F9 captures D9+E9
        // Rest differs from the first E9-alone (two elsewhere stones added) -> legal.
        assertEquals(1, p.mv(-1, 4, 0))
    }

    @Test fun `D9-first then E9 then F9 capture then E9 retake is legal even with passes`() {
        val p = Pos()
        surround(p)
        assertEquals(0, p.mv(-1, 3, 0)) // White D9 first
        // Black passes (no rest change)
        assertEquals(0, p.mv(-1, 4, 0)) // White E9 (Black passed, so White moves again - simulated via direct play)
        // Black F9 captures both
        assertEquals(2, p.mv(1, 5, 0))
        // Retake is E9-alone, but before we had D9-alone -> different board -> legal.
        assertEquals(1, p.mv(-1, 4, 0))
    }

    @Test fun `replay-based legality matches Pos for the reported shape`() {
        // Same as E9-first + passes, but through GoBoard.replay (the VM path).
        val hist = mutableListOf<MoveRec>()
        fun add(color: Int, x: Int, y: Int) { hist.add(MoveRec(x, y, color)) }
        add(1, 2, 0); add(1, 2, 1); add(1, 3, 1); add(1, 4, 1)
        add(-1, 1, 0); add(-1, 5, 1); add(-1, 6, 0); add(-1, 6, 1)
        add(-1, 4, 0) // W E9 first
        // B pass (no entry changes board, but add explicit pass to mirror VM history)
        hist.add(MoveRec(-1, -1, 1))
        add(-1, 3, 0) // W D9
        add(1, 5, 0) // B F9 captures 2
        val rep = GoBoard.replay(hist)
        // Current board: D9 empty, E9 empty, F9 black
        assertEquals(0, rep.board[3, 0])
        assertEquals(0, rep.board[4, 0])
        assertEquals(1, rep.board[5, 0])
        // White E9 via VM path: currentPosition + play
        val c = rep.board.play(-1, 4, 0, rep.hashes)
        assertEquals(-1, c) // banned: repeats E9-alone
        assertTrue(rep.hashes.contains(rep.board.positionHash()))
    }
}
