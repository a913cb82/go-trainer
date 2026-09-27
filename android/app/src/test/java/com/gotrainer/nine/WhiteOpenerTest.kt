package com.gotrainer.nine

import com.gotrainer.nine.game.GameFlow
import com.gotrainer.nine.game.GameFlow.OpeningAction.BOT_REPLY
import com.gotrainer.nine.game.GameFlow.OpeningAction.REQUEST_CANDIDATES
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * BUG: opening the app as White (play second) leaves a dead board — the bot
 * (Black, to move) never opens, you cannot place a stone, but pass works
 * (pass() kicks botReply directly).
 *
 * GameViewModel.init always requests candidates, and requestCandidates bails
 * when it is not the human's turn — so nothing ever asks the engine to move.
 * Only the New-game sheet path (newGame: human-White -> botReply) works.
 */
class WhiteOpenerTest {
    @Test fun `black opener requests candidates`() {
        assertEquals(REQUEST_CANDIDATES, GameFlow.openingAction(playerColor = 1))
    }

    @Test fun `white opener asks the bot to move first`() {
        assertEquals(BOT_REPLY, GameFlow.openingAction(playerColor = -1))
    }
}
