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
 * botReply/candidates query raises the flag. The resync must show thinking
 * exactly when a follow-up engine query will be issued.
 */
class ResumedResyncThinkingTest {
    @Test fun `bot to move shows thinking during resync`() {
        assertTrue(GameFlow.resyncThinkingWanted(toMove = 1, playerColor = -1, choiceCount = 5, candidatesPresent = false, reviewing = false))
    }

    @Test fun `human to move with unseen candidates shows thinking`() {
        assertTrue(GameFlow.resyncThinkingWanted(toMove = 1, playerColor = 1, choiceCount = 5, candidatesPresent = false, reviewing = false))
    }

    @Test fun `human to move with candidates already seen stays quiet`() {
        assertFalse(GameFlow.resyncThinkingWanted(toMove = 1, playerColor = 1, choiceCount = 5, candidatesPresent = true, reviewing = false))
    }

    @Test fun `free choice human to move stays quiet (no query follows)`() {
        assertFalse(GameFlow.resyncThinkingWanted(toMove = 1, playerColor = 1, choiceCount = 0, candidatesPresent = false, reviewing = false))
    }

    @Test fun `reviewing a human turn stays quiet (query would early-return)`() {
        assertFalse(GameFlow.resyncThinkingWanted(toMove = 1, playerColor = 1, choiceCount = 5, candidatesPresent = false, reviewing = true))
    }
}
