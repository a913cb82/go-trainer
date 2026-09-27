package com.gotrainer.nine.game

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Glicko-2 (Glickman 2001), single rating period per update call. Pure JVM:
 * zero Android imports, so the paper's own example doubles as the unit test.
 *
 * We call [update] once per rated game (period of 1). OGS batches ~15 games
 * per period; the sequential fold is simpler and deviation-correct, at the
 * cost of slightly different RD dynamics. Bot opponents are fixed anchors:
 * only the player's [Rating] ever changes.
 */
object Glicko2 {
    /** Rating-scale divisor from the paper (also the rating floor offset). */
    const val SCALE = 173.7178

    /** System constant constraining volatility change; Glickman's 0.3-1.2 band. */
    const val TAU = 0.5

    /** Convergence epsilon for the volatility iteration (paper's value). */
    private const val EPSILON = 0.000001

    data class Rating(val rating: Double, val rd: Double, val vol: Double)

    /** One opponent in the period: their rating/RD, and our score (1/0.5/0). */
    data class Opponent(val rating: Double, val rd: Double, val score: Double)

    private fun g(phi: Double): Double = 1.0 / sqrt(1.0 + 3.0 * phi * phi / (PI * PI))

    private fun e(mu: Double, muOpp: Double, phiOpp: Double): Double =
        1.0 / (1.0 + exp(-g(phiOpp) * (mu - muOpp)))

    /**
     * Expected score (win probability) for [player] vs a fixed opponent.
     * Inverted by automatch to pick a bot for a target winrate.
     */
    fun expectedScore(player: Rating, oppRating: Double, oppRd: Double): Double {
        val mu = (player.rating - 1500.0) / SCALE
        val muOpp = (oppRating - 1500.0) / SCALE
        return e(mu, muOpp, oppRd / SCALE)
    }

    fun update(player: Rating, opponents: List<Opponent>): Rating {
        require(opponents.isNotEmpty())
        val mu = (player.rating - 1500.0) / SCALE
        val phi = player.rd / SCALE
        data class Term(val g: Double, val e: Double, val s: Double)
        val terms = opponents.map {
            val muOpp = (it.rating - 1500.0) / SCALE
            val phiOpp = it.rd / SCALE
            Term(g(phiOpp), e(mu, muOpp, phiOpp), it.score)
        }
        val v = 1.0 / terms.sumOf { it.g * it.g * it.e * (1.0 - it.e) }
        val delta = v * terms.sumOf { it.g * (it.s - it.e) }

        // New volatility via the paper's Illinois-style iteration on f(x).
        val a = ln(player.vol * player.vol)
        fun f(x: Double): Double {
            val ex = exp(x)
            val denom = phi * phi + v + ex
            return ex * (delta * delta - phi * phi - v - ex) / (2.0 * denom * denom) -
                (x - a) / (TAU * TAU)
        }
        var bigB: Double
        if (delta * delta > phi * phi + v) {
            bigB = ln(delta * delta - phi * phi - v)
        } else {
            var k = 1
            while (f(a - k * TAU) < 0) k++
            bigB = a - k * TAU
        }
        var fA = f(a)
        var fB = f(bigB)
        var smallA = a
        while (kotlin.math.abs(bigB - smallA) > EPSILON) {
            val c = smallA + (smallA - bigB) * fA / (fB - fA)
            val fC = f(c)
            if (fC * fB <= 0) {
                smallA = bigB
                fA = fB
            } else {
                fA /= 2.0
            }
            bigB = c
            fB = fC
        }
        val volPrime = exp(smallA / 2.0)

        val phiStar = sqrt(phi * phi + volPrime * volPrime)
        val phiPrime = 1.0 / sqrt(1.0 / (phiStar * phiStar) + 1.0 / v)
        val muPrime = mu + phiPrime * phiPrime * terms.sumOf { it.g * (it.s - it.e) }
        return Rating(
            rating = SCALE * muPrime + 1500.0,
            rd = SCALE * phiPrime,
            vol = volPrime,
        )
    }
}
