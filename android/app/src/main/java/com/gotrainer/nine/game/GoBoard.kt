package com.gotrainer.nine.game

/**
 * Pure 9x9 Go logic. Zero Android imports.
 * Board values: 1 = black, -1 = white, 0 = empty.
 */
class GoBoard(val size: Int = 9) {
    val cells: IntArray = IntArray(size * size)

    operator fun get(x: Int, y: Int): Int = cells[y * size + x]
    operator fun set(x: Int, y: Int, v: Int) { cells[y * size + x] = v }

    fun copy(): GoBoard {
        val b = GoBoard(size)
        cells.copyInto(b.cells)
        return b
    }

    fun signMap(): List<List<Int>> =
        List(size) { y -> List(size) { x -> this[x, y] } }

    /**
     * Play a stone. Returns captured count, or -1 if illegal
     * (occupied, suicide, or ko violation).
     */
    fun play(color: Int, x: Int, y: Int, koBan: Pair<Int, Int>? = null): Int {
        require(color == 1 || color == -1)
        if (x !in 0 until size || y !in 0 until size) return -1
        if (this[x, y] != 0) return -1
        if (koBan != null && koBan.first == x && koBan.second == y) return -1
        val trial = copy()
        trial[x, y] = color
        var captured = 0
        for ((nx, ny) in neighbors(x, y)) {
            if (trial[nx, ny] == -color && !trial.groupHasLiberty(nx, ny)) {
                captured += trial.removeGroup(nx, ny)
            }
        }
        if (!trial.groupHasLiberty(x, y)) return -1 // suicide
        // Commit
        trial.cells.copyInto(cells)
        return captured
    }

    fun isLegal(color: Int, x: Int, y: Int, koBan: Pair<Int, Int>? = null): Boolean {
        if (x !in 0 until size || y !in 0 until size || this[x, y] != 0) return false
        if (koBan != null && koBan.first == x && koBan.second == y) return false
        val trial = copy()
        return trial.play(color, x, y, null) >= 0
    }

    fun legalMoves(color: Int, koBan: Pair<Int, Int>? = null): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>(size * size)
        for (y in 0 until size) for (x in 0 until size) {
            if (isLegal(color, x, y, koBan)) out.add(x to y)
        }
        return out
    }

    private fun neighbors(x: Int, y: Int): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>(4)
        if (x > 0) out.add((x - 1) to y)
        if (x < size - 1) out.add((x + 1) to y)
        if (y > 0) out.add(x to (y - 1))
        if (y < size - 1) out.add(x to (y + 1))
        return out
    }

    private fun groupHasLiberty(sx: Int, sy: Int): Boolean {
        val color = this[sx, sy]
        if (color == 0) return true
        val seen = BooleanArray(size * size)
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.add(sx to sy)
        seen[sy * size + sx] = true
        while (stack.isNotEmpty()) {
            val (x, y) = stack.removeLast()
            for ((nx, ny) in neighbors(x, y)) {
                val v = this[nx, ny]
                if (v == 0) return true
                if (v == color && !seen[ny * size + nx]) {
                    seen[ny * size + nx] = true
                    stack.add(nx to ny)
                }
            }
        }
        return false
    }

    private fun removeGroup(sx: Int, sy: Int): Int {
        val color = this[sx, sy]
        var n = 0
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.add(sx to sy)
        while (stack.isNotEmpty()) {
            val (x, y) = stack.removeLast()
            if (this[x, y] != color) continue
            this[x, y] = 0
            n++
            for ((nx, ny) in neighbors(x, y)) {
                if (this[nx, ny] == color) stack.add(nx to ny)
            }
        }
        return n
    }

    companion object {
        private const val LETTERS = "ABCDEFGHJKLMNOPQRST" // GTP: skip I

        fun colChar(x: Int): String = LETTERS[x].toString()
        fun gtpCoord(x: Int, y: Int, size: Int = 9): String = "${colChar(x)}${size - y}"

        fun fromHistory(history: List<MoveRec>, size: Int = 9): GoBoard {
            val b = GoBoard(size)
            var ko: Pair<Int, Int>? = null
            for (m in history) {
                if (m.x < 0) {
                    ko = null
                    continue
                }
                val before = b.copy()
                val captured = b.play(m.color, m.x, m.y, ko)
                if (captured < 0) continue
                ko = detectKo(before, b, m.x, m.y)
            }
            return b
        }

        /** Simple ko: single-stone capture leaving a lone stone with one liberty. */
        fun detectKo(before: GoBoard, after: GoBoard, x: Int, y: Int): Pair<Int, Int>? {
            var diff = 0
            var emptyNow: Pair<Int, Int>? = null
            for (i in before.cells.indices) {
                if (before.cells[i] != 0 && after.cells[i] == 0) {
                    diff++
                    emptyNow = (i % before.size) to (i / before.size)
                } else if (before.cells[i] == 0 && after.cells[i] != 0) {
                    diff++
                }
            }
            // Classic ko: exactly 2 points changed (1 placed + 1 captured)
            return if (diff == 2) emptyNow else null
        }
    }
}
