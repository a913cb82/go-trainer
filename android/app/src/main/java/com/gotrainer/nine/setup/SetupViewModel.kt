package com.gotrainer.nine.setup

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gotrainer.nine.engine.KataGoGtpEngine
import com.gotrainer.nine.engine.ModelManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One-time engine setup gate: the game cannot run until KataGo's files are on device. */
class SetupViewModel(app: Application) : AndroidViewModel(app) {
    companion object {
        private const val TAG = "Setup"
    }

    private val appContext = app.applicationContext
    private val manager = ModelManager(appContext.filesDir)
    private val katago = KataGoGtpEngine(appContext)
    private var job: Job? = null
    private val settings = com.gotrainer.nine.data.SettingsRepository(appContext)

    sealed interface Ui {
        data object Checking : Ui
        data class Missing(val rows: List<ModelManager.FileRow>, val serverUrl: String) : Ui
        data class Downloading(
            val fileIndex: Int,
            val fileCount: Int,
            val fileName: String,
            val doneBytes: Long,
            val totalBytes: Long?,
            val overallDone: Long,
            val overallTotal: Long,
        ) : Ui
        data class Failed(val error: String, val retryLabel: String = "Retry download") : Ui
        data object Ready : Ui
    }

    private val _ui = MutableStateFlow<Ui>(Ui.Checking)
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    init {
        recheck()
    }

    /** Stage the APK-bundled binary/cfg, then report what's still missing. Loud on failure. */
    fun recheck() {
        job?.cancel()
        job = viewModelScope.launch {
            _ui.value = Ui.Checking
            try {
                katago.stageIfNeeded()
            } catch (e: Exception) {
                Log.e(TAG, "staging failed", e)
                _ui.value = Ui.Failed(e.message ?: "Engine binary missing from the APK", "Retry")
                return@launch
            }
            val rows = manager.rows()
            if (rows.all { it.present }) {
                _ui.value = Ui.Ready
            } else {
                val s = settings.settings.first()
                _ui.value = Ui.Missing(rows, s.serverUrl)
            }
        }
    }

    /**
     * Skip the downloads and play via the home server instead (adb reverse or
     * LAN). Probes /health first — loud on failure, no silent fallback.
     */
    fun useRemote(url: String) {
        job?.cancel()
        job = viewModelScope.launch {
            _ui.value = Ui.Checking
            val clean = url.trim().trimEnd('/').ifEmpty { "http://127.0.0.1:3001" }
            try {
                val ok = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val conn = java.net.URL("$clean/health").openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    try {
                        conn.responseCode in 200..299
                    } finally {
                        conn.disconnect()
                    }
                }
                if (!ok) throw java.io.IOException("server unhealthy")
                settings.setEngineMode(com.gotrainer.nine.game.EngineMode.REMOTE)
                settings.setServerUrl(clean)
                _ui.value = Ui.Ready
            } catch (e: Exception) {
                Log.e(TAG, "remote probe failed", e)
                _ui.value = Ui.Failed("Server unreachable at $clean — start it (server/npm run dev) and check adb reverse / LAN", "Retry")
            }
        }
    }

    fun startDownload() {
        job?.cancel()
        job = viewModelScope.launch {
            manager.downloadMissing()
                .catch { e ->
                    Log.e(TAG, "download failed", e)
                    _ui.value = Ui.Failed(e.message ?: "Download failed")
                }
                .collect { state ->
                    _ui.value = when (state) {
                        is ModelManager.DownloadState.Downloading -> Ui.Downloading(
                            state.fileIndex, state.fileCount, state.fileName,
                            state.doneBytes, state.totalBytes, state.overallDone, state.overallTotal,
                        )
                        is ModelManager.DownloadState.Failed -> Ui.Failed(state.error)
                        ModelManager.DownloadState.Done -> Ui.Ready
                    }
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        job?.cancel()
    }
}
