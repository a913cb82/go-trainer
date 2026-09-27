package com.gotrainer.nine.game

/**
 * Score text formatting. KataGo's `final_score` (Chinese rules, komi 7.5) is
 * the ONLY score: there is deliberately no local estimator. A fallback guess
 * would miscount dead stones left on the board and present it as the result —
 * worse than showing that the count failed.
 */
object Scoring {
    private fun margin(x: Double): String {
        val a = kotlin.math.abs(x)
        return "+" + (if (a == kotlin.math.floor(a)) a.toInt().toString() else "%.1f".format(a))
    }

    /** KaTrain-style lead text, Black perspective: "Black +5", "White +4.5", "Draw". */
    fun formatLead(blackLead: Double): String {
        if (blackLead == 0.0) return "Draw"
        val winner = if (blackLead > 0) "Black" else "White"
        return "$winner ${margin(blackLead)}"
    }
}
