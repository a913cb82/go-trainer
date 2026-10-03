package com.gotrainer.nine.game

import kotlin.math.pow

/**
 * WHR-scale fixed bot anchors.
 *
 * Each rung is the FP2(3,3) fractional-polynomial fit to 2,120 bot-vs-bot
 * games (tournament branch, tag calibration-2026-10-03; rung-2 games
 * excluded, 18k takes the smoothed value), pinned at 5k = 1500.
 * Recalibration edits this table; stored histories resolve anchors at
 * recompute time, so old games follow automatically.
 */
object WhrAnchors {
    /** Elo-scale spread: P(win) = 1 / (1 + 10^(-gap / 400)). */
    const val ELO_SCALE = 400.0

    /** Absolute pin: rung 0 (20k, the HumanSL floor) sits at this WHR. */
    const val RUNG0_WHR = 1031.666105

    /** WHR rating per ladder rung, weakest first (frozen derivation, see above). */
    val table: List<Double> = listOf(
        1031.666105,
        1034.145917,
        1039.779011,
        1049.284143,
        1063.127853,
        1081.591029,
        1104.807239,
        1132.787811,
        1165.439563,
        1202.578012,
        1243.937616,
        1289.179934,
        1337.900289,
        1389.633322,
        1443.857674,
        1500.000000,
        1557.438449,
        1615.505700,
        1673.491646,
        1730.645776,
        1786.179301,
        1839.267078,
        1889.049340,
        1934.633271,
        1975.094448,
        2009.478156,
        2036.800606,
        2056.050047,
        2066.187811,
    )

    /** Fixed WHR rating of the bot at a ladder rung. */
    fun whrForRank(rank: Rank): Double = table[Rank.ALL.indexOf(rank)]

    /**
     * Fractional ladder rung (0..28) for a WHR rating; clamps outside the
     * table. Piecewise-linear between rungs, exact at every rung.
     */
    fun whrToRank(whr: Double): Double {
        val t = table
        if (whr <= t.first()) return 0.0
        if (whr >= t.last()) return 28.0
        var i = 0
        while (t[i + 1] < whr) i++
        return i + (whr - t[i]) / (t[i + 1] - t[i])
    }

    /** WHR expected score (win probability) for [whr] vs [oppWhr]. */
    fun expectedScore(whr: Double, oppWhr: Double): Double =
        1.0 / (1.0 + 10.0.pow(-(whr - oppWhr) / ELO_SCALE))

    /**
     * Player label with provisional marker, e.g. "8k", "2d?", "20k?".
     * Uncertainty always shown alongside — never a bare "?". WHR units match
     * Glicko units, so the 160 provisional threshold carries over unchanged.
     */
    fun whrPlayerLabel(whr: Double, unc: Double): String {
        val mark = if (unc >= BotRatings.PROVISIONAL_RD) "?" else ""
        return BotRatings.rankLabel(whrToRank(whr)) + mark
    }

    /** WHR rank deviation: how many ranks the uncertainty spans upward. */
    fun whrRankDeviation(whr: Double, unc: Double): Double =
        whrToRank(whr + unc) - whrToRank(whr)
}

