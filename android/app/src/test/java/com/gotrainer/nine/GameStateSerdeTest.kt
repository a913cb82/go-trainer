package com.gotrainer.nine

import com.gotrainer.nine.game.ColorChoice
import com.gotrainer.nine.game.Difficulty
import com.gotrainer.nine.game.GameState
import com.gotrainer.nine.game.GameStateSerde
import com.gotrainer.nine.game.GoBoard
import com.gotrainer.nine.game.MoveRec
import com.gotrainer.nine.game.Rank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameStateSerdeTest {
    private fun midGame() = GameState(
        history = listOf(
            MoveRec(4, 4, 1),
            MoveRec(3, 4, -1),
            MoveRec(-1, -1, 1),
            MoveRec(2, 3, -1),
        ),
        toMove = 1,
        rank = Rank.R8K,
        winrateHistory = listOf(0.5, 0.55, 0.55, 0.4),
        passing = 1,
        reviewIdx = 2,
        colorChoice = ColorChoice.RANDOM,
        playerColor = -1,
        undoUsed = true,
        pendingFreePly = true,
        difficulty = Difficulty.AUTOMATCH,
        targetWinrate = 70,
        playerRankText = "12k ±3",
        playerRating = 1358.0,
    )

    @Test fun `mid-game round trip restores everything but the derived board`() {
        val want = midGame()
        val got = GameStateSerde.decode(GameStateSerde.encode(want))
        // The board rebuilds from history on load; sync it like the VM does.
        val synced = got!!.copy(boardSignMap = GoBoard.fromHistory(got.history).signMap())
        assertEquals(want.copy(boardSignMap = GoBoard.fromHistory(want.history).signMap()), synced)
    }

    @Test fun `finished scored game round trips lead and ownership`() {
        val own = List(9) { r -> List(9) { c -> if ((r + c) % 2 == 0) 0.9 else 0.1 } }
        val want = GameState(
            history = listOf(MoveRec(4, 4, 1), MoveRec(-1, -1, -1), MoveRec(-1, -1, 1)),
            status = "finished",
            passing = 2,
            finalScoreLead = 3.5,
            finalOwnership = own,
        )
        val got = GameStateSerde.decode(GameStateSerde.encode(want))!!
        assertEquals(3.5, got.finalScoreLead!!, 1e-9)
        assertEquals(own, got.finalOwnership)
        assertEquals("finished", got.status)
    }

    @Test fun `fresh state round trips`() {
        assertEquals(GameState(), GameStateSerde.decode(GameStateSerde.encode(GameState())))
    }

    @Test fun `corrupt blobs decode to null`() {
        assertNull(GameStateSerde.decode("garbage"))
        assertNull(GameStateSerde.decode("v0\n1,1,playing"))
        assertNull(GameStateSerde.decode(GameStateSerde.encode(midGame()).split("\n").take(3).joinToString("\n")))
        assertNull(GameStateSerde.decode(GameStateSerde.encode(midGame()).replace("0.55", "xyz")))
    }

    @Test fun `v1 multiple-choice blobs no longer decode (fresh game)`() {
        val v1 = listOf(
            "v1",
            "1,-1,playing,1,8k,1,2,3,random,1,0,2,1,automatch,70,1,null,1,1358.0,12k ±3,1",
            "4,4,1;3,4,-1;-1,-1,1;2,3,-1",
            "NULL",
            "NULL",
            "0.5,0.55,0.55,0.4",
            "",
            "",
            "NULL",
        ).joinToString("\n")
        assertNull(GameStateSerde.decode(v1))
    }
}
