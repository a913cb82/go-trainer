package com.gotrainer.nine

import com.gotrainer.nine.game.Rank
import com.gotrainer.nine.game.gapColor
import com.gotrainer.nine.game.thresholds
import org.junit.Assert.assertEquals
import org.junit.Test

class RankTest {
    @Test fun `thresholds per rank mirror rank ts`() {
        assertEquals(1.5, Rank.R15K.thresholds().good, 1e-9)
        assertEquals(4.0, Rank.R15K.thresholds().bad, 1e-9)
        assertEquals(1.5, Rank.R10K.thresholds().good, 1e-9)
        assertEquals(3.2, Rank.R10K.thresholds().bad, 1e-9)
        assertEquals(1.0, Rank.R5K.thresholds().good, 1e-9)
        assertEquals(2.5, Rank.R5K.thresholds().bad, 1e-9)
        assertEquals(0.8, Rank.R3D.thresholds().good, 1e-9)
        assertEquals(1.8, Rank.R3D.thresholds().bad, 1e-9)
    }

    @Test fun `gap colors mirror gapColor`() {
        assertEquals("#27864a", gapColor(0.5, Rank.R10K).hex)
        assertEquals("#b7791f", gapColor(2.0, Rank.R10K).hex)
        assertEquals("#c0392b", gapColor(5.0, Rank.R10K).hex)
    }

    @Test fun `fromId fallback`() {
        assertEquals(Rank.R10K, Rank.fromId("bogus"))
        assertEquals(Rank.R3D, Rank.fromId("3d"))
    }

    @Test fun `full ladder`() {
        assertEquals(39, Rank.ALL.size)
        assertEquals("30k", Rank.ALL.first().id)
        assertEquals("9d", Rank.ALL.last().id)
        assertEquals(Rank.R30K, Rank.fromId("30k"))
        assertEquals(Rank.R9D, Rank.fromId("9d"))
        // Buckets extend across the ladder: weakest loosest, dans tightest.
        assertEquals(4.0, Rank.R30K.thresholds().bad, 1e-9)
        assertEquals(1.5, Rank.R20K.thresholds().good, 1e-9)
        assertEquals(3.2, Rank.R8K.thresholds().bad, 1e-9)
        assertEquals(1.0, Rank.R7K.thresholds().good, 1e-9)
        assertEquals(2.5, Rank.R3K.thresholds().bad, 1e-9)
        assertEquals(0.8, Rank.R2K.thresholds().good, 1e-9)
        assertEquals(0.8, Rank.R1D.thresholds().good, 1e-9)
        assertEquals(1.8, Rank.R9D.thresholds().bad, 1e-9)
    }
}
