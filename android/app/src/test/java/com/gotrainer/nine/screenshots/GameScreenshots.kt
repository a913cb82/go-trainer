package com.gotrainer.nine.screenshots

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import com.gotrainer.nine.game.Candidate
import com.gotrainer.nine.game.ColorChoice
import com.gotrainer.nine.game.EvaluatedMove
import com.gotrainer.nine.ui.GameActions
import com.gotrainer.nine.game.GameState
import com.gotrainer.nine.game.MoveRec
import com.gotrainer.nine.game.Rank
import com.gotrainer.nine.game.Strategy
import com.gotrainer.nine.ui.GameScreenContent
import com.gotrainer.nine.ui.OpponentSheetContent
import com.gotrainer.nine.ui.goTrainerTheme
import org.junit.Rule
import org.junit.Test

private val DemoCandidates = listOf(
    Candidate(4, 4, "A", 0.31, 0.55, 2.5, 0.0, "good"),
    Candidate(2, 2, "B", 0.22, 0.53, 2.1, 0.4, "good"),
    Candidate(6, 6, "C", 0.18, 0.50, 1.4, 1.1, "ok"),
    Candidate(2, 6, "D", 0.11, 0.44, -0.6, 3.1, "ok"),
    Candidate(6, 2, "E", 0.07, 0.38, -2.7, 5.2, "overconcentrated"),
)

private fun boardWith(vararg stones: Triple<Int, Int, Int>): List<List<Int>> {
    val b = List(9) { MutableList(9) { 0 } }
    for ((x, y, v) in stones) b[y][x] = v
    return b
}

private val MidBoard = boardWith(
    Triple(4, 4, 1),
    Triple(2, 2, -1),
    Triple(6, 6, 1),
    Triple(5, 5, -1),
    Triple(3, 5, 1),
    Triple(4, 2, -1),
)

private val MidHistory = listOf(
    MoveRec(4, 4, 1),
    MoveRec(2, 2, -1),
    MoveRec(6, 6, 1),
    MoveRec(5, 5, -1),
    MoveRec(3, 5, 1),
    MoveRec(4, 2, -1),
)

/** Feedback for the mid-game position: picked D4 (gap 0, green halo) + 3 live alternatives. */
private val MidEvals = listOf(
    EvaluatedMove(3, 5, "A", 0.31, 0.58, 3.2, 0.0, 0.0, "good"),
    EvaluatedMove(2, 4, "B", 0.22, 0.55, 2.3, 0.9, 0.9, "good"),
    EvaluatedMove(5, 4, "C", 0.18, 0.52, 1.2, 2.0, 2.0, "ok"),
    EvaluatedMove(6, 3, "D", 0.11, 0.44, -0.9, 4.1, 4.1, "overconcentrated"),
)

private val WinDemo = listOf(0.5, 0.53, 0.51, 0.55, 0.52, 0.57, 0.55)

/** Next-turn suggestions (n=5, good-vs-tempting) on points empty in MidBoard. */
private val NextCandidates = listOf(
    Candidate(2, 5, "A", 0.28, 0.56, 2.8, 0.0, "good"),
    Candidate(5, 2, "B", 0.19, 0.52, 1.9, 0.9, "good"),
    Candidate(3, 3, "C", 0.14, 0.50, 1.2, 1.6, "ok"),
    Candidate(6, 4, "D", 0.09, 0.45, -0.3, 3.1, "ok"),
    Candidate(1, 3, "E", 0.05, 0.39, -1.7, 4.5, "overconcentrated"),
)

private fun midGame() = GameState(
    boardSignMap = MidBoard,
    history = MidHistory,
    toMove = 1,
    rank = Rank.R10K,
    evaluations = MidEvals,
    winrateHistory = WinDemo,
)

