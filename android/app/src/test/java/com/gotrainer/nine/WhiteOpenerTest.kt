package com.gotrainer.nine

import com.gotrainer.nine.game.GameFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Opening the app: the bot moves first only when it holds Black (the human
 * plays White). A human-Black game waits for a tap — no query is issued.
 */
class WhiteOpenerTest {
    @Test fun `black opener waits for the human tap`() {
        assertFalse(GameFlow.botOpens(playerColor = 1))
    }

    @Test fun `white opener asks the bot to move first`() {
        assertTrue(GameFlow.botOpens(playerColor = -1))
    }
}
