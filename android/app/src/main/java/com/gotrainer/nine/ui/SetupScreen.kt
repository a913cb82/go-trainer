package com.gotrainer.nine.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gotrainer.nine.engine.ModelManager
import com.gotrainer.nine.setup.SetupViewModel

fun formatMB(bytes: Long): String {
    val mb = bytes / 1_048_576.0
    return if (mb >= 100) "${mb.toInt()} MB" else "%.1f MB".format(mb)
}

@Composable
fun SetupScreen(
    ui: SetupViewModel.Ui,
    onDownload: () -> Unit,
    onRetry: () -> Unit,
    onRecheck: () -> Unit,
    onUseRemote: (String) -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Go 9×9 Trainer", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(
            "One-time engine setup",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "The game needs KataGo's neural networks to play and judge moves. " +
                "They download once (use Wi-Fi — it's a big download), stay on your device, " +
                "and the game runs fully offline afterwards. Interrupted downloads resume.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 560.dp),
        )

        when (ui) {
            is SetupViewModel.Ui.Checking -> {
                CircularProgressIndicator()
                Text("Checking engine files…", style = MaterialTheme.typography.bodyMedium)
            }
            is SetupViewModel.Ui.Missing -> {
                var showRemote by remember { mutableStateOf(false) }
                var remoteUrl by remember { mutableStateOf(ui.serverUrl) }
                FileList(rows = ui.rows)
                Button(onClick = onDownload, modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Download engine files")
                }
                if (!showRemote) {
                    OutlinedButton(onClick = { showRemote = true }) {
                        Text("Or play via home server")
                    }
                } else {
                    ElevatedCard(modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "Run the home server (server/npm run dev) and expose it " +
                                    "via adb reverse or LAN, then connect:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedTextField(
                                value = remoteUrl,
                                onValueChange = { remoteUrl = it },
                                label = { Text("Server URL") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            Button(onClick = { onUseRemote(remoteUrl) }, modifier = Modifier.fillMaxWidth()) {
                                Text("Connect")
                            }
                        }
                    }
                }
                OutlinedButton(onClick = onRecheck) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Recheck files")
                }
            }
            is SetupViewModel.Ui.Downloading -> {
                val frac = ui.totalBytes?.let { if (it > 0) ui.doneBytes.toFloat() / it else null }
                val overallFrac = if (ui.overallTotal > 0) ui.overallDone.toFloat() / ui.overallTotal else null
                ElevatedCard(modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "File ${ui.fileIndex + 1} of ${ui.fileCount} · ${ui.fileName}",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        if (frac != null) LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
                        else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            if (ui.totalBytes != null) {
                                "${formatMB(ui.doneBytes)} of ${formatMB(ui.totalBytes)}"
                            } else {
                                "${formatMB(ui.doneBytes)} downloaded"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (overallFrac != null) {
                            Text(
                                "Overall ${formatMB(ui.overallDone)} of ${formatMB(ui.overallTotal)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            LinearProgressIndicator(progress = { overallFrac }, modifier = Modifier.fillMaxWidth())
                        }
                        Text(
                            "Keep the app open — downloads resume after interruptions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            is SetupViewModel.Ui.Failed -> {
                ElevatedCard(
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text(
                            ui.error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
                Button(onClick = onRetry, modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                    Text(ui.retryLabel)
                }
                OutlinedButton(onClick = onRecheck) {
                    Text("Recheck files")
                }
            }
            is SetupViewModel.Ui.Ready -> {
                CircularProgressIndicator()
                Text("Engine ready — starting game…", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun FileList(rows: List<ModelManager.FileRow>) {
    ElevatedCard(modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            for (row in rows) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (row.present) Icons.Filled.CheckCircle else Icons.Filled.Download,
                        contentDescription = null,
                        tint = if (row.present) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                    Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(row.file.displayName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (row.present) "${formatMB(row.bytes)} · on device"
                            else "${row.file.detail} · ~${formatMB(row.file.approxSize)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                    Text("Engine binary + config", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Bundled with the app · staged automatically",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
