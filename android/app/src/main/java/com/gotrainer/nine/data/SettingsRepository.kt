package com.gotrainer.nine.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gotrainer.nine.game.ColorChoice
import com.gotrainer.nine.game.EngineMode
import com.gotrainer.nine.game.Rank
import com.gotrainer.nine.game.Strategy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore("settings")

/** Single DataStore instance via applicationContext. */
class SettingsRepository(private val appContext: Context) {
    companion object {
        private val RANK = stringPreferencesKey("rank")
        private val N = intPreferencesKey("n")
        private val COLOR = stringPreferencesKey("color")
        private val ENGINE = stringPreferencesKey("engine")
        private val SERVER_URL = stringPreferencesKey("server_url")
        private val STRATEGY = stringPreferencesKey("strategy")
        private val SHOW_FEEDBACK = booleanPreferencesKey("show_feedback")
    }

    data class Settings(
        val rank: Rank = Rank.R10K,
        val n: Int = 5,
        val colorChoice: ColorChoice = ColorChoice.BLACK,
        val engineMode: EngineMode = EngineMode.REMOTE,
        val serverUrl: String = "http://127.0.0.1:3001",
        val strategy: Strategy = Strategy.GOOD_VS_TEMPTING,
        val showFeedback: Boolean = true,
    )

    val settings: Flow<Settings> = appContext.settingsStore.data.map { p ->
        Settings(
            rank = Rank.fromId(p[RANK] ?: "10k"),
            n = (p[N] ?: 5).let { if (it == 0 || it == 3 || it == 5) it else 5 },
            colorChoice = ColorChoice.fromId(p[COLOR] ?: "black"),
            engineMode = EngineMode.fromId(p[ENGINE] ?: "remote"),
            serverUrl = (p[SERVER_URL] ?: "http://127.0.0.1:3001").trim().trimEnd('/').ifEmpty { "http://127.0.0.1:3001" },
            strategy = Strategy.fromId(p[STRATEGY] ?: "good-vs-tempting"),
            showFeedback = p[SHOW_FEEDBACK] ?: true,
        )
    }

    suspend fun setRank(rank: Rank) = appContext.settingsStore.edit { it[RANK] = rank.id }
    suspend fun setN(n: Int) = appContext.settingsStore.edit { it[N] = n }
    suspend fun setColorChoice(c: ColorChoice) = appContext.settingsStore.edit { it[COLOR] = c.id }
    suspend fun setEngineMode(m: EngineMode) = appContext.settingsStore.edit { it[ENGINE] = m.id }
    suspend fun setServerUrl(u: String) = appContext.settingsStore.edit { it[SERVER_URL] = u }
    suspend fun setStrategy(s: Strategy) = appContext.settingsStore.edit { it[STRATEGY] = s.id }
    suspend fun setShowFeedback(v: Boolean) = appContext.settingsStore.edit { it[SHOW_FEEDBACK] = v }
}
