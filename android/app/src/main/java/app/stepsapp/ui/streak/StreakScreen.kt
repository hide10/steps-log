package app.stepsapp.ui.streak

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.stepsapp.domain.CalendarMonth
import app.stepsapp.domain.DayState
import app.stepsapp.domain.Records
import app.stepsapp.ui.theme.LocalAccentColors

private val WEEKDAY = listOf("月", "火", "水", "木", "金", "土", "日")

/**
 * 自己記録。**過去の自分と比べる数字だけ**を並べる。
 *
 * 順位や平均との差は出さない。他人と比べる機能は作らない、という前提があるので、
 * 比べる相手は常に「これまでの自分」になる。
 *
 * 週と月は平均で比べる。合計で比べると、記録が多い期間ほど有利になって
 * 「よく歩いた週」ではなく「よく記録できた週」を選んでしまう。
 */
@Composable
private fun RecordCard(records: Records) {
    if (records.daysRecorded == 0) return

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("自己記録", style = MaterialTheme.typography.titleSmall)

            records.bestDay?.let { RecordRow("1日の最高", "%,d 歩".format(it.value), it.on) }
            records.bestWeek?.let {
                RecordRow("週の最高平均", "%,d 歩".format(it.value), it.on?.let { d -> "${d}の週" })
            }
            records.bestMonth?.let { RecordRow("月の最高平均", "%,d 歩".format(it.value), it.on) }
            if (records.longestStreak > 0) {
                RecordRow("最長の連続", "${records.longestStreak} 日", null)
            }
            RecordRow("累計", "%,d 歩".format(records.total), "${records.daysRecorded}日ぶん")
        }
    }
}

@Composable
private fun RecordRow(label: String, value: String, note: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Column(horizontalAlignment = Alignment.End) {
            Text(value, style = MaterialTheme.typography.bodyLarge)
            note?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 続いた日をカレンダーで見る。
 *
 * **数か月をまとめて並べる。** 知りたいのは「いま何日続いているか」より
 * 「どういうときに続き、どういうときに切れるか」なので、
 * 月をまたいだ変化が見えることを優先した。日付も読めるので、
 * 切れた日に何があったか思い出せる。
 */
@Composable
fun StreakScreen(vm: StreakViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Stat(if (state.current > 0) "${state.current}日" else "—", "連続")
            Stat(if (state.best > 0) "${state.best}日" else "—", "自己ベスト")
            Stat("${state.achieved}日", "達成した日")
        }

        RecordCard(state.records)

        Legend()

        state.months.forEach { month -> MonthGrid(month) }

        if (state.canLoadMore) {
            OutlinedButton(
                onClick = { vm.loadMore() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("もっと前を見る") }
        }

        state.bestSpan?.let { span ->
            Text(
                "いちばん長く続いたのは ${span.days}日（${span.from} 〜 ${span.to}）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        DayState.entries.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colorFor(s)),
                )
                Text(
                    " " + labelFor(s),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MonthGrid(month: CalendarMonth) {
    Column {
        Text(
            "%d年%d月".format(month.yearMonth.year, month.yearMonth.monthValue),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            WEEKDAY.forEach { w ->
                Text(
                    w,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // 7列ずつ折り返す。空白のマスも詰めずに置いて曜日を合わせる
        month.cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { cell -> DayCell(cell.date?.dayOfMonth, cell.state) }
                repeat(7 - week.size) { Box(modifier = Modifier.weight(1f)) {} }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.DayCell(day: Int?, state: DayState) {
    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (day == null) Color.Transparent else colorFor(state)),
        contentAlignment = Alignment.Center,
    ) {
        if (day != null) {
            Text(
                day.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = if (state == DayState.ACHIEVED) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun colorFor(state: DayState): Color = when (state) {
    DayState.ACHIEVED -> LocalAccentColors.current.primary
    DayState.MISSED -> MaterialTheme.colorScheme.surfaceVariant
    // 未計測は「記録が無い」ことが伝わるよう、いちばん control を弱くする
    DayState.UNMEASURED -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
}

private fun labelFor(state: DayState): String = when (state) {
    DayState.ACHIEVED -> "達成"
    DayState.MISSED -> "未達"
    DayState.UNMEASURED -> "未計測"
}
