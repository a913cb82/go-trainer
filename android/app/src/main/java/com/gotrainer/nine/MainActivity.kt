package com.gotrainer.nine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.gotrainer.nine.game.GameViewModel
import com.gotrainer.nine.setup.SetupViewModel
import com.gotrainer.nine.ui.GameScreen
import com.gotrainer.nine.ui.SetupScreen
import com.gotrainer.nine.ui.goTrainerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            goTrainerTheme {
                val setupVm: SetupViewModel = viewModel()
                val setupUi by setupVm.ui.collectAsState()
                if (setupUi is SetupViewModel.Ui.Ready) {
                    val gameVm: GameViewModel = viewModel()
                    GameScreen(gameVm)
                } else {
                    SetupScreen(
                        ui = setupUi,
                        onDownload = setupVm::startDownload,
                        onRetry = setupVm::startDownload,
                        onRecheck = setupVm::recheck,
                    )
                }
            }
        }
    }
}
