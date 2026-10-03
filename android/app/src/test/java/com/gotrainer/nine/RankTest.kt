package com.gotrainer.nine

import com.gotrainer.nine.game.Rank
import org.junit.Assert.assertEquals
import org.junit.Test

class RankTest {
    @Test fun `fromId fallback`() {
        assertEquals(Rank.R10K, Rank.fromId("bogus"))
        assertEquals(Rank.R3D, Rank.fromId("3d"))
    }

    @Test fun `retired sub-20k ids read as the 20k bot actually faced`() {
        // The 30k–21k bots never existed (one rank_20k config); old records
        // keep their raw ids in storage, but every lookup resolves upward.
        assertEquals(Rank.R20K, Rank.fromId("30k"))
        assertEquals(Rank.R20K, Rank.fromId("25k"))
        assertEquals(Rank.R20K, Rank.fromId("21k"))
        assertEquals(Rank.R20K, Rank.fromId("20k"))
        assertEquals(Rank.R19K, Rank.fromId("19k"))
    }

    @Test fun `full ladder`() {
        assertEquals(29, Rank.ALL.size)
        assertEquals("20k", Rank.ALL.first().id)
        assertEquals("9d", Rank.ALL.last().id)
        assertEquals(Rank.R9D, Rank.fromId("9d"))
    }
}
