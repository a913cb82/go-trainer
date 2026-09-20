package com.gotrainer.nine.game

import androidx.compose.ui.graphics.Color

/**
 * Full ladder weakest to strongest: 30k … 1k, 1d … 9d.
 * KataGo HumanSL accepts `rank_<id>` profiles across this range.
 */
enum class Rank(val id: String) {
    R30K("30k"), R29K("29k"), R28K("28k"), R27K("27k"), R26K("26k"),
    R25K("25k"), R24K("24k"), R23K("23k"), R22K("22k"), R21K("21k"),
    R20K("20k"), R19K("19k"), R18K("18k"), R17K("17k"), R16K("16k"),
    R15K("15k"), R14K("14k"), R13K("13k"), R12K("12k"), R11K("11k"),
    R10K("10k"), R9K("9k"), R8K("8k"), R7K("7k"), R6K("6k"),
    R5K("5k"), R4K("4k"), R3K("3k"), R2K("2k"), R1K("1k"),
    R1D("1d"), R2D("2d"), R3D("3d"), R4D("4d"), R5D("5d"),
    R6D("6d"), R7D("7d"), R8D("8d"), R9D("9d");

    companion object {
        val ALL = entries.toList()
        fun fromId(id: String): Rank = entries.firstOrNull { it.id == id } ?: R10K
    }
}

enum class Strategy(val id: String) {
    GOOD_VS_TEMPTING("good-vs-tempting"),
    HUMAN_ONLY("human-only"),
    TESUJI("tesuji"),
    BLUNDER_CHECK("blunder-check"),
    STRONG_ONLY("strong-only");

    companion object {
        val ALL = entries.toList()
        fun fromId(id: String): Strategy = entries.firstOrNull { it.id == id } ?: GOOD_VS_TEMPTING
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
        else -> Thresholds(1.5, 4.0)                // 30k–12k: loosest
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
