package app.stepsapp.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.stepsapp.domain.ImportMode

@Composable
fun BackupScreen(vm: BackupViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    val saveJson = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let { vm.exportTo(it, asCsv = false) } }

    val saveCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> uri?.let { vm.exportTo(it, asCsv = true) } }

    val open = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { vm.prepareImport(it) } }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("書き出し", style = MaterialTheme.typography.titleMedium)
            Text(
                "JSON は生ログまで含む完全バックアップ。CSV は分析用の3カラム。",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = { saveJson.launch("steps-backup.json") },
                enabled = !state.busy,
            ) { Text("JSON（バックアップ）") }
            OutlinedButton(
                onClick = { saveCsv.launch("steps.csv") },
                enabled = !state.busy,
            ) { Text("CSV（分析用）") }

            Text("読み込み", style = MaterialTheme.typography.titleMedium)
            Text("同じ日付がぶつかったときの扱い", style = MaterialTheme.typography.bodySmall)
            ImportMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier.selectable(
                        selected = state.mode == mode,
                        onClick = { vm.setMode(mode) },
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = state.mode == mode, onClick = { vm.setMode(mode) })
                    Text(mode.label(), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Button(
                onClick = { open.launch(arrayOf("*/*")) },
                enabled = !state.busy,
            ) { Text("ファイルを選んで読み込む") }
        }
    }

    // 誤操作を防ぐため、何件書き換わるか見せてから確定させる
    state.pending?.let { pending ->
        AlertDialog(
            onDismissRequest = { vm.cancelImport() },
            title = { Text("読み込みの確認") },
            text = {
                Text(
                    "${pending.days.size}件を読み込みました。\n" +
                        "このうち${pending.changedCount}件が書き換わります。",
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.confirmImport() }) { Text("取り込む") }
            },
            dismissButton = {
                TextButton(onClick = { vm.cancelImport() }) { Text("やめる") }
            },
        )
    }

    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = { vm.dismissMessage() },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { vm.dismissMessage() }) { Text("OK") }
            },
        )
    }
}

private fun ImportMode.label(): String = when (this) {
    ImportMode.MERGE -> "歩数の大きいほうを採用（おすすめ）"
    ImportMode.SKIP -> "既存を優先する"
    ImportMode.OVERWRITE -> "読み込んだ側で上書きする"
}
