package com.gotrainer.nine

import com.gotrainer.nine.game.CandidateSelector
import com.gotrainer.nine.game.PoolEntry
import com.gotrainer.nine.game.Strategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Threshold-free selection contract: rank enters only through the human policy
 * (cumulative-mass pool), quality is ordinal within that pool.
 */
class CandidateSelectorTest {
    /** Policies total 100: 40+30+20+5+3+1+1. Losses are bestScore - score. */
    private fun pool(): Pair<List<PoolEntry>, List<PoolEntry>> {
        val entries = listOf(
            PoolEntry(0, 0, humanPolicy = 40.0, strongScore = 2.0),  // loss 4
            PoolEntry(1, 1, humanPolicy = 30.0, strongScore = 6.0),  // loss 0 (best)
            PoolEntry(2, 2, humanPolicy = 20.0, strongScore = 5.5),  // loss 0.5
            PoolEntry(3, 3, humanPolicy = 5.0, strongScore = 1.0),   // loss 5
            PoolEntry(4, 4, humanPolicy = 3.0, strongScore = 5.0),   // loss 1
            PoolEntry(5, 5, humanPolicy = 1.0, strongScore = 0.0),   // loss 6
            PoolEntry(6, 6, humanPolicy = 1.0, strongScore = 0.5),   // loss 5.5
        )
        return entries to entries.sortedByDescending { it.strongScore }
    }

    @Test fun `human pool covers 85 percent of the policy mass`() {
        // Policies total 100: cumulative 20,40,55,70,80,90 -> 85% is reached
        // inside the sixth move, so the pool is those six.
        val sharp = listOf(20.0, 20.0, 15.0, 15.0, 10.0, 10.0, 5.0, 5.0)
            .mapIndexed { i, p -> PoolEntry(i, 0, humanPolicy = p, strongScore = 10.0 - i) }
        val hp = CandidateSelector.humanPool(sharp)
        assertEquals(6, hp.size)
        assertEquals(listOf(0, 1, 2, 3, 4, 5), hp.map { it.x })
    }

    @Test fun `human pool keeps a floor of five in concentrated positions`() {
        val concentrated = listOf(90.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0)
            .mapIndexed { i, p -> PoolEntry(i, 0, humanPolicy = p, strongScore = 10.0 - i) }
        val hp = CandidateSelector.humanPool(concentrated)
        assertEquals(5, hp.size)
    }

    @Test fun `good vs tempting takes lowest and highest loss inside the human pool`() {
        val (byH, byS) = pool()
        val out = CandidateSelector.select(byH, byS, Strategy.GOOD_VS_TEMPTING, 5)
        assertEquals(5, out.size)
        // Human pool = moves 0,1,2 (loss 4, 0, 0.5). Best two: 1 (0), 2 (0.5).
        // Worst three of the human pool: 2 (0.5), 0 (4) -> only 3 slots, so the
        // third comes from the wider pool by policy order (fill).
        assertEquals(listOf("good", "good", "overconcentrated"), out.take(3).map { it.tag }.sorted())
        assertTrue(out.any { it.tag == "good" })
        assertTrue(out.any { it.tag == "overconcentrated" })
        assertEquals(5, out.map { it.x to it.y }.toSet().size) // no duplicates
    }

    @Test fun `good vs tempting splits 2 plus 3 when the pool is wide enough`() {
        // Human pool = first six (mass 90 >= 85). Losses: 10,0,5,2,15,1;
        // (6,6) and (7,7) stay outside the pool despite the worst scores.
        val wide = listOf(
            PoolEntry(0, 0, humanPolicy = 20.0, strongScore = 90.0),
            PoolEntry(1, 1, humanPolicy = 20.0, strongScore = 100.0),
            PoolEntry(2, 2, humanPolicy = 15.0, strongScore = 95.0),
            PoolEntry(3, 3, humanPolicy = 15.0, strongScore = 98.0),
            PoolEntry(4, 4, humanPolicy = 10.0, strongScore = 85.0),
            PoolEntry(5, 5, humanPolicy = 10.0, strongScore = 99.0),
            PoolEntry(6, 6, humanPolicy = 5.0, strongScore = 70.0),
            PoolEntry(7, 7, humanPolicy = 5.0, strongScore = 60.0),
        )
        val byS = wide.sortedByDescending { it.strongScore }
        val out = CandidateSelector.select(wide, byS, Strategy.GOOD_VS_TEMPTING, 5)
        assertEquals(5, out.size)
        assertEquals(2, out.count { it.tag == "good" })
        assertEquals(3, out.count { it.tag == "overconcentrated" })
        // Best two by loss inside the pool: (1,1) 0 and (5,5) 1.
        assertEquals(setOf(1 to 1, 5 to 5), out.filter { it.tag == "good" }.map { it.x to it.y }.toSet())
        // Worst three inside the pool: (2,2) 5, (0,0) 10, (4,4) 15.
        assertEquals(setOf(2 to 2, 0 to 0, 4 to 4), out.filter { it.tag == "overconcentrated" }.map { it.x to it.y }.toSet())
    }

