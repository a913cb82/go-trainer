package com.gotrainer.nine.game

/**
 * Rank display math (scale-free: takes fractional ladder rungs, 0 = 20k ..
 * 28 = 9d). The OGS-derived Glicko table lived here until the WHR migration;
 * bot anchors now live in [WhrAnchors] and this file keeps only display.
 */
object BotRatings {
    /** OGS rule: RD at/above this shows the rank with "?". We still show rank ±. */
    const val PROVISIONAL_RD = 160.0

    /** Whole-rank label in app style, clamped to 20k–9d ("8k", "2d"). */
    fun rankLabel(rank: Double): String {
        val r = rank.coerceIn(0.0, 28.0)
        return if (r < 20) "${kotlin.math.ceil(20 - r).toInt()}k"
        else "${kotlin.math.floor(r - 19).toInt()}d"
    }
}
