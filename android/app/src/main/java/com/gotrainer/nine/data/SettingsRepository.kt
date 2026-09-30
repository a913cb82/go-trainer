package com.gotrainer.nine.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gotrainer.nine.game.CandidateSelector
import com.gotrainer.nine.game.ColorChoice
import com.gotrainer.nine.game.Difficulty
import com.gotrainer.nine.game.PlayerRating
import com.gotrainer.nine.game.Rank
import com.gotrainer.nine.game.RatedGame
import com.gotrainer.nine.game.Strategy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore("settings")

/** Single DataStore instance via applicationContext. */
class SettingsRepository(private val appContext: Context) {
    companion object {
        private val RANK = stringPreferencesKey("rank")
        private val MULTIPLE_CHOICE = booleanPreferencesKey("multiple_choice")
        private val BEST_COUNT = intPreferencesKey("best_count")
        private val WORST_COUNT = intPreferencesKey("worst_count")
        private val COLOR = stringPreferencesKey("color")
        private val SHOW_FEEDBACK = booleanPreferencesKey("show_feedback")
        private val GRAPH_OPEN = booleanPreferencesKey("graph_open")
        private val DIFFICULTY = stringPreferencesKey("difficulty")
        private val TARGET_WINRATE = intPreferencesKey("target_winrate")
        // Rated-game history (free-choice games only); player rating is a
        // fold over this list, never stored. See PlayerRating.
        private val RATED_HISTORY = stringPreferencesKey("rated_history")
        // Whole-game snapshot (GameStateSerde); restored on cold start.
        private val SAVED_GAME = stringPreferencesKey("saved_game")

        // Legacy keys (0/3/5 + strategy presets), kept for one-time migration.
        private val LEGACY_N = intPreferencesKey("n")
        private val LEGACY_STRATEGY = stringPreferencesKey("strategy")
    }

    data class Settings(
        val rank: Rank = Rank.R10K,
        val multipleChoice: Boolean = true,
        val bestCount: Int = 2,
        val worstCount: Int = 3,
        val colorChoice: ColorChoice = ColorChoice.BLACK,
        val showFeedback: Boolean = true,
        val graphOpen: Boolean = true,
        val difficulty: Difficulty = Difficulty.FIXED,
        /** Target winrate percent for automatch (10-90). */
        val targetWinrate: Int = 50,
    )

    val settings: Flow<Settings> = appContext.settingsStore.data.map { p ->
        val best = p[BEST_COUNT]
        val worst = p[WORST_COUNT]
        val multiple = p[MULTIPLE_CHOICE]
        // One-time migration from the legacy n/strategy presets.
        val migrated = if (best == null || worst == null || multiple == null) {
            migrateLegacy(p[LEGACY_N] ?: 5, p[LEGACY_STRATEGY])
        } else {
            null
        }
        Settings(
            rank = Rank.fromId(p[RANK] ?: "10k"),
            multipleChoice = multiple ?: migrated?.first ?: true,
            bestCount = (best ?: migrated?.second ?: 2).coerceIn(0, 5),
            worstCount = (worst ?: migrated?.third ?: 3).coerceIn(0, 5),
            colorChoice = ColorChoice.fromId(p[COLOR] ?: "black"),
            showFeedback = p[SHOW_FEEDBACK] ?: true,
            graphOpen = p[GRAPH_OPEN] ?: true,
            difficulty = Difficulty.fromId(p[DIFFICULTY] ?: "fixed"),
            targetWinrate = (p[TARGET_WINRATE] ?: 50).coerceIn(10, 90),
        )
    }

    /** Rated games, oldest first. Single active game, so last-record ops are safe. */
    val ratedHistory: Flow<List<RatedGame>> = appContext.settingsStore.data.map { p ->
        PlayerRating.decode(p[RATED_HISTORY] ?: "")
    }

    /** Write-ahead loss record on the player's first ply (abandons stay losses). */
    suspend fun appendRated(game: RatedGame) {
        val hist = ratedHistory.first() + game
        appContext.settingsStore.edit { it[RATED_HISTORY] = PlayerRating.encode(hist) }
    }

    /** Upgrade the pending record on a clean finish (win=1, draw=0.5). */
    suspend fun updateLastRated(score: Double) {
        val hist = ratedHistory.first()
        if (hist.isEmpty()) return
        appContext.settingsStore.edit {
            it[RATED_HISTORY] = PlayerRating.encode(hist.dropLast(1) + hist.last().copy(score = score))
        }
    }

    /** Clear all rated games (stats-screen reset; rank returns to 30k). */
    suspend fun clearRated() {
        appContext.settingsStore.edit { it[RATED_HISTORY] = "" }
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
        appContext.settingsStore.edit {
            it[RATED_HISTORY] = PlayerRating.encode(hist.dropLast(1))
        }
    }

    /** Legacy (n, strategyId) -> (multipleChoice, best, worst). */
    private fun migrateLegacy(n: Int, strategyId: String?): Triple<Boolean, Int, Int> {
        if (n == 0) return Triple(false, 2, 3)
        val strategy = Strategy.fromId(strategyId ?: "good-vs-tempting")
        val (b, w) = CandidateSelector.slots(strategy, n)
        return Triple(true, b.coerceIn(0, 5), w.coerceIn(0, 5))
    }

    suspend fun setRank(rank: Rank) = appContext.settingsStore.edit { it[RANK] = rank.id }
    suspend fun setMultipleChoice(v: Boolean) = appContext.settingsStore.edit { it[MULTIPLE_CHOICE] = v }
    suspend fun setBestCount(v: Int) = appContext.settingsStore.edit { it[BEST_COUNT] = v.coerceIn(0, 5) }
    suspend fun setWorstCount(v: Int) = appContext.settingsStore.edit { it[WORST_COUNT] = v.coerceIn(0, 5) }
    suspend fun setColorChoice(c: ColorChoice) = appContext.settingsStore.edit { it[COLOR] = c.id }
    suspend fun setShowFeedback(v: Boolean) = appContext.settingsStore.edit { it[SHOW_FEEDBACK] = v }
    suspend fun setGraphOpen(v: Boolean) = appContext.settingsStore.edit { it[GRAPH_OPEN] = v }
    suspend fun setDifficulty(d: Difficulty) = appContext.settingsStore.edit { it[DIFFICULTY] = d.id }
    suspend fun setTargetWinrate(v: Int) = appContext.settingsStore.edit { it[TARGET_WINRATE] = v.coerceIn(10, 90) }
}
