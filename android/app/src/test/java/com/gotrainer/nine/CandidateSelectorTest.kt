package com.gotrainer.nine

import com.gotrainer.nine.game.CandidateSelector
import com.gotrainer.nine.game.PoolEntry
import com.gotrainer.nine.game.Rank
import com.gotrainer.nine.game.Strategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateSelectorTest {
    private fun pool(): Pair<List<PoolEntry>, List<PoolEntry>> {
        // human order: A(0,0) best policy but weak; B(1,1) good; C(2,2) best score
        val entries = listOf(
            PoolEntry(0, 0, humanPolicy = 9.0, strongScore = 0.0),
            PoolEntry(1, 1, humanPolicy = 8.0, strongScore = 5.0),
            PoolEntry(2, 2, humanPolicy = 7.0, strongScore = 6.0),
            PoolEntry(3, 3, humanPolicy = 6.0, strongScore = 4.0),
            PoolEntry(4, 4, humanPolicy = 5.0, strongScore = -5.0),
            PoolEntry(5, 5, humanPolicy = 4.0, strongScore = -8.0),
        )
        return entries to entries.sortedByDescending { it.strongScore }
    }

    @Test fun `good-vs-tempting mixes good and bad`() {
        val (byH, byS) = pool()
        val out = CandidateSelector.select(byH, byS, Strategy.GOOD_VS_TEMPTING, Rank.R10K, 5)
        assertEquals(5, out.size)
        assertEquals("A", out[0].label)
        // tags computed vs best score 6.0 with 10k thresholds good=1.5 bad=3.2
        assertTrue(out.any { it.tag == "good" })
        assertTrue(out.any { it.tag == "overconcentrated" })
    }

    @Test fun `human-only takes top policy`() {
        val (byH, byS) = pool()
        val out = CandidateSelector.select(byH, byS, Strategy.HUMAN_ONLY, Rank.R10K, 3)
        assertEquals(listOf(0 to 0, 1 to 1, 2 to 2), out.map { it.x to it.y })
    }

    @Test fun `strong-only takes top score`() {
        val (byH, byS) = pool()
        val out = CandidateSelector.select(byH, byS, Strategy.STRONG_ONLY, Rank.R10K, 2)
        assertEquals(listOf(2 to 2, 1 to 1), out.map { it.x to it.y })
    }

    @Test fun `tesuji leads with best score`() {
        val (byH, byS) = pool()
        val out = CandidateSelector.select(byH, byS, Strategy.TESUJI, Rank.R10K, 3)
        assertEquals(2 to 2, out[0].x to out[0].y)
    }

    @Test fun `dedups coordinates`() {
        val dup = listOf(PoolEntry(1, 1, 9.0, strongScore = 5.0), PoolEntry(1, 1, 8.0, strongScore = 5.0))
        val out = CandidateSelector.select(dup, dup, Strategy.HUMAN_ONLY, Rank.R10K, 2)
        assertEquals(1, out.size)
    }

    @Test fun `toEvaluated computes gaps`() {
        val (byH, byS) = pool()
        val cands = CandidateSelector.select(byH, byS, Strategy.STRONG_ONLY, Rank.R10K, 2)
        val evals = CandidateSelector.toEvaluated(cands)
        assertEquals(0.0, evals[0].gap, 1e-9)
        assertEquals(1.0, evals[1].gap, 1e-9)
    }
}
