package com.gotrainer.nine.game

import kotlin.math.pow

/**
 * WHR-scale fixed bot anchors.
 *
 * Each rung was derived by probability-matching (adjacent OGS expected
 * scores inverted through the Elo logistic, rung 0 (20k) pinned) and
 * then frozen here so the Glicko derivation could be deleted. Recalibration
 * (e.g. from bot-vs-bot tournaments) edits this table; stored histories
 * resolve anchors at recompute time, so old games follow automatically.
 */
object WhrAnchors {
    /** Elo-scale spread: P(win) = 1 / (1 + 10^(-gap / 400)). */
    const val ELO_SCALE = 400.0

    /** Absolute pin: rung 0 (20k, the HumanSL floor) sits at this WHR. */
    const val RUNG0_WHR = 803.641111022468

    /** WHR rating per ladder rung, weakest first (frozen derivation, see above). */
    val table: List<Double> = listOf(
        803.641111022468,
        838.7072357832695,
        875.321287821848,
        913.5515974391317,
        953.4695112470157,
        995.1495253176653,
        1038.6694242104384,
        1084.1104261358887,
        1131.5573345277571,
        1181.0986963058199,
        1232.8269671249463,
        1286.8386839187663,
        1343.2346450599453,
        1402.1200984732918,
        1463.604938052762,
        1527.8039087489153,
        1594.83682070957,
        1664.828772873284,
        1737.9103864329497,
        1814.2180486051907,
        1893.8941671604941,
        1977.087436189095,
        2063.953113598583,
        2154.653310861118,
        2249.3572955509812,
        2348.241807237072,
        2451.4913873198752,
        2559.298723428455,
        2671.8650090201986,
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

