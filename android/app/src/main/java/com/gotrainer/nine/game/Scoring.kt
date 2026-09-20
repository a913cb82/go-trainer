package com.gotrainer.nine.game

/**
 * Score estimator for finished (or under-review) positions.
 *
 * Chinese-style area scoring on the stones as they stand: every stone counts,
 * empty regions fully surrounded by one colour count as its territory, shared
 * regions (seki-like) count for no one. Dead stones on the board are counted
 * as alive — fine for an estimate; close games should be counted properly.
 * Komi 7 to White, matching the rest of the trainer.
 */
object Scoring {
    const val KOMI = 7.0

    data class Result(val black: Double, val white: Double) {
        /** Positive = Black leads by N points. */
        val diff: Double get() = black - white
    }

    fun estimate(board: List<List<Int>>): Result {
        val n = board.size
        var black = 0.0
        var white = KOMI
        for (row in board) for (v in row) {
            if (v == 1) black += 1.0 else if (v == -1) white += 1.0
        }
        val seen = Array(n) { BooleanArray(n) }
        val dirs = arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        for (y in 0 until n) for (x in 0 until n) {
            if (board[y][x] != 0 || seen[y][x]) continue
            // Flood-fill one empty region, tracking which colours border it.
            var size = 0
            var touchesBlack = false
            var touchesWhite = false
            val stack = ArrayDeque<Pair<Int, Int>>()
            stack.add(x to y)
            seen[y][x] = true
            while (stack.isNotEmpty()) {
                val (cx, cy) = stack.removeLast()
                size++
                for (d in dirs) {
                    val nx = cx + d.first
                    val ny = cy + d.second
                    if (nx !in 0 until n || ny !in 0 until n) continue
                    when (board[ny][nx]) {
                        1 -> touchesBlack = true
                        -1 -> touchesWhite = true
                        else -> if (!seen[ny][nx]) {
                            seen[ny][nx] = true
                            stack.add(nx to ny)
                        }
                    }
                }
            }
            if (touchesBlack && !touchesWhite) black += size
            else if (touchesWhite && !touchesBlack) white += size
        }
        return Result(black, white)
    }

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

    /** e.g. "Game over · Black +5", "Game over · White +3.5", "Game over · Draw". */
    fun gameOverText(board: List<List<Int>>): String = "Game over · " + formatLead(estimate(board).diff)
}
