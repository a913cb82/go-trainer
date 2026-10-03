package com.gotrainer.nine.game

data class MoveRec(
    val x: Int,
    val y: Int, // -1,-1 = pass
    val color: Int, // 1 black, -1 white
)

/** One stone removed by the last play, for the pop overlay. */
data class CapturedStone(val x: Int, val y: Int, val color: Int)

/**
 * TRANSIENT pop-animation payload: stones the last real play captured.
 * Never persisted (the serde skips it); any next action clears it, so the
 * overlay can never queue up or hold up a turn.
 */
data class CaptureFx(val stones: List<CapturedStone>, val seq: Int)

/**
 * TRANSIENT place-animation payload: the stone the last real play put
 * down. Never persisted; cleared with the capture payload. The logical
 * board already holds the stone — BoardView skips it statically and draws
 * the settle on top instead.
 */
data class PlaceFx(val x: Int, val y: Int, val color: Int, val seq: Int)

data class GameState(
    val boardSignMap: List<List<Int>> = List(9) { List(9) { 0 } },
    val history: List<MoveRec> = emptyList(),
    val toMove: Int = 1,
    val rank: Rank = Rank.R10K,
    val winrateHistory: List<Double> = emptyList(),
    val status: String = "playing", // playing | finished
    val passing: Int = 0,
    val isThinking: Boolean = false,
    val reviewIdx: Int? = null,
    val error: String? = null,
    /** Winrate card expanded; persisted across app opens. */
    val graphOpen: Boolean = true,
    /** Who the human plays (resolved per game into playerColor). */
    val colorChoice: ColorChoice = ColorChoice.BLACK,
    /** On-device KataGo vs the home server (adb reverse / LAN). */
    /** Resolved side for the current game: 1 = you are Black, -1 = you are White. */
    val playerColor: Int = 1,
    /** Engine final score (BLACK-perspective lead) once the game ends; null until scored. */
    val finalScoreLead: Double? = null,
    /** True while the engine counts the final score (chip shows "scoring…"). */
    val scoring: Boolean = false,
    /** Engine ownership map for future heatmaps; null until scored or unavailable. */
    val finalOwnership: List<List<Double>>? = null,
    /** Any undo this game: poisons the rated-game upgrade (stays a loss). */
    val undoUsed: Boolean = false,
    /**
     * The last appended winrate point is a free-choice placeholder waiting
     * for the bot's root appraisal of the same board. Part of game state
     * (not a VM transient) so abandon accounting survives process death.
     */
    val pendingFreePly: Boolean = false,
    /** Fixed rung vs automatch-to-target-winrate. */
    val difficulty: Difficulty = Difficulty.FIXED,
    /** Automatch target winrate percent (10-90). */
    val targetWinrate: Int = 50,
    /** Live player rank, e.g. "20k? ±8". */
    val playerRankText: String = "20k? ±6",
    /** Live player WHR for the automatch preview; defaults to the 20k start. */
    val playerRating: Double = WhrAnchors.RUNG0_WHR,
    /** Pop overlay for the last play's captures; transient, never persisted. */
    val captureFx: CaptureFx? = null,
    /** Settle overlay for the last play's stone; transient, never persisted. */
    val placeFx: PlaceFx? = null,
    /** The player opted into rating; every game is free play, so it counts. */
    val ranked: Boolean = true,
)
