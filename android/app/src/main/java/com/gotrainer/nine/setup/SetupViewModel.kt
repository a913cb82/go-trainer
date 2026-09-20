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
import kotlinx.coroutines.launch

/** One-time engine setup gate: the game cannot run until KataGo's files are on device. */
class SetupViewModel(app: Application) : AndroidViewModel(app) {
    companion object {
        private const val TAG = "Setup"
    }

    private val appContext = app.applicationContext
    private val manager = ModelManager(appContext.filesDir)
    private val katago = KataGoGtpEngine(appContext)
    private var job: Job? = null

    sealed interface Ui {
        data object Checking : Ui
        data class Missing(val rows: List<ModelManager.FileRow>) : Ui
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
                _ui.value = Ui.Missing(rows)
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
