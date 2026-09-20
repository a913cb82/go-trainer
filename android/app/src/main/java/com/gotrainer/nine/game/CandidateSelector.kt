package com.gotrainer.nine.game

/**
 * Pure candidate selection (shared contract with the web trainer, now
 * threshold-free).
 *
 * Selection has two independent axes:
 *  - QUALITY: score loss against the best analyzed move (points).
 *  - HUMAN-NESS: the human net's policy at the chosen rank profile.
 *
 * There are no per-rank point thresholds any more. Rank enters through the
 * human net only, via [humanPool]: the moves a player at that rank seriously
 * considers. Quality is ordinal within that pool (lowest loss = best).
 */
data class PoolEntry(
    val x: Int,
    val y: Int,
    val humanPolicy: Double,
    val strongWinrate: Double = 0.5,
    val strongScore: Double = 0.0,
)

object CandidateSelector {
    /** Share of the human policy mass the candidate pool must cover. */
    private const val HUMAN_COVERAGE = 0.85

    /** Floor so a best/worst split always has material in sharp positions. */
    private const val HUMAN_POOL_MIN = 5

    /**
     * The moves a human at this rank seriously considers: the shortest prefix
     * of the policy-sorted pool that covers [HUMAN_COVERAGE] of the total
     * policy mass, and never fewer than [HUMAN_POOL_MIN] moves.
     */
    fun humanPool(byHuman: List<PoolEntry>): List<PoolEntry> {
        if (byHuman.isEmpty()) return byHuman
        val total = byHuman.sumOf { it.humanPolicy }
        if (total <= 0.0) return byHuman.take(HUMAN_POOL_MIN)
        val target = total * HUMAN_COVERAGE
        var acc = 0.0
        var i = 0
        while (i < byHuman.size && (i < HUMAN_POOL_MIN || acc < target)) {
            acc += byHuman[i].humanPolicy
            i++
        }
        return byHuman.take(i)
    }

    private fun lossOf(e: PoolEntry, bestScore: Double): Double =
        maxOf(0.0, bestScore - e.strongScore)

    /**
     * Strategy shape: (best-group slots, worst-group slots). The worst group
     * holds the highest-loss moves in the human pool; the best group the
     * lowest-loss. Scales with n (the app offers 0/3/5).
     */
    fun slots(strategy: Strategy, n: Int): Pair<Int, Int> = when (strategy) {
        Strategy.HUMAN_ONLY -> 0 to 0
        Strategy.STRONG_ONLY -> n to 0
        Strategy.GOOD_VS_TEMPTING -> {
            val best = minOf(2, maxOf(1, n - 1))
            best to (n - best)
        }
        Strategy.SPLIT_3_2 -> {
            val best = minOf(3, maxOf(1, n - 1))
            best to (n - best)
        }
        Strategy.BLUNDER_CHECK -> {
            val worst = minOf(1, maxOf(0, n - 1))
            (n - worst) to worst
        }
        Strategy.TESUJI -> 1 to maxOf(0, n - 1)
    }

    fun select(
        poolByHuman: List<PoolEntry>,
        poolByScore: List<PoolEntry>,
        strategy: Strategy,
        n: Int,
    ): List<Candidate> {
        val bestScore = poolByScore.maxOfOrNull { it.strongScore } ?: return emptyList()
        val human = humanPool(poolByHuman)
        // Stable sort: the input is policy-sorted, so equal losses keep the more
        // human move first (ties are common at ~150 analyze visits).
        val humanByLoss = human.sortedBy { lossOf(it, bestScore) }
        val byLoss = poolByScore.sortedBy { lossOf(it, bestScore) }

        val picked: List<Pair<PoolEntry, String>> = when (strategy) {
            // No score at all: the five most human moves at this rank.
            Strategy.HUMAN_ONLY -> poolByHuman.take(n).map { it to "ok" }

            Strategy.STRONG_ONLY -> byLoss.take(n).map { it to "good" }

            Strategy.GOOD_VS_TEMPTING, Strategy.BLUNDER_CHECK, Strategy.SPLIT_3_2 -> {
                val (b, w) = slots(strategy, n)
                val best = if (b > 0) humanByLoss.take(b).map { it to "good" } else emptyList()
                val worst = if (w > 0) humanByLoss.takeLast(w).map { it to "overconcentrated" } else emptyList()
                best + worst
            }

            // Best = objectively best (the tesuji may be rank-atypical); the
            // rest are the worst moves a human at this rank would still play.
            Strategy.TESUJI -> {
                val best = byLoss.firstOrNull()?.let { listOf(it to "good") } ?: emptyList()
                val worst = humanByLoss.takeLast(n - 1).map { it to "overconcentrated" }
                best + worst
            }
        }

        val seen = LinkedHashSet<String>()
        val out = ArrayList<Candidate>()
        fun add(e: PoolEntry, tag: String) {
            val key = "${e.x},${e.y}"
            if (!seen.add(key)) return
            val gap = maxOf(0.0, Math.round(lossOf(e, bestScore) * 10) / 10.0)
            out.add(
                Candidate(
                    x = e.x, y = e.y,
                    label = "ABCDE"[out.size % 5].toString(),
                    humanPolicy = e.humanPolicy,
                    strongWinrate = e.strongWinrate,
                    strongScore = e.strongScore,
                    scoreGap = gap,
                    tag = tag,
                )
            )
        }
        for ((e, t) in picked) add(e, t)
        // Top up (tiny pools, overlap between groups) from the strategy's own
        // ordering so the extra candidates stay in character.
        val fill = when (strategy) {
            Strategy.STRONG_ONLY -> byLoss
            Strategy.TESUJI -> humanByLoss
            else -> poolByHuman
        }
        for (e in fill) {
            if (out.size >= n) break
            add(e, "ok")
        }
        // Flat strategies (human-like, strongest) have no best/worst groups:
        // color them positionally — first green, last red, middle yellow.
        val flat = strategy == Strategy.HUMAN_ONLY || strategy == Strategy.STRONG_ONLY
        if (!flat || out.isEmpty()) return out.take(n)
        return out.take(n).mapIndexed { i, c ->
            c.copy(
                tag = when {
                    out.size == 1 || i == 0 -> "good"
                    i == out.size - 1 -> "overconcentrated"
                    else -> "ok"
                }
            )
        }
    }

    fun toEvaluated(candidates: List<Candidate>): List<EvaluatedMove> {
        val best = candidates.maxOfOrNull { it.strongScore } ?: 0.0
        return candidates.map { c ->
            val gap = best - c.strongScore
            EvaluatedMove(
                x = c.x, y = c.y, label = c.label,
                humanPolicy = c.humanPolicy, strongWinrate = c.strongWinrate,
                strongScore = c.strongScore, scoreGap = c.scoreGap, gap = gap, tag = c.tag,
            )
        }
    }
}
