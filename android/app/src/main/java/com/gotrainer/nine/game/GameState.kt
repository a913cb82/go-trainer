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

data class GameState(
    val boardSignMap: List<List<Int>> = List(9) { List(9) { 0 } },
    val history: List<MoveRec> = emptyList(),
    val toMove: Int = 1,
    val rank: Rank = Rank.R10K,
    val n: Int = 5,
    val strategy: Strategy = Strategy.GOOD_VS_TEMPTING,
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
    /** Candidates offered at each position (keyed by moves played before Black's pick). */
    val pastCandidates: Map<Int, List<Candidate>> = emptyMap(),
    /** Feedback recorded for each Black pick (same keying), so review shows real history. */
    val pastEvals: Map<Int, List<EvaluatedMove>> = emptyMap(),
    /** Who the human plays (resolved per game into playerColor). */
    val colorChoice: ColorChoice = ColorChoice.BLACK,
    /** On-device KataGo vs the home server (adb reverse / LAN). */
    val engineMode: EngineMode = EngineMode.REMOTE,
    /** Remote engine base URL (e.g. http://127.0.0.1:3001 via adb reverse). */
    val serverUrl: String = "http://127.0.0.1:3001",
    /** Resolved side for the current game: 1 = you are Black, -1 = you are White. */
    val playerColor: Int = 1,
    /** Engine final score (BLACK-perspective lead) once the game ends; null until scored. */
    val finalScoreLead: Double? = null,
    /** True while the engine counts the final score (chip shows "scoring…"). */
    val scoring: Boolean = false,
    /** Engine ownership map for future heatmaps; null until scored or unavailable. */
    val finalOwnership: List<List<Double>>? = null,
)
