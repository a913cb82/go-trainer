package com.gotrainer.nine

import com.gotrainer.nine.game.CandidateSelector
import com.gotrainer.nine.game.PoolEntry
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * BUG: choices mode ranks and colours moves by BLACK-relative score even when
 * the human plays White.
 *
 * The engine analyzes in Black perspective (raw scoreLead, sorted best-first
 * for Black) and GameViewModel converts only strongWinrate to the human side —
 * strongScore/scoreGap stay Black-relative. So with White to move, the "good"
 * group holds Black's best moves (= White's worst) and the pills show
 * Black-relative points with inverted colours.
 *
 * This test feeds selectBestWorst EXACTLY what KataGoGtpEngine.candidates
 * feeds it today for a White-to-move position (Black-perspective scores) and
 * asserts the good group holds White's best move.
 */
class WhitePerspectiveTest {
    // Black-perspective scores: A is best for Black, E is best for White.
    private val byPolicy = listOf(
        PoolEntry(0, 0, humanPolicy = 0.30, strongWinrate = 0.80, strongScore = 6.0), // A
        PoolEntry(1, 1, humanPolicy = 0.25, strongWinrate = 0.65, strongScore = 3.0), // B
        PoolEntry(2, 2, humanPolicy = 0.20, strongWinrate = 0.50, strongScore = 0.0), // C
        PoolEntry(3, 3, humanPolicy = 0.15, strongWinrate = 0.35, strongScore = -3.0), // D
        PoolEntry(4, 4, humanPolicy = 0.10, strongWinrate = 0.20, strongScore = -6.0), // E
    )
    private val byScore = byPolicy.sortedByDescending { it.strongScore }

    @Test fun `best group holds white's best move when white is to move`() {
        val out = CandidateSelector.selectBestWorst(byPolicy, byScore, best = 1, worst = 1, toMove = -1)
        val good = out.first { it.tag == "good" }
        assertEquals("good group should be E (best for White), was ${good.x},${good.y}", 4, good.x)
    }

    @Test fun `worst group holds white's worst move when white is to move`() {
        val out = CandidateSelector.selectBestWorst(byPolicy, byScore, best = 1, worst = 1, toMove = -1)
        val bad = out.first { it.tag == "overconcentrated" }
        assertEquals("worst group should be A (worst for White), was ${bad.x},${bad.y}", 0, bad.x)
    }
}
