package com.gotrainer.nine.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gotrainer.nine.game.ColorChoice
import com.gotrainer.nine.game.Difficulty
import com.gotrainer.nine.game.PlayerRating
import com.gotrainer.nine.game.Rank
import com.gotrainer.nine.game.RatedGame
import com.gotrainer.nine.game.RatingCurve
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore("settings")

/** Single DataStore instance via applicationContext. */
class SettingsRepository(private val appContext: Context) {
    companion object {
        private val RANK = stringPreferencesKey("rank")
        private val RANKED = booleanPreferencesKey("ranked")
        private val COLOR = stringPreferencesKey("color")
        private val GRAPH_OPEN = booleanPreferencesKey("graph_open")
        private val DIFFICULTY = stringPreferencesKey("difficulty")
        private val TARGET_WINRATE = intPreferencesKey("target_winrate")
        // Rated-game history (free-choice games only); the player rating is
        // recomputed from this list on read, never stored. See PlayerWhr.
        private val RATED_HISTORY = stringPreferencesKey("rated_history")
        // Cached causal rating curve (per-point versions); Stats backfills
        // stale entries lazily, game flow appends/overwrites only the latest.
        private val RATING_CURVE = stringPreferencesKey("rating_curve")
        // Whole-game snapshot (GameStateSerde); restored on cold start.
        private val SAVED_GAME = stringPreferencesKey("saved_game")

    }

    data class Settings(
        val rank: Rank = Rank.R10K,
        val colorChoice: ColorChoice = ColorChoice.BLACK,
        val ranked: Boolean = true,
        val graphOpen: Boolean = true,
        val difficulty: Difficulty = Difficulty.FIXED,
        /** Target winrate percent for automatch (10-90). */
        val targetWinrate: Int = 50,
    )

    val settings: Flow<Settings> = appContext.settingsStore.data.map { p ->
        Settings(
            rank = Rank.fromId(p[RANK] ?: "10k"),
            colorChoice = ColorChoice.fromId(p[COLOR] ?: "black"),
            ranked = p[RANKED] ?: true,
            graphOpen = p[GRAPH_OPEN] ?: true,
            difficulty = Difficulty.fromId(p[DIFFICULTY] ?: "fixed"),
            targetWinrate = (p[TARGET_WINRATE] ?: 50).coerceIn(10, 90),
        )
    }

    /** Rated games, oldest first. Single active game, so last-record ops are safe. */
    val ratedHistory: Flow<List<RatedGame>> = appContext.settingsStore.data.map { p ->
        PlayerRating.decode(p[RATED_HISTORY] ?: "")
    }

    /** Cached causal points, oldest first; positions align with ratedHistory. */
    val ratedCurve: Flow<List<RatingCurve.CurvePoint>> = appContext.settingsStore.data.map { p ->
        RatingCurve.decode(p[RATING_CURVE] ?: "")
    }

    /** Write-ahead loss record on the player's first ply (abandons stay losses). */
    suspend fun appendRated(game: RatedGame, point: RatingCurve.CurvePoint) {
        val hist = ratedHistory.first()
        val curve = RatingCurve.appended(ratedCurve.first(), hist.size, point)
        appContext.settingsStore.edit {
            it[RATED_HISTORY] = PlayerRating.encode(hist + game)
            it[RATING_CURVE] = RatingCurve.encode(curve)
        }
    }

    /** Upgrade the pending record on a clean finish (win=1, draw=0.5). */
    suspend fun updateLastRated(score: Double, point: RatingCurve.CurvePoint) {
        val hist = ratedHistory.first()
        if (hist.isEmpty()) return
        val newHist = hist.dropLast(1) + hist.last().copy(score = score)
        val curve = RatingCurve.lastReplaced(ratedCurve.first(), newHist.size, point)
        appContext.settingsStore.edit {
            it[RATED_HISTORY] = PlayerRating.encode(newHist)
            it[RATING_CURVE] = RatingCurve.encode(curve)
        }
    }

    /** Overwrite the whole curve (Stats backfill publishes here). */
    suspend fun setCurve(points: List<RatingCurve.CurvePoint>) {
        appContext.settingsStore.edit { it[RATING_CURVE] = RatingCurve.encode(points) }
    }

    /** Clear all rated games (stats-screen reset; rank returns to 20k). */
    suspend fun clearRated() {
        appContext.settingsStore.edit {
            it[RATED_HISTORY] = ""
            it[RATING_CURVE] = ""
        }
    }

    /** Latest game snapshot, if any. Corrupt blobs read as null (fresh game). */
    val savedGame: Flow<String?> = appContext.settingsStore.data.map { p -> p[SAVED_GAME] }

    /** Overwrite the game snapshot (called on every state change; cheap). */
    suspend fun saveGame(blob: String) {
        appContext.settingsStore.edit { it[SAVED_GAME] = blob }
    }

    /** Void the pending record when the engine count fails (errors, never losses). */
    suspend fun dropLastRated() {
        val hist = ratedHistory.first()
        if (hist.isEmpty()) return
        val curve = RatingCurve.aligned(ratedCurve.first(), hist.size).dropLast(1)
        appContext.settingsStore.edit {
            it[RATED_HISTORY] = PlayerRating.encode(hist.dropLast(1))
            it[RATING_CURVE] = RatingCurve.encode(curve)
        }
    }

    suspend fun setRank(rank: Rank) = appContext.settingsStore.edit { it[RANK] = rank.id }
    suspend fun setColorChoice(c: ColorChoice) = appContext.settingsStore.edit { it[COLOR] = c.id }
    suspend fun setRanked(v: Boolean) = appContext.settingsStore.edit { it[RANKED] = v }
    suspend fun setGraphOpen(v: Boolean) = appContext.settingsStore.edit { it[GRAPH_OPEN] = v }
    suspend fun setDifficulty(d: Difficulty) = appContext.settingsStore.edit { it[DIFFICULTY] = d.id }
    suspend fun setTargetWinrate(v: Int) = appContext.settingsStore.edit { it[TARGET_WINRATE] = v.coerceIn(10, 90) }
}
