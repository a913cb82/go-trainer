package com.gotrainer.nine.game

/**
 * Rank display math (scale-free: takes fractional ladder rungs, 0 = 30k ..
 * 38 = 9d). The OGS-derived Glicko table lived here until the WHR migration;
 * bot anchors now live in [WhrAnchors] and this file keeps only display.
 */
object BotRatings {
    /** OGS rule: RD at/above this shows the rank with "?". We still show rank ±. */
    const val PROVISIONAL_RD = 160.0

    /** Whole-rank label in app style, clamped to 30k–9d ("8k", "2d"). */
    fun rankLabel(rank: Double): String {
        val r = rank.coerceIn(0.0, 38.0)
        return if (r < 30) "${kotlin.math.ceil(30 - r).toInt()}k"
        else "${kotlin.math.floor(r - 29).toInt()}d"
    }
}
