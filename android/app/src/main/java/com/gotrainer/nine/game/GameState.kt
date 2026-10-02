package com.gotrainer.nine.game

/** One candidate choice offered to the player (mirrors server /candidates move). */
data class Candidate(
    val x: Int,
    val y: Int,
    val label: String,
    val humanPolicy: Double,
    val strongWinrate: Double,
    val strongScore: Double,
    val scoreGap: Double,
    val tag: String, // good | ok | overconcentrated
)

/** Candidate with evaluated gap vs best (mirrors gameStore evaluations). */
data class EvaluatedMove(
    val x: Int,
    val y: Int,
    val label: String,
    val humanPolicy: Double,
    val strongWinrate: Double,
    val strongScore: Double,
    val scoreGap: Double,
    val gap: Double,
    val tag: String,
)

data class MoveRec(
    val x: Int,
    val y: Int, // -1,-1 = pass
    val color: Int, // 1 black, -1 white
    val candidate: Candidate? = null,
    val gap: Double? = null,
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
    /** Multiple-choice mode: when false the player plays anywhere (free choice). */
    val multipleChoice: Boolean = true,
    /** Best-group slots (0-5) and worst-group slots (0-5) from the sheet sliders. */
    val bestCount: Int = 2,
    val worstCount: Int = 3,
    val candidates: List<Candidate>? = null,
    val evaluations: List<EvaluatedMove>? = null,
    val showFeedback: Boolean = true,
    val feedbackScopeAll: Boolean = true, // false = picked-only
    val winrateHistory: List<Double> = emptyList(),
    val status: String = "playing", // playing | finished
    val passing: Int = 0,
    val isThinking: Boolean = false,
    val reviewIdx: Int? = null,
    val error: String? = null,
    /** Winrate card expanded; persisted across app opens. */
    val graphOpen: Boolean = true,
    /** Candidates offered at each position (keyed by moves played before Black's pick). */
    val pastCandidates: Map<Int, List<Candidate>> = emptyMap(),
    /** Feedback recorded for each Black pick (same keying), so review shows real history. */
    val pastEvals: Map<Int, List<EvaluatedMove>> = emptyMap(),
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
    /** Live player rank, e.g. "30k? ±12". */
    val playerRankText: String = "30k? ±12",
    /** Live player WHR for the automatch preview; defaults to the 30k start. */
    val playerRating: Double = WhrAnchors.RUNG0_WHR,
    /** Pop overlay for the last play's captures; transient, never persisted. */
    val captureFx: CaptureFx? = null,
    /** Settle overlay for the last play's stone; transient, never persisted. */
    val placeFx: PlaceFx? = null,
    /**
     * The player opted into rating. Effective only when the game is actually
     * free play ([choiceCount] == 0); suggestions games are always unrated.
     */
    val ranked: Boolean = true,
) {
    /** Moves offered per turn: best + worst while multiple-choice is on, else 0. */
    val choiceCount: Int get() = if (!multipleChoice) 0 else bestCount + worstCount
}
