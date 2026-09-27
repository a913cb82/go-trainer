package com.gotrainer.nine.game

/**
 * One rated game. Stores the bot's ladder RANK id ("8k"), not its rating:
 * the rating is resolved at recompute time via [BotRatings.forRank], so a
 * future tournament recalibration applies to old games automatically.
 *
 * Only free-choice games are ever recorded; suggestions games leave no trace.
 * Write-ahead: appended with score 0 on the player's first ply (abandons are
 * losses with no end-of-game hook needed), upgraded to 1/0.5 on a clean
 * finish, voided (dropped) if the engine count fails.
 */
data class RatedGame(
    val ts: Long,
    val botRankId: String, // Rank.id, e.g. "8k"
    val playerBlack: Boolean,
    val score: Double, // 0 loss | 0.5 draw | 1 win, player perspective
)

/**
 * Player rating = sequential Glicko-2 fold over history from the 30k start.
 * Computed on read; never stored. Even long histories fold in milliseconds.
 */
object PlayerRating {
    val START = Glicko2.Rating(BotRatings.START_RATING, BotRatings.START_RD, BotRatings.START_VOL)

    fun rate(history: List<RatedGame>): Glicko2.Rating =
        history.fold(START) { r, g ->
            val botRating = BotRatings.forRank(Rank.fromId(g.botRankId))
            Glicko2.update(r, listOf(Glicko2.Opponent(botRating, BotRatings.BOT_RD, g.score)))
        }

    /** Rating trajectory after each game (drives the stats graph). */
    fun trajectory(history: List<RatedGame>): List<Glicko2.Rating> {
        val out = ArrayList<Glicko2.Rating>(history.size)
        var r = START
        for (g in history) {
            val botRating = BotRatings.forRank(Rank.fromId(g.botRankId))
            r = Glicko2.update(r, listOf(Glicko2.Opponent(botRating, BotRatings.BOT_RD, g.score)))
            out.add(r)
        }
        return out
    }

    // --- Storage encoding: flat delimited string, no JSON dependency ----
    // "ts|rankId|black|score;..."  e.g. "1727000000000|8k|1|1.0;..."

    fun encode(history: List<RatedGame>): String =
        history.joinToString(";") {
            "${it.ts}|${it.botRankId}|${if (it.playerBlack) 1 else 0}|${it.score}"
        }

    fun decode(s: String): List<RatedGame> {
        if (s.isBlank()) return emptyList()
        return s.split(";").mapNotNull { rec ->
            val p = rec.split("|")
            if (p.size != 4) return@mapNotNull null
            val ts = p[0].toLongOrNull() ?: return@mapNotNull null
            val score = p[3].toDoubleOrNull() ?: return@mapNotNull null
            if (score != 0.0 && score != 0.5 && score != 1.0) return@mapNotNull null
            RatedGame(ts, p[1], p[2] == "1", score)
        }
    }
}
