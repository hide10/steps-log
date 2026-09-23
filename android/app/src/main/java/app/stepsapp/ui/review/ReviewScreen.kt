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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.stepsapp.domain.WeeklyReport
import app.stepsapp.domain.weeklyReportShareText
import java.time.LocalDate

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
                "完了した週の記録を振り返れます。平均は記録した日だけで計算しています。",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (state.loading) {
            item { Text("読み込み中…") }
        }
        if (!state.loading && state.reports.isEmpty()) {
            item { Text("完了した週の記録はまだありません") }
        }
        items(state.reports, key = { it.weekStart }) { report -> ReviewCard(report) }
    }
}

@Composable
private fun ReviewCard(report: WeeklyReport) {
    val context = LocalContext.current
    val shareText = weeklyReportShareText(report)
    val end = LocalDate.parse(report.weekStart).plusDays(6)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("${report.weekStart}〜$end", style = MaterialTheme.typography.titleMedium)
            Text("1日平均 %,d 歩".format(report.average), style = MaterialTheme.typography.headlineSmall)
            Text("合計 %,d 歩 · 7日中%d日の記録 · 目標達成 %d日".format(
                report.total, report.daysRecorded, report.achieved,
            ))
            report.diff?.let { diff ->
                Text(
                    when {
                        diff > 0 -> "前週より1日平均 %,d 歩多い".format(diff)
                        diff < 0 -> "前週より1日平均 %,d 歩少ない".format(-diff)
                        else -> "前週と1日平均が同じ"
                    },
                )
            }
            Text("共有する文章", style = MaterialTheme.typography.labelLarge)
            Text(shareText, style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(intent, "振り返りを共有"))
                },
            ) { Text("この週を共有") }
        }
    }
}
