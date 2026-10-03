package com.gotrainer.nine.game

/**
 * Whole-game persistence: encodes a [GameState] to one flat string for
 * DataStore, so a killed app resumes exactly mid-game. The board rebuilds
 * from history on load (single source of truth); transients (thinking,
 * scoring, errors) reset. Any corruption decodes to null => fresh game.
 *
 * v2 dropped the multiple-choice payload (candidates, evaluations,
 * per-move gaps, counts); v1 blobs no longer decode and start fresh.
 */
object GameStateSerde {
    private const val VERSION = "v2"

    fun encode(s: GameState): String = buildString {
        appendLine(VERSION)
        appendLine(
            listOf(
                s.toMove, s.playerColor, s.status, s.passing, s.rank.id,
                s.colorChoice.id, s.reviewIdx ?: -1, b(s.undoUsed),
                s.difficulty.id, s.targetWinrate, b(s.pendingFreePly),
                s.finalScoreLead?.toString() ?: "null", b(s.graphOpen),
                s.playerRating, s.playerRankText, b(s.ranked),
            ).joinToString(","),
        )
        appendLine(s.history.joinToString(";") { "${it.x},${it.y},${it.color}" })
        appendLine(s.winrateHistory.joinToString(","))
        appendLine(s.finalOwnership?.flatten()?.joinToString(",") ?: "NULL")
    }

    fun decode(blob: String): GameState? {
        try {
            val lines = blob.lines()
            if (lines.size < 5 || lines[0] != VERSION) return null
            val f = lines[1].split(",")
            if (f.size != 16) return null
            return GameState(
                toMove = f[0].toIntOrNull() ?: return null,
                playerColor = f[1].toIntOrNull() ?: return null,
                status = if (f[2] == "finished") "finished" else "playing",
                passing = f[3].toIntOrNull() ?: return null,
                rank = Rank.fromId(f[4]),
                colorChoice = ColorChoice.fromId(f[5]),
                reviewIdx = f[6].toIntOrNull()?.takeIf { it >= 0 },
                undoUsed = f[7] == "1",
                difficulty = Difficulty.fromId(f[8]),
                targetWinrate = (f[9].toIntOrNull() ?: return null).coerceIn(10, 90),
                pendingFreePly = f[10] == "1",
                finalScoreLead = f[11].takeIf { it != "null" }?.toDoubleOrNull(),
                graphOpen = f[12] == "1",
                playerRating = f[13].toDoubleOrNull() ?: return null,
                playerRankText = f[14],
                ranked = f[15] == "1",
                history = if (lines[2].isEmpty()) emptyList() else lines[2].split(";").map { decodeMove(it) ?: return null },
                winrateHistory = if (lines[3].isEmpty()) emptyList() else lines[3].split(",").map { it.toDoubleOrNull() ?: return null },
                finalOwnership = lines[4].takeIf { it != "NULL" }?.split(",")?.map { it.toDoubleOrNull() ?: return null }
                    ?.takeIf { it.size == 81 }?.chunked(9),
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun b(v: Boolean) = if (v) "1" else "0"

    private fun decodeMove(s: String): MoveRec? {
        val f = s.split(",")
        if (f.size != 3) return null
        return MoveRec(
            x = f[0].toIntOrNull() ?: return null,
            y = f[1].toIntOrNull() ?: return null,
            color = f[2].toIntOrNull() ?: return null,
        )
    }
}
