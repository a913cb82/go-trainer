package com.gotrainer.nine

import com.gotrainer.nine.game.GameFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BUG: as Black (play first), White's first reply is always slow. Nothing
 * preloads the engine while the human thinks: if you move before the
 * launch-time warmup finishes, botReply queues behind the engine start
 * on the single-flight engine mutex.
 *
 * The fix: when the human is to move and the engine is not warm yet, kick a
 * background warmup while waiting for the human's move.
 */
class FirstReplyWarmupTest {
    @Test fun `preload while the human thinks on a cold engine`() {
        assertTrue(GameFlow.preloadWanted(humanToMove = true, engineReady = false))
    }

    @Test fun `no preload once the engine is warm`() {
        assertFalse(GameFlow.preloadWanted(humanToMove = true, engineReady = true))
    }

    @Test fun `no preload while the engine is already working`() {
        assertFalse(GameFlow.preloadWanted(humanToMove = false, engineReady = false))
    }
}
