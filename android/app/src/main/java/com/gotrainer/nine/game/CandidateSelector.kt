package com.gotrainer.nine.game

/**
 * Pure candidate selection mirroring server/src/index.ts strategies.
 * Pool entries carry (humanPrior, strongScore); output keeps input order info via labels A-E.
 */
data class PoolEntry(
    val x: Int,
    val y: Int,
    val humanPolicy: Double,
    val strongWinrate: Double = 0.5,
    val strongScore: Double = 0.0,
)

object CandidateSelector {
    fun select(
        poolByHuman: List<PoolEntry>,
        poolByScore: List<PoolEntry>,
        strategy: Strategy,
        rank: Rank,
        n: Int,
    ): List<Candidate> {
        val th = rank.thresholds()
        val bestScore = poolByScore.firstOrNull()?.strongScore ?: 0.0
        fun gapOf(e: PoolEntry) = bestScore - e.strongScore

        val picked: List<PoolEntry> = when (strategy) {
            Strategy.HUMAN_ONLY -> poolByHuman.take(n)
            Strategy.STRONG_ONLY -> poolByScore.take(n)
            Strategy.TESUJI -> {
                val best = poolByScore.firstOrNull()
                val bad = poolByHuman.filter { it != best && gapOf(it) >= 2.0 }.take(4)
                (if (best != null) listOf(best) + bad else poolByHuman.take(n)).take(n)
            }
            Strategy.BLUNDER_CHECK -> {
                val good = poolByHuman.filter { gapOf(it) <= th.good }.take(4)
                val bad = poolByHuman.filter { gapOf(it) >= 5.0 }.take(1)
                val combo = (good + bad).take(n)
                if (combo.size < n) poolByHuman.take(n) else combo
            }
            Strategy.GOOD_VS_TEMPTING -> {
                val good = poolByHuman.filter { gapOf(it) <= th.good }.take(3)
                val bad = poolByHuman.filter { gapOf(it) >= th.bad }.take(2)
                var combo = good + bad
                if (combo.size < n) {
                    val rest = poolByHuman.filter { e -> combo.none { it.x == e.x && it.y == e.y } }
                        .take(n - combo.size)
                    combo = combo + rest
                }
                combo.take(n)
            }
        }

        // Dedup by coordinate, skip passes (none in PoolEntry), label A-E.
        val seen = LinkedHashSet<String>()
        val out = ArrayList<Candidate>()
        for (e in picked) {
            val key = "${e.x},${e.y}"
            if (!seen.add(key)) continue
            val gap = maxOf(0.0, Math.round(gapOf(e) * 10) / 10.0)
            val tag = if (gap <= th.good) "good" else if (gap >= th.bad) "overconcentrated" else "ok"
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
        return out
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