    @Test fun `human only ignores score loss and colors positionally`() {
        val (byH, byS) = pool()
        val out = CandidateSelector.select(byH, byS, Strategy.HUMAN_ONLY, 3)
        assertEquals(listOf(0 to 0, 1 to 1, 2 to 2), out.map { it.x to it.y })
        assertEquals(listOf("good", "ok", "overconcentrated"), out.map { it.tag })
    }

    @Test fun `strong only takes the best scores regardless of human-ness`() {
        val (byH, byS) = pool()
        val out = CandidateSelector.select(byH, byS, Strategy.STRONG_ONLY, 2)
        assertEquals(listOf(1 to 1, 2 to 2), out.map { it.x to it.y })
    }

    @Test fun `tesuji leads with the objectively best move`() {
        val (byH, byS) = pool()
        val out = CandidateSelector.select(byH, byS, Strategy.TESUJI, 3)
        assertEquals(3, out.size)
        assertEquals(1 to 1, out[0].x to out[0].y) // loss 0
        assertEquals("good", out[0].tag)
        assertEquals(2, out.count { it.tag == "overconcentrated" })
    }

    @Test fun `blunder check scales to three slots`() {
        val (byH, byS) = pool()
        val out = CandidateSelector.select(byH, byS, Strategy.BLUNDER_CHECK, 3)
        assertEquals(3, out.size)
        assertEquals(2, out.count { it.tag == "good" })
        assertEquals(1, out.count { it.tag == "overconcentrated" })
    }

    @Test fun `ties break toward the more human move`() {
        val entries = listOf(
            PoolEntry(0, 0, humanPolicy = 50.0, strongScore = 5.0), // loss 0, more human
            PoolEntry(1, 1, humanPolicy = 30.0, strongScore = 5.0), // loss 0
            PoolEntry(2, 2, humanPolicy = 10.0, strongScore = 4.0),
            PoolEntry(3, 3, humanPolicy = 6.0, strongScore = 3.0),
            PoolEntry(4, 4, humanPolicy = 4.0, strongScore = 2.0),
        )
        val byS = entries.sortedByDescending { it.strongScore }
        val out = CandidateSelector.select(entries, byS, Strategy.STRONG_ONLY, 3)
        assertEquals(listOf(0 to 0, 1 to 1, 2 to 2), out.map { it.x to it.y })
    }

    @Test fun `dedups coordinates`() {
        val dup = listOf(PoolEntry(1, 1, 9.0, strongScore = 5.0), PoolEntry(1, 1, 8.0, strongScore = 5.0))
        val out = CandidateSelector.select(dup, dup, Strategy.HUMAN_ONLY, 2)
        assertEquals(1, out.size)
    }

    @Test fun `split 3 plus 2 takes three best and two worst`() {
        val wide = listOf(
            PoolEntry(0, 0, humanPolicy = 20.0, strongScore = 90.0),
            PoolEntry(1, 1, humanPolicy = 20.0, strongScore = 100.0),
            PoolEntry(2, 2, humanPolicy = 15.0, strongScore = 95.0),
            PoolEntry(3, 3, humanPolicy = 15.0, strongScore = 98.0),
            PoolEntry(4, 4, humanPolicy = 10.0, strongScore = 85.0),
            PoolEntry(5, 5, humanPolicy = 10.0, strongScore = 99.0),
            PoolEntry(6, 6, humanPolicy = 5.0, strongScore = 70.0),
            PoolEntry(7, 7, humanPolicy = 5.0, strongScore = 60.0),
        )
        val byS = wide.sortedByDescending { it.strongScore }
        val out = CandidateSelector.select(wide, byS, Strategy.SPLIT_3_2, 5)
        assertEquals(5, out.size)
        assertEquals(3, out.count { it.tag == "good" })
        assertEquals(2, out.count { it.tag == "overconcentrated" })
        assertEquals(
            setOf(1 to 1, 5 to 5, 3 to 3),
            out.filter { it.tag == "good" }.map { it.x to it.y }.toSet(),
        )
        assertEquals(
            setOf(4 to 4, 0 to 0),
            out.filter { it.tag == "overconcentrated" }.map { it.x to it.y }.toSet(),
        )
    }

    @Test fun `labels are A to E in output order`() {
        val (byH, byS) = pool()
        val out = CandidateSelector.select(byH, byS, Strategy.GOOD_VS_TEMPTING, 5)
        assertEquals(listOf("A", "B", "C", "D", "E"), out.map { it.label })
    }
}
