package com.gotrainer.nine.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gotrainer.nine.game.CandidateSelector
import com.gotrainer.nine.game.ColorChoice
import com.gotrainer.nine.game.Rank
import com.gotrainer.nine.game.Strategy
import kotlinx.coroutines.flow.Flow
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
        )
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
}
