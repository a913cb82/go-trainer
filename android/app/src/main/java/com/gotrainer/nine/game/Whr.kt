package com.gotrainer.nine.game

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Whole-History Rating core (Coulom 2008) — Phase 2 of the Glicko-2 -> WHR
 * migration. Pure JVM, zero Android imports.
 *
 * Restricted to our shape: ONE free player (the human) against FIXED bot
 * anchors, so there is no inter-player coupling. The solve is a single 1-D
 * Newton chain over the player's distinct game-days with a tridiagonal
 * Hessian (Thomas algorithm, O(days) per pass) with a backtracking line
 * search. Live: player rank text, stats, and automatch all read this.
 *
 * Conventions (per the migration plan): Elo scale, w2 = 60 elo^2/day
 * (Coulom's Go-data value), game times in whole days from real timestamps,
 * new-player prior of one virtual win plus one virtual loss against
 * [WhrAnchors.RUNG0_WHR] on the first day, zero color advantage.
 */
object Whr {
    /** Wiener drift variance, elo^2/day. The "humans improve this fast" knob. */
    const val W2 = 60.0

    /** Newton iteration cap (Coulom's value; typical convergence is <10). */
    const val MAX_ITERATIONS = 200

    /** r-units (log-gamma) to Elo. Numerically identical to Glicko's SCALE: same scale. */
    const val R_TO_ELO = 400.0 / 2.302585092994046

    /** One game: whole-day timestamp, fixed opponent rating, score from our seat. */
    data class Game(val day: Int, val oppWhr: Double, val score: Double)

    /** Posterior estimate: rating plus one standard deviation, both WHR units. */
    data class Rating(val whr: Double, val unc: Double)

    /** Current estimate: posterior at the latest game-day (prior when empty). */
    fun rate(games: List<Game>, priorWhr: Double = WhrAnchors.RUNG0_WHR): Rating {
        val fit = solve(games, priorWhr)
        val last = fit.days.size - 1
        return Rating(fit.elo[last], fit.unc[last])
    }

    /**
     * Smoothed skill curve: one estimate per game from a SINGLE full-history
     * refit (the WHR-native presentation, cf. goratings curves). Unlike the
     * old causal fold, each point uses all games — a streak retrospectively
     * reshapes the curve instead of sawtoothing it.
     */
    fun trajectory(games: List<Game>, priorWhr: Double = WhrAnchors.RUNG0_WHR): List<Rating> {
        if (games.isEmpty()) return emptyList()
        val fit = solve(games, priorWhr)
        return games.map { g ->
            val i = fit.days.binarySearch(g.day).let { if (it < 0) -(it + 1) else it }
            Rating(fit.elo[i], fit.unc[i])
        }
    }

    private data class Fit(val days: List<Int>, val elo: List<Double>, val unc: List<Double>)

    private fun solve(games: List<Game>, priorWhr: Double): Fit {
        val byDay = LinkedHashMap<Int, MutableList<Game>>()
        for (g in games) byDay.getOrPut(g.day) { ArrayList() }.add(g)
        val days = (if (byDay.isEmpty()) listOf(0) else byDay.keys.sorted())
        val n = days.size
        // Day 0 carries the prior: a virtual win and loss vs the prior
        // center, i.e. a 2-game-weight anchor that contributes zero gradient
        // exactly at the center.
        val dayGames = days.mapIndexed { i, d ->
            val list = (byDay[d] ?: emptyList()).toMutableList()
            if (i == 0) {
                list.add(Game(d, priorWhr, 1.0))
                list.add(Game(d, priorWhr, 0.0))
            }
            list
        }
        val priorR = priorWhr / R_TO_ELO
        val oppR = dayGames.map { gl -> gl.map { it.oppWhr / R_TO_ELO } }
        val scores = dayGames.map { gl -> gl.map { it.score } }
        val w2r = W2 / (R_TO_ELO * R_TO_ELO)
        val gaps = DoubleArray(n)
        for (i in 1 until n) gaps[i] = (days[i] - days[i - 1]) * w2r

        val r = DoubleArray(n) { priorR }
        val grad = DoubleArray(n)
        val diag = DoubleArray(n)
        val off = DoubleArray(n) // off[i] = H[i][i+1], i < n-1
        var it = 0
        while (it < MAX_ITERATIONS) {
            grad.fill(0.0); diag.fill(0.0); off.fill(0.0)
            for (i in 0 until n) {
                for (k in oppR[i].indices) {
                    val p = 1.0 / (1.0 + exp(-(r[i] - oppR[i][k])))
                    grad[i] -= (scores[i][k] - p)
                    diag[i] += p * (1.0 - p)
                }
                if (i > 0) {
                    val drift = (r[i] - r[i - 1]) / gaps[i]
                    grad[i] += drift
                    grad[i - 1] -= drift
                    diag[i] += 1.0 / gaps[i]
                    diag[i - 1] += 1.0 / gaps[i]
                    off[i - 1] = -1.0 / gaps[i]
                }
            }
            val full = thomasSolve(diag, off, grad, negateRhs = true)
            var fmx = 0.0
            for (v in full) fmx = maxOf(fmx, abs(v))
            if (fmx < 1e-9) break // at the optimum; applying changes nothing
            // Backtracking line search: cold-start Newton overshoots on long
            // streaks (likelihood terms saturate, the Hessian goes singular
            // and the raw step diverges). Halve until the objective improves;
            // the posterior is log-concave, so this always converges.
            val base = negLogLikelihood(r, oppR, scores, gaps)
            var alpha = 1.0
            while (alpha > 1e-12) {
                val cand = DoubleArray(n) { i -> r[i] + alpha * full[i] }
                if (negLogLikelihood(cand, oppR, scores, gaps) < base) break
                alpha /= 2.0
            }
            if (alpha <= 1e-12) break // no descent possible: at the optimum
            var mx = 0.0
            for (i in 0 until n) {
                r[i] += alpha * full[i]
                mx = maxOf(mx, abs(alpha * full[i]))
            }
            it++
            if (mx < 1e-9) break
        }
        // Rebuild the Hessian at the MAP for the uncertainty diagonal.
        grad.fill(0.0); diag.fill(0.0); off.fill(0.0)
        for (i in 0 until n) {
            for (k in oppR[i].indices) {
                val p = 1.0 / (1.0 + exp(-(r[i] - oppR[i][k])))
                diag[i] += p * (1.0 - p)
            }
            if (i > 0) {
                diag[i] += 1.0 / gaps[i]
                diag[i - 1] += 1.0 / gaps[i]
                off[i - 1] = -1.0 / gaps[i]
            }
        }
        val variances = inverseDiagonal(diag, off)
        return Fit(
            days,
            r.map { it * R_TO_ELO },
            variances.map { sqrt(it) * R_TO_ELO },
        )
    }

    /** Negative log-posterior at [r] (game terms plus Wiener drift). */
    private fun negLogLikelihood(
        r: DoubleArray,
        oppR: List<List<Double>>,
        scores: List<List<Double>>,
        gaps: DoubleArray,
    ): Double {
        var total = 0.0
        for (i in r.indices) {
            for (k in oppR[i].indices) {
                val p = (1.0 / (1.0 + exp(-(r[i] - oppR[i][k])))).coerceIn(1e-15, 1.0 - 1e-15)
                total -= scores[i][k] * ln(p) + (1.0 - scores[i][k]) * ln(1.0 - p)
            }
            if (i > 0) {
                val drift = r[i] - r[i - 1]
                total += drift * drift / (2.0 * gaps[i])
            }
        }
        return total
    }

    /** Solve the tridiagonal H x = rhs (rhs negated when [negateRhs]). */
    private fun thomasSolve(diag: DoubleArray, off: DoubleArray, rhs: DoubleArray, negateRhs: Boolean): DoubleArray {
        val n = diag.size
        if (n == 1) {
            val b = if (negateRhs) -rhs[0] else rhs[0]
            return doubleArrayOf(b / diag[0])
        }
        val cp = DoubleArray(n)
        val y = DoubleArray(n)
        var denom = diag[0]
        cp[0] = off[0] / denom
        y[0] = (if (negateRhs) -rhs[0] else rhs[0]) / denom
        for (i in 1 until n) {
            denom = diag[i] - off[i - 1] * cp[i - 1]
            cp[i] = if (i < n - 1) off[i] / denom else 0.0
            val b = if (negateRhs) -rhs[i] else rhs[i]
            y[i] = (b - off[i - 1] * y[i - 1]) / denom
        }
        val x = DoubleArray(n)
        x[n - 1] = y[n - 1]
        for (i in n - 2 downTo 0) x[i] = y[i] - cp[i] * x[i + 1]
        return x
    }

    /** Diagonal of the inverse of a symmetric positive-definite tridiagonal. */
    private fun inverseDiagonal(diag: DoubleArray, off: DoubleArray): DoubleArray {
        val n = diag.size
        if (n == 1) return doubleArrayOf(1.0 / diag[0])
        val d = DoubleArray(n)
        val b = DoubleArray(n)
        d[0] = diag[0]; b[0] = off[0]
        for (i in 1 until n) {
            val a = off[i - 1] / d[i - 1]
            d[i] = diag[i] - a * b[i - 1]
            b[i] = if (i < n - 1) off[i] else 0.0
        }
        val dp = DoubleArray(n)
        val bp = DoubleArray(n)
        dp[n - 1] = diag[n - 1]; bp[n - 1] = off[n - 2]
        for (i in n - 2 downTo 0) {
            val ap = off[i] / dp[i + 1]
            dp[i] = diag[i] - ap * bp[i + 1]
            bp[i] = if (i > 0) off[i - 1] else 0.0
        }
        val v = DoubleArray(n)
        v[n - 1] = 1.0 / d[n - 1]
        for (i in n - 2 downTo 0) v[i] = dp[i + 1] / (d[i] * dp[i + 1] - b[i] * bp[i + 1])
        return v
    }
}

/**
 * Rated-history mapping for WHR: whole days from real timestamps, fixed bot
 * anchors resolved at recompute time (so a future anchor recalibration
 * applies to old games automatically — same property as before).
 */
object PlayerWhr {
    private const val MILLIS_PER_DAY = 86_400_000L

    fun games(history: List<RatedGame>): List<Whr.Game> = history.map {
        Whr.Game(
            day = (it.ts / MILLIS_PER_DAY).toInt(),
            oppWhr = WhrAnchors.whrForRank(Rank.fromId(it.botRankId)),
            score = it.score,
        )
    }

    fun rate(history: List<RatedGame>): Whr.Rating = Whr.rate(games(history))

    fun trajectory(history: List<RatedGame>): List<Whr.Rating> = Whr.trajectory(games(history))
}
