package com.gotrainer.nine

import com.gotrainer.nine.game.GameFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BUG (user report): nigiri->Black, first stone down fast: 1-2s of dead
 * board, then the thinking bar for ~0.5s, then White replies. Logcat showed
 * a 2133ms cold engine start still holding engineMutex via newGame, so the
 * move's engine sync queued behind it — with isThinking raised only inside
 * botReply, i.e. AFTER the wait. The bar must go up with the player's move,
 * before any engine sync, on every path a bot reply follows.
 */
class HumanMoveThinkingTest {
    @Test fun `placement is always followed by a bot reply`() {
        assertTrue(GameFlow.replyFollowsHumanMove(gameFinished = false))
    }

    @Test fun `first pass is answered by the bot`() {
        assertTrue(GameFlow.replyFollowsHumanMove(gameFinished = false))
    }

    @Test fun `second pass ends the game, no bot reply`() {
        assertFalse(GameFlow.replyFollowsHumanMove(gameFinished = true))
    }
}
