package com.gotrainer.nine.game

import androidx.compose.ui.graphics.Color

/**
 * Ladder weakest to strongest: 20k … 1k, 1d … 9d. KataGo HumanSL profiles
 * exist for every rung (rank_20k is its floor); the old 30k–21k ids never
 * had bots behind them and resolve to [R20K] in [fromId].
 */
enum class Rank(val id: String) {
    R20K("20k"), R19K("19k"), R18K("18k"), R17K("17k"), R16K("16k"),
    R15K("15k"), R14K("14k"), R13K("13k"), R12K("12k"), R11K("11k"),
    R10K("10k"), R9K("9k"), R8K("8k"), R7K("7k"), R6K("6k"),
    R5K("5k"), R4K("4k"), R3K("3k"), R2K("2k"), R1K("1k"),
    R1D("1d"), R2D("2d"), R3D("3d"), R4D("4d"), R5D("5d"),
    R6D("6d"), R7D("7d"), R8D("8d"), R9D("9d");

    companion object {
        val ALL = entries.toList()
        fun fromId(id: String): Rank {
            entries.firstOrNull { it.id == id }?.let { return it }
            // Retired sub-20k ids: those games were played against the
            // rank_20k config (the only one below the HumanSL floor), so
            // old records resolve upward instead of falling back to 10k.
            val num = id.dropLast(1).toIntOrNull()
            if (id.endsWith("k") && num != null && num > 20) return R20K
            return R10K
        }
    }
}

enum class Strategy(val id: String) {
    GOOD_VS_TEMPTING("good-vs-tempting"),
    HUMAN_ONLY("human-only"),
    TESUJI("tesuji"),
    BLUNDER_CHECK("blunder-check"),
    STRONG_ONLY("strong-only"),
    SPLIT_3_2("split-3-2");

    companion object {
        val ALL = entries.toList()
        fun fromId(id: String): Strategy = entries.firstOrNull { it.id == id } ?: GOOD_VS_TEMPTING
    }

    /**
     * Count-based display name, e.g. "2 Good, 3 Bad" / "5 Human-like". Side is
     * decided by the group the move was picked from, not by point cutoffs.
     */
    fun label(n: Int): String = when (this) {
        TESUJI -> "1 Good, ${n - 1} Bad"
        GOOD_VS_TEMPTING -> "2 Good, ${n - 2} Bad"
        SPLIT_3_2 -> "3 Good, ${n - 3} Bad"
        BLUNDER_CHECK -> "${n - 1} Good, 1 Bad"
        HUMAN_ONLY -> "$n Human-like"
        STRONG_ONLY -> "$n Strongest"
    }

    fun blurb(): String = when (this) {
        TESUJI -> "Best move hidden among bad ones"
        GOOD_VS_TEMPTING -> "Mix of best moves and tempting mistakes"
        SPLIT_3_2 -> "Three good moves hide two mistakes"
        BLUNDER_CHECK -> "Spot the one blunder"
        HUMAN_ONLY -> "Most likely human moves at your rank"
        STRONG_ONLY -> "Strongest moves only"
    }
}

/**
 * Which training styles the sheet offers for n choices. Display order is
 * fixed (hunt, splits, blunder, flat). A split family appears only when both
 * groups are non-empty AND its (best, worst) shape isn't already offered:
 * n=5 gives all four splits, n=3 gives (1,2) and (2,1) only.
 */
fun availableStrategies(n: Int): List<Strategy> {
    if (n <= 0) return Strategy.ALL
    val order = listOf(
        Strategy.TESUJI,
        Strategy.GOOD_VS_TEMPTING,
        Strategy.SPLIT_3_2,
        Strategy.BLUNDER_CHECK,
        Strategy.HUMAN_ONLY,
        Strategy.STRONG_ONLY,
    )
    val seen = mutableSetOf<Pair<Int, Int>>()
    return order.filter { st ->
        if (st == Strategy.HUMAN_ONLY || st == Strategy.STRONG_ONLY) return@filter true
        val shape = CandidateSelector.slots(st, n)
        if (shape.second < 1) return@filter false
        seen.add(shape)
    }
}

data class Thresholds(val good: Double, val bad: Double)

/**
 * Points thresholds, rank-graduated (mirrors app/src/lib/rank.ts buckets, extended
 * across the full ladder): weaker ranks tolerate bigger mistakes.
 */
fun Rank.thresholds(): Thresholds {
    val num = id.dropLast(1).toIntOrNull() ?: 10
    val isDan = id.endsWith("d")
    return when {
        isDan || num <= 2 -> Thresholds(0.8, 1.8)   // 2k and all dan: tightest
        num <= 7 -> Thresholds(1.0, 2.5)            // 7k–3k
        num <= 11 -> Thresholds(1.5, 3.2)           // 11k–8k
        else -> Thresholds(1.5, 4.0)                // 20k–12k: loosest
    }
}

data class GapStyle(val hex: String, val bg: String) {
    val color: Color get() = Color(android.graphics.Color.parseColor(hex))
    val bgColor: Color get() = Color(android.graphics.Color.parseColor(bg))
}

/** Mirrors gapColor() from rank.ts. */
fun gapColor(gap: Double, rank: Rank): GapStyle {
    val (good, bad) = rank.thresholds()
    return when {
        gap <= good -> GapStyle("#27864a", "#c8f0c8")
        gap <= bad -> GapStyle("#b7791f", "#fff6b0")
        else -> GapStyle("#c0392b", "#ffcccc")
    }
}
