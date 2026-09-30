package com.gotrainer.nine.game

/**
 * Whole-game persistence: encodes a [GameState] to one flat string for
 * DataStore, so a killed app resumes exactly mid-game. The board rebuilds
 * from history on load (single source of truth); transients (thinking,
 * scoring, errors) reset. Any corruption decodes to null => fresh game.
 */
object GameStateSerde {
    private const val VERSION = "v1"

    fun encode(s: GameState): String = buildString {
        appendLine(VERSION)
        appendLine(
            listOf(
                s.toMove, s.playerColor, s.status, s.passing, s.rank.id,
                b(s.multipleChoice), s.bestCount, s.worstCount, s.colorChoice.id,
                b(s.showFeedback), b(s.feedbackScopeAll),
                s.reviewIdx ?: -1, b(s.undoUsed), s.difficulty.id,
                s.targetWinrate, b(s.pendingFreePly),
                s.finalScoreLead?.toString() ?: "null", b(s.graphOpen),
                s.playerRating, s.playerRankText, b(s.ranked),
            ).joinToString(","),
        )
        appendLine(s.history.joinToString(";") { encodeMove(it) })
        appendLine(s.candidates?.joinToString(";") { encodeCand(it) } ?: "NULL")
        appendLine(s.evaluations?.joinToString(";") { encodeEval(it) } ?: "NULL")
        appendLine(s.winrateHistory.joinToString(","))
        appendLine(s.pastCandidates.entries.joinToString(";") { (k, v) -> "$k=" + v.joinToString("|") { encodeCand(it) } })
        appendLine(s.pastEvals.entries.joinToString(";") { (k, v) -> "$k=" + v.joinToString("|") { encodeEval(it) } })
        appendLine(s.finalOwnership?.flatten()?.joinToString(",") ?: "NULL")
    }

    fun decode(blob: String): GameState? {
        try {
            val lines = blob.lines()
            if (lines.size < 8 || lines[0] != VERSION) return null
            val f = lines[1].split(",")
            // 20-field blobs predate the ranked toggle; they default to ranked.
            if (f.size != 20 && f.size != 21) return null
            return GameState(
                toMove = f[0].toIntOrNull() ?: return null,
                playerColor = f[1].toIntOrNull() ?: return null,
                status = if (f[2] == "finished") "finished" else "playing",
                passing = f[3].toIntOrNull() ?: return null,
                rank = Rank.fromId(f[4]),
                multipleChoice = f[5] == "1",
                bestCount = (f[6].toIntOrNull() ?: return null).coerceIn(0, 5),
                worstCount = (f[7].toIntOrNull() ?: return null).coerceIn(0, 5),
                colorChoice = ColorChoice.fromId(f[8]),
                showFeedback = f[9] == "1",
                feedbackScopeAll = f[10] == "1",
                reviewIdx = f[11].toIntOrNull()?.takeIf { it >= 0 },
                undoUsed = f[12] == "1",
                difficulty = Difficulty.fromId(f[13]),
                targetWinrate = (f[14].toIntOrNull() ?: return null).coerceIn(10, 90),
                pendingFreePly = f[15] == "1",
                finalScoreLead = f[16].takeIf { it != "null" }?.toDoubleOrNull(),
                graphOpen = f[17] == "1",
                playerRating = f[18].toDoubleOrNull() ?: return null,
                playerRankText = f[19],
                ranked = f.getOrNull(20)?.let { it == "1" } ?: true,
                history = if (lines[2].isEmpty()) emptyList() else lines[2].split(";").map { decodeMove(it) ?: return null },
                candidates = lines[3].takeIf { it != "NULL" }?.split(";")?.map { decodeCand(it) ?: return null },
                evaluations = lines[4].takeIf { it != "NULL" }?.split(";")?.map { decodeEval(it) ?: return null },
                winrateHistory = if (lines[5].isEmpty()) emptyList() else lines[5].split(",").map { it.toDoubleOrNull() ?: return null },
                pastCandidates = decodeMap(lines[6], ::decodeCand) ?: return null,
                pastEvals = decodeMap(lines[7], ::decodeEval) ?: return null,
                finalOwnership = lines.getOrNull(8)?.takeIf { it != "NULL" }?.split(",")?.map { it.toDoubleOrNull() ?: return null }
                    ?.takeIf { it.size == 81 }?.chunked(9),
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun b(v: Boolean) = if (v) "1" else "0"

    private fun encodeCand(c: Candidate) =
        "${c.x},${c.y},${c.label},${c.humanPolicy},${c.strongWinrate},${c.strongScore},${c.scoreGap},${c.tag}"

    private fun encodeEval(e: EvaluatedMove) =
        "${e.x},${e.y},${e.label},${e.humanPolicy},${e.strongWinrate},${e.strongScore},${e.scoreGap},${e.gap},${e.tag}"

    private fun encodeMove(m: MoveRec): String {
        val base = "${m.x},${m.y},${m.color}"
        val cand = m.candidate?.let { "~" + encodeCand(it) } ?: ""
        val gap = m.gap?.let { "@$it" } ?: ""
        return base + cand + gap
    }

    private fun decodeCand(s: String): Candidate? {
        val f = s.split(",")
        if (f.size != 8) return null
        return Candidate(
            x = f[0].toIntOrNull() ?: return null,
            y = f[1].toIntOrNull() ?: return null,
            label = f[2],
            humanPolicy = f[3].toDoubleOrNull() ?: return null,
            strongWinrate = f[4].toDoubleOrNull() ?: return null,
            strongScore = f[5].toDoubleOrNull() ?: return null,
            scoreGap = f[6].toDoubleOrNull() ?: return null,
            tag = f[7],
        )
    }

    private fun decodeEval(s: String): EvaluatedMove? {
        val f = s.split(",")
        if (f.size != 9) return null
        return EvaluatedMove(
            x = f[0].toIntOrNull() ?: return null,
            y = f[1].toIntOrNull() ?: return null,
            label = f[2],
            humanPolicy = f[3].toDoubleOrNull() ?: return null,
            strongWinrate = f[4].toDoubleOrNull() ?: return null,
            strongScore = f[5].toDoubleOrNull() ?: return null,
            scoreGap = f[6].toDoubleOrNull() ?: return null,
            gap = f[7].toDoubleOrNull() ?: return null,
            tag = f[8],
        )
    }

    private fun decodeMove(s: String): MoveRec? {
        val gapSplit = s.split("@")
        if (gapSplit.size > 2) return null
        val gap = gapSplit.getOrNull(1)?.toDoubleOrNull()
        if (gapSplit.size == 2 && gap == null) return null
        val candSplit = gapSplit[0].split("~")
        if (candSplit.size > 2) return null
        val base = candSplit[0].split(",")
        if (base.size != 3) return null
        return MoveRec(
            x = base[0].toIntOrNull() ?: return null,
            y = base[1].toIntOrNull() ?: return null,
            color = base[2].toIntOrNull() ?: return null,
            candidate = candSplit.getOrNull(1)?.let { decodeCand(it) ?: return null },
            gap = gap,
        )
    }

    private fun <T> decodeMap(line: String, decode: (String) -> T?): Map<Int, List<T>>? {
        if (line.isEmpty()) return emptyMap()
        val out = mutableMapOf<Int, List<T>>()
        for (entry in line.split(";")) {
            val kv = entry.split("=", limit = 2)
            if (kv.size != 2) return null
            val k = kv[0].toIntOrNull() ?: return null
            out[k] = kv[1].split("|").map { decode(it) ?: return null }
        }
        return out
    }
}
