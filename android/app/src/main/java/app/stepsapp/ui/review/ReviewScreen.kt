package app.stepsapp.ui.review

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.stepsapp.domain.WeeklyReport
import app.stepsapp.domain.weeklyComparisonLabel
import app.stepsapp.domain.weeklyPeriodLabel
import app.stepsapp.domain.weeklyReportShareText

@Composable
fun ReviewScreen(modifier: Modifier = Modifier, vm: ReviewViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "過去の歩数データを週単位で確認できます。",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (state.loading) {
            item { Text("データを読み込んでいます…") }
        }
        if (!state.loading && state.reports.isEmpty()) {
            item { Text("完了した週の記録がまだありません。") }
        }
        items(state.reports, key = { it.weekStart }) { report -> ReviewCard(report) }
    }
}

@Composable
private fun ReviewCard(report: WeeklyReport) {
    val context = LocalContext.current
    val shareText = weeklyReportShareText(report)
    var showSharePreview by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(weeklyPeriodLabel(report), style = MaterialTheme.typography.titleMedium)
            Text("記録日の平均", style = MaterialTheme.typography.labelMedium)
            Text("%,d 歩/日".format(report.average), style = MaterialTheme.typography.headlineSmall)
            Text("合計 %,d 歩".format(report.total))
            Text("記録 ${report.daysRecorded}日 / 7日 · 目標達成 ${report.achieved}日")
            Text(weeklyComparisonLabel(report) ?: "前週の記録なし")
            Button(onClick = { showSharePreview = true }) { Text("記録を共有") }
        }
    }
    if (showSharePreview) {
        AlertDialog(
            onDismissRequest = { showSharePreview = false },
            title = { Text("共有プレビュー") },
            text = { Text(shareText) },
            confirmButton = {
                Button(onClick = {
                    showSharePreview = false
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(intent, "週間記録の共有"))
                }) { Text("共有する") }
            },
            dismissButton = {
                TextButton(onClick = { showSharePreview = false }) { Text("キャンセル") }
            },
        )
    }
}
