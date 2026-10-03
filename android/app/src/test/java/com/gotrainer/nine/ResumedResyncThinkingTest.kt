package com.gotrainer.nine

import com.gotrainer.nine.game.GameFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BUG (user report): open the app on the bot's turn and the thinking bar
 * stays dark for 2-3s, then shows for ~1s before the bot moves.
 *
 * Cause: the resumed-game path replays the engine board (process spawn +
 * model load + N play syncs) with isThinking=false; only the follow-up
 * botReply query raises the flag. The resync must show thinking exactly
 * when the bot is about to move; a human turn never issues a query.
 */
class ResumedResyncThinkingTest {
    @Test fun `bot to move shows thinking during resync`() {
        assertTrue(GameFlow.resyncThinkingWanted(toMove = 1, playerColor = -1))
    }

    @Test fun `human to move stays quiet (no query follows)`() {
        assertFalse(GameFlow.resyncThinkingWanted(toMove = 1, playerColor = 1))
    }
}
