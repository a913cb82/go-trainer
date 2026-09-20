package com.gotrainer.nine.game

/** Minimal SGF writer for 9x9 games (komi 7, Japanese rules). */
object Sgf {
    fun boardToSgf(history: List<MoveRec>, komi: Double = 7.0, size: Int = 9): String {
        val sb = StringBuilder("(;GM[1]FF[4]SZ[$size]KM[$komi]RU[Japanese]")
        for (m in history) {
            val prop = if (m.color == 1) "B" else "W"
            val coord = if (m.x < 0) "" else "${('a' + m.x)}${('a' + m.y)}"
            sb.append(";$prop[$coord]")
        }
        sb.append(")")
        return sb.toString()
    }
}
