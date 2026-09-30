package com.gotrainer.nine.game

import kotlin.random.Random

/**
 * Pure 9x9 Go logic. Zero Android imports.
 * Board values: 1 = black, -1 = white, 0 = empty.
 *
 * Repetition uses POSITIONAL SUPERKO (Chinese rules): a stone placement is
 * illegal iff its resulting board has occurred before at any point in the
 * game. No ko-ban point, no liberty heuristics — ko, snapback and ko fights
 * all reduce to "have I seen this board before". Passes never change the
 * board, so they need no special handling (a delayed retake differs by the
 * moves played since, an immediate one matches and stays banned).
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
     * (occupied, suicide, or superko repetition).
     *
     * [previousHashes] holds [positionHash] of every board since game start
     * (see [replay]); empty means "no history", never "ban nothing yet".
     */
    fun play(color: Int, x: Int, y: Int, previousHashes: Set<Long> = emptySet()): Int {
        require(color == 1 || color == -1)
        if (x !in 0 until size || y !in 0 until size) return -1
        if (this[x, y] != 0) return -1
        val trial = copy()
        trial[x, y] = color
        var captured = 0
        for ((nx, ny) in neighbors(x, y)) {
            if (trial[nx, ny] == -color && !trial.groupHasLiberty(nx, ny)) {
                captured += trial.removeGroup(nx, ny)
            }
        }
        if (!trial.groupHasLiberty(x, y)) return -1 // suicide
        if (trial.positionHash() in previousHashes) return -1 // superko
        // Commit
        trial.cells.copyInto(cells)
        return captured
    }

    fun isLegal(color: Int, x: Int, y: Int, previousHashes: Set<Long> = emptySet()): Boolean {
        if (x !in 0 until size || y !in 0 until size || this[x, y] != 0) return false
        val trial = copy()
        return trial.play(color, x, y, previousHashes) >= 0
    }

    fun legalMoves(color: Int, previousHashes: Set<Long> = emptySet()): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>(size * size)
        for (y in 0 until size) for (x in 0 until size) {
            if (isLegal(color, x, y, previousHashes)) out.add(x to y)
        }
        return out
    }

    /** One replay pass: the board plus every position hash since game start. */
    data class ReplayResult(val board: GoBoard, val hashes: Set<Long>)

    /**
     * Zobrist hash of the current position. Recomputed from scratch on
     * demand (81 cells) rather than maintained incrementally: stateless, so
     * it cannot desync, and at ~80 plies/game the whole replay costs
     * microseconds — far cheaper than the flood fills around it.
     */
    fun positionHash(): Long {
        var h = 0L
        for (i in cells.indices) {
            when (cells[i]) {
                1 -> h = h xor ZOBRIST_BLACK[i]
                -1 -> h = h xor ZOBRIST_WHITE[i]
            }
        }
        return h
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
        // Zobrist table: fixed seed, so hashes are stable across runs and
        // tests. Sized for 19x19; 9x9 uses the first 81 entries.
        private val ZOBRIST_BLACK: LongArray
        private val ZOBRIST_WHITE: LongArray

        init {
            val rng = Random(0x5EED_9A09L)
            ZOBRIST_BLACK = LongArray(361) { rng.nextLong() }
            ZOBRIST_WHITE = LongArray(361) { rng.nextLong() }
        }

        private const val LETTERS = "ABCDEFGHJKLMNOPQRST" // GTP: skip I

        fun colChar(x: Int): String = LETTERS[x].toString()
        fun gtpCoord(x: Int, y: Int, size: Int = 9): String = "${colChar(x)}${size - y}"

        /**
         * Replay history once, threading superko legality through it. Passes
         * never change the board and are always legal. A single call replaces
         * the old board-replay + ko-replay pair, so legality checks cost one
         * pass no matter the game length.
         */
        fun replay(history: List<MoveRec>, size: Int = 9): ReplayResult {
            val b = GoBoard(size)
            val seen = HashSet<Long>(history.size + 1)
            seen.add(b.positionHash())
            for (m in history) {
                if (m.x < 0) continue
                if (b.play(m.color, m.x, m.y, seen) < 0) continue
                seen.add(b.positionHash())
            }
            return ReplayResult(b, seen)
        }

        fun fromHistory(history: List<MoveRec>, size: Int = 9): GoBoard =
            replay(history, size).board

        /**
         * Stones present in [before] but gone in [after], with their color —
         * i.e. what the last play captured. Pure board diff; placement shows
         * as added, undo restorations as added, so only real plays pop.
         */
        fun capturedBy(
            before: List<List<Int>>,
            after: List<List<Int>>,
        ): List<CapturedStone> {
            val out = mutableListOf<CapturedStone>()
            for (y in before.indices) for (x in before[y].indices) {
                val v = before[y][x]
                if (v != 0 && after[y][x] == 0) out.add(CapturedStone(x, y, v))
            }
            return out
        }
    }
}