class GameScreenshots {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6,
        renderingMode = SessionParams.RenderingMode.SHRINK,
    )

    private fun snap(name: String, content: @Composable () -> Unit) {
        paparazzi.snapshot(name = name) {
            goTrainerTheme { content() }
        }
    }

    private fun game(name: String, s: GameState) =
        snap(name) { GameScreenContent(s = s, actions = GameActions()) }

    @Test
    fun s01_new_game() {
        game("01_new_game", GameState(candidates = DemoCandidates))
    }

    @Test
    fun s02_opponent_sheet() {
        snap("02_opponent_sheet") {
            Surface {
                OpponentSheetContent(s = GameState(), draftRank = Rank.R10K, onDraftRank = {}, draftN = 5, onDraftN = {}, draftColor = ColorChoice.BLACK, onDraftColor = {}, draftStrategy = Strategy.GOOD_VS_TEMPTING, onDraftStrategy = {}, draftFeedback = true, onDraftFeedback = {}, onStart = {})
            }
        }
    }

    @Test
    fun s03_thinking() {
        game("03_thinking", GameState(isThinking = true))
    }

    @Test
    fun s04_feedback() {
        game("04_feedback", midGame().copy(candidates = NextCandidates))
    }

    @Test
    fun s05_mine_only() {
        game("05_mine_only", midGame().copy(candidates = NextCandidates, feedbackScopeAll = false))
    }

    @Test
    fun s06_free_choice() {
        game(
            "06_free_choice",
            GameState(
                boardSignMap = MidBoard,
                history = MidHistory,
                toMove = 1,
                n = 0,
                winrateHistory = WinDemo,
            ),
        )
    }

    @Test
    fun s07_review() {
        game(
            "07_review",
            GameState(
                boardSignMap = MidBoard,
                history = MidHistory,
                reviewIdx = 3,
                winrateHistory = WinDemo,
                pastCandidates = mapOf(3 to NextCandidates),
                pastEvals = mapOf(2 to MidEvals),
            ),
        )
    }

    @Test
    fun s09_white() {
        // Human plays White: bot (Black) opened E5, White to play with choices.
        game(
            "09_white",
            GameState(
                boardSignMap = boardWith(Triple(4, 4, 1)),
                history = listOf(MoveRec(4, 4, 1)),
                toMove = -1,
                playerColor = -1,
                colorChoice = ColorChoice.WHITE,
                candidates = NextCandidates,
                winrateHistory = listOf(0.45),
            ),
        )
    }

    @Test
    fun s08_finished() {
        // Engine-scored (decimals) rather than the local estimate.
        game(
            "08_finished",
            midGame().copy(status = "finished", winrateHistory = WinDemo + 0.6, finalScoreLead = -4.5),
        )
    }

}

class SetupScreenshots {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6,
        renderingMode = SessionParams.RenderingMode.SHRINK,
    )

    private fun snap(name: String, content: @Composable () -> Unit) {
        paparazzi.snapshot(name = name) {
            goTrainerTheme { content() }
        }
    }

    @Test
    fun s12_setup() {
        snap("12_setup") {
            com.gotrainer.nine.ui.SetupScreen(
                ui = com.gotrainer.nine.setup.SetupViewModel.Ui.Missing(
                    listOf(
                        com.gotrainer.nine.engine.ModelManager.FileRow(com.gotrainer.nine.engine.ModelManager.HUMAN, false, 0L),
                    ),
                ),
                onDownload = {},
                onRetry = {},
                onRecheck = {},
            )
        }
    }

    @Test
    fun s13_downloading() {
        snap("13_downloading") {
            com.gotrainer.nine.ui.SetupScreen(
                ui = com.gotrainer.nine.setup.SetupViewModel.Ui.Downloading(
                    fileIndex = 1,
                    fileCount = 2,
                    fileName = "Strong network",
                    doneBytes = 142_606_336L,
                    totalBytes = 271_440_852L,
                    overallDone = 232_606_336L,
                    overallTotal = 366_440_852L,
                ),
                onDownload = {},
                onRetry = {},
                onRecheck = {},
            )
        }
    }
}

class GameScreenshotsDark {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = com.android.resources.NightMode.NIGHT),
        renderingMode = SessionParams.RenderingMode.SHRINK,
    )

    private fun snap(name: String, content: @Composable () -> Unit) {
        paparazzi.snapshot(name = name) {
            goTrainerTheme { content() }
        }
    }

    @Test
    fun s10_dark_game() {
        snap("10_dark_game") {
            GameScreenContent(s = GameState(candidates = DemoCandidates), actions = GameActions())
        }
    }

    @Test
    fun s11_dark_sheet() {
        snap("11_dark_sheet") {
            Surface {
                OpponentSheetContent(
                    s = GameState(rank = Rank.R3D, n = 0, strategy = Strategy.STRONG_ONLY, showFeedback = false),
                    draftRank = Rank.R3D, onDraftRank = {},
                    draftN = 0, onDraftN = {},
                    draftColor = ColorChoice.BLACK, onDraftColor = {},

                    draftStrategy = Strategy.STRONG_ONLY, onDraftStrategy = {},
                    draftFeedback = false, onDraftFeedback = {},
                    onStart = {},
                )
            }
        }
    }
}
