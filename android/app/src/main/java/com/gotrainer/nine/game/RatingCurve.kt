package com.gotrainer.nine.game

/**
 * Versioned cache for the causal rating curve. Point g is `rate` over games
 * `1..g` under the stored version, so points are mutually independent: game
 * flow appends/overwrites only the latest point, Stats backfills exactly the
 * indices whose version mismatches (plus any tail gap). Anchors resolve at
 * recompute time, so a table refit rides the next backfill via a version bump.
 */
object RatingCurve {
    /** One cached causal point: curve version plus WHR estimate and doubt. */
    data class CurvePoint(val ver: Int, val whr: Double, val unc: Double)

    // --- Storage encoding: flat delimited string, no JSON dependency ----
    // "ver,whr,unc;..."  e.g. "1,751.95,228.1;1,660.5,199.0;..."

    fun encode(points: List<CurvePoint>): String =
        points.joinToString(";") { "${it.ver},${it.whr},${it.unc}" }

    fun decode(s: String): List<CurvePoint> {
        if (s.isBlank()) return emptyList()
        return s.split(";").mapNotNull { rec ->
            val p = rec.split(",")
            if (p.size != 3) return@mapNotNull null
            val ver = p[0].toIntOrNull() ?: return@mapNotNull null
            val whr = p[1].toDoubleOrNull() ?: return@mapNotNull null
            val unc = p[2].toDoubleOrNull() ?: return@mapNotNull null
            if (!whr.isFinite() || !unc.isFinite()) return@mapNotNull null
            CurvePoint(ver, whr, unc)
        }
    }

    /**
     * History indices needing (re)compute: every g with no point or a stale
     * version. Extras beyond the history (drop/reset path truncates) never
     * produce work here.
     */
    fun missingIndices(historySize: Int, cache: List<CurvePoint>): List<Int> {
        val need = ArrayList<Int>()
        for (g in 0 until historySize) {
            val c = cache.getOrNull(g)
            if (c == null || c.ver != Whr.CURVE_VERSION) need.add(g)
        }
        return need
    }

    /** Never-computed marker: always stale, filled by the Stats backfill. */
    val STALE = CurvePoint(ver = -1, whr = 0.0, unc = 0.0)

    /** Size the cache to the history: truncate extras, pad gaps stale. */
    fun aligned(cache: List<CurvePoint>, historySize: Int): List<CurvePoint> {
        val out = cache.take(historySize).toMutableList()
        while (out.size < historySize) out.add(STALE)
        return out
    }

    /**
     * Game-flow append: history grows by one, only r_G is solved. Gaps stay
     * stale for the Stats backfill; positions never shift.
     */
    fun appended(cache: List<CurvePoint>, historySizeBefore: Int, point: CurvePoint): List<CurvePoint> =
        aligned(cache, historySizeBefore) + point

    /**
     * Clean-finish upgrade: history length unchanged, the pending-loss point
     * is overwritten with the final-result point in place.
     */
    fun lastReplaced(cache: List<CurvePoint>, historySize: Int, point: CurvePoint): List<CurvePoint> {
        if (historySize <= 0) return emptyList()
        return aligned(cache, historySize - 1) + point
    }
}
