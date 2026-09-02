package app.stepsapp.ui.body

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.stepsapp.domain.Trend
import app.stepsapp.domain.VitalKind
import app.stepsapp.domain.formatDuration
import app.stepsapp.domain.formatVital
import app.stepsapp.domain.isMeaningful
import app.stepsapp.ui.common.TrendChart

/**
 * 体重・睡眠と、そのほかの健康データ。
 *
 * **見る頻度に合わせて扱いを変える。** 体重と睡眠は毎日気にするものなので
 * グラフ付きで大きく、心拍や血圧はたまに確認できればよいので値と傾向の1行にし、
 * タップで初めてグラフを開く。全部をグラフにすると縦に伸びて目的の項目を探しにくい。
 */
@Composable
fun BodyScreen(vm: BodyViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val nothing = state.weightLatest == null &&
            state.sleepLatest == null &&
            state.vitals.isEmpty()
        if (nothing) {
            Text(
                "まだ記録がありません。Health Connect を許可すると、" +
                    "体重・睡眠・心拍などを取り込みます。",
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Column
        }

        state.weightLatest?.let { kg ->
            TrendCard(
                title = "体重",
                headline = "%.1f %s".format(kg, state.weightUnit.suffix),
                // 体重は日々1kg近く揺れるので 0.3kg 未満は変化と言わない
                note = trendNote(state.weightTrend.change, 0.3) {
                    "%.1f %s".format(it, state.weightUnit.suffix)
                },
                trend = state.weightTrend,
                format = { "%.1f".format(it) },
            )
        }

        state.sleepLatest?.let { minutes ->
            TrendCard(
                title = "睡眠" + (state.sleepDate?.let { " ・ $it" } ?: ""),
                headline = formatDuration(minutes),
                note = trendNote(state.sleepTrend.change, 15.0) { formatDuration(it.toLong()) },
                trend = state.sleepTrend,
                format = { formatDuration(it.toLong()) },
            )
        }

        if (state.vitals.isNotEmpty()) {
            Text("そのほか", style = MaterialTheme.typography.titleSmall)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    state.vitals.forEachIndexed { index, row ->
                        if (index > 0) HorizontalDivider()
                        VitalRowItem(
                            row = row,
                            expanded = state.expanded == row.kind,
                            trend = state.expandedTrend,
                            onClick = { vm.toggle(row.kind) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VitalRowItem(
    row: VitalRow,
    expanded: Boolean,
    trend: Trend,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(row.kind.label, style = MaterialTheme.typography.bodyMedium)
                Text(row.date, style = MaterialTheme.typography.labelSmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${formatVital(row.kind, row.value)} ${row.kind.unit}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = arrowFor(row),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            TrendChart(
                trend = trend,
                modifier = Modifier.padding(top = 8.dp),
                format = { formatVital(row.kind, it) },
            )
        }
    }
}

/** 増減の向きだけ。数値まで並べると一覧が読みにくくなる。 */
private fun arrowFor(row: VitalRow): String {
    // 単位ごとに「意味のある差」が違う。心拍の1bpm と体脂肪率の1% は重みが別
    val threshold = when (row.kind) {
        VitalKind.BODY_FAT, VitalKind.OXYGEN_SATURATION -> 0.5
        VitalKind.DISTANCE -> 0.3
        VitalKind.FLOORS_CLIMBED -> 1.0
        else -> 2.0
    }
    if (!isMeaningful(row.change, threshold)) return "→"
    return if (row.change!! > 0) "↗" else "↘"
}

@Composable
private fun TrendCard(
    title: String,
    headline: String,
    note: String,
    trend: Trend,
    format: (Double) -> String,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column {
                    Text(title, style = MaterialTheme.typography.labelMedium)
                    Text(headline, style = MaterialTheme.typography.headlineSmall)
                }
                Text(note, style = MaterialTheme.typography.bodySmall)
            }
            TrendChart(
                trend = trend,
                modifier = Modifier.padding(top = 10.dp),
                format = format,
            )
        }
    }
}

/** 増減を言葉にする。意味のない小さな揺れは「横ばい」と言い切る。 */
private fun trendNote(
    change: Double?,
    threshold: Double,
    format: (Double) -> String,
): String = when {
    !isMeaningful(change, threshold) -> "このところ横ばい"
    change!! > 0 -> "増加傾向 (+${format(change)})"
    else -> "減少傾向 (-${format(-change)})"
}
