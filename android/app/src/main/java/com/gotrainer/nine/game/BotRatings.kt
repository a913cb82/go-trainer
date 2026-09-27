package com.gotrainer.nine.game

import kotlin.math.exp
import kotlin.math.ln

/**
 * OGS rank math (verified against online-go.com `rank_utils.ts`: A=525,
 * C=23.15), with our display range. The ladder rung IS the OGS rank:
 * Rank.ALL[0] (30k) = rank 0 … Rank.ALL[38] (9d) = rank 38.
 *
 * Differences from OGS, all deliberate:
 * - Display floor is 30k (OGS hides below 25k); the raw formula still runs
 *   underneath so improvement always moves the number.
 * - Labels follow our existing "8k"/"2d" style, whole ranks only.
 * - Bot ratings are fixed anchors from this table. Tournament recalibration
 *   later = editing [forRank], zero logic changes. Only the player's rating
 *   ever moves.
 */
object BotRatings {
    private const val A = 525.0
    private const val C = 23.15
    private const val MIN_RATING = 100.0
    private const val MAX_RATING = 6000.0

    /** New player: exactly 30k. */
    const val START_RATING = 525.0
    const val START_RD = 350.0
    const val START_VOL = 0.06

    /**
     * Fixed bot deviation. Never stored or updated; used only inside g(phi)
     * for expected-score/automatch math. Confident anchor, near-identity.
     */
    const val BOT_RD = 60.0

    /** OGS rule: RD at/above this shows the rank with "?". We still show rank ±. */
    const val PROVISIONAL_RD = 160.0

    fun rankToRating(rank: Double): Double = A * exp(rank / C)

    fun ratingToRank(rating: Double): Double =
        ln(rating.coerceIn(MIN_RATING, MAX_RATING) / A) * C

    /** OGS rank deviation: how many ranks the RD spans upward. */
    fun rankDeviation(rating: Double, rd: Double): Double =
        ratingToRank(rating + rd) - ratingToRank(rating)

    /** Fixed rating of the bot at a ladder rung (tournament-seeded, §file doc). */
    fun forRank(rank: Rank): Double = rankToRating(Rank.ALL.indexOf(rank).toDouble())

    /** Whole-rank label in app style, clamped to 30k–9d ("8k", "2d"). */
    fun rankLabel(rank: Double): String {
        val r = rank.coerceIn(0.0, 38.0)
        return if (r < 30) "${kotlin.math.ceil(30 - r).toInt()}k"
        else "${kotlin.math.floor(r - 29).toInt()}d"
    }

    /**
     * Player label with provisional marker, e.g. "8k", "2d?", "30k?".
     * Uncertainty always shown alongside — never a bare "?".
     */
    fun playerLabel(rating: Double, rd: Double): String {
        val rank = ratingToRank(rating)
        val mark = if (rd >= PROVISIONAL_RD) "?" else ""
        return rankLabel(rank) + mark
    }
}
