package app.stepsapp.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import app.stepsapp.ui.theme.LocalAccentColors
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.stepsapp.data.local.HealthConnectReader
import app.stepsapp.domain.Bucket
import app.stepsapp.domain.HourlySteps
import app.stepsapp.domain.Period
import app.stepsapp.domain.RecordKind
import app.stepsapp.domain.busiestHoursText
import app.stepsapp.domain.achievedRatio
import app.stepsapp.domain.isAchieved
import app.stepsapp.ui.common.GoalRing

@Composable
fun HomeScreen(
    onOpenStreak: () -> Unit = {},
    onOpenShare: () -> Unit = {},
    vm: HomeViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val hcLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { vm.refreshHealthConnect() }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> vm.onPermissionResult(granted) }

    LaunchedEffect(Unit) {
        vm.refreshGoal()
        vm.refreshHealthConnect()
        val needed = Manifest.permission.ACTIVITY_RECOGNITION
        val already = ContextCompat.checkSelfPermission(context, needed) ==
            PackageManager.PERMISSION_GRANTED
        if (already) {
            vm.onPermissionResult(true)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            launcher.launch(needed)
        } else {
            vm.onPermissionResult(true)
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 共有は歩数リストの下に置くと、スクロールしないと見つからなかった。
            // リング上の余白に逃がせば、集計を押し出さずに常に見える
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onOpenShare) {
                    Icon(Icons.Filled.Share, contentDescription = "画像にして共有")
                }
            }

            GoalRing(
                steps = state.todaySteps,
                goalSteps = state.goal.dailySteps,
                ratio = achievedRatio(state.todaySteps, state.goal),
                achieved = isAchieved(state.todaySteps, state.goal),
            )

            StatRow(state, onOpenStreak)

            // 過去の自分を超えた日だけ出す。毎日出ると意味が薄れる
            if (state.newRecords.isNotEmpty()) {
                NewRecordBanner(state.newRecords)
            }

            // 「今日のどこで歩いたか」は今日についてしか言えないので、日表示のときだけ出す
            if (state.period == Period.DAY && !state.hourly.isEmpty) {
                HourlyChart(state.hourly)
            }

            PeriodSelector(selected = state.period, onSelect = { vm.setPeriod(it) })

            state.comparison?.let { diff -> ComparisonLine(state.period, diff) }

            BucketList(
                period = state.period,
                buckets = state.buckets,
                goal = state.goal.dailySteps,
                canLoadMore = state.canLoadMore,
                onLoadMore = { vm.loadMoreBuckets() },
            )

            // 以下は普段は見えなくてよいもの。主役の集計を押し出さないよう下に置く
            if (!state.sensorAvailable) {
                Warning("この端末には歩数センサーがありません")
            }
            if (!state.permissionGranted) {
                Warning("「身体活動」の権限が必要です")
                // 一度「許可しない」を選ぶと、Android は二度目のダイアログを出さない。
                // 文だけ残ると手詰まりになるので、設定への入口を必ず添える
                OutlinedButton(onClick = { openAppSettings(context) }) {
                    Text("設定を開いて許可する")
                }
            }
            if (state.healthConnectAvailable && !state.healthConnectGranted) {
                Text(
                    "Health Connect を許可すると、アプリが止まっていた間の歩数も拾えます",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = { hcLauncher.launch(HealthConnectReader.PERMISSIONS) }) {
                    Text("Health Connect を許可する")
                }
            }

            TextButton(onClick = { vm.sync() }, enabled = !state.syncing) {
                Text(if (state.syncing) "更新中…" else "いま読み取る")
            }
        }
    }
}

@Composable
private fun StatRow(state: HomeUiState, onOpenStreak: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 連続と自己ベストは押すと記録の画面へ行ける。**囲って矢印を出す。**
        // 数字が並んでいるだけでは、どれが押せるのか見て分からなかった
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                label = "連続",
                value = if (state.streak > 0) "${state.streak}日" else "—",
                // 今日を含んでいるのかどうかが数字だけでは分からない。
                // 「1日」と出ていて今日が未達のときに、いちばん誤解を招く
                note = if (isAchieved(state.todaySteps, state.goal)) "今日も達成" else "今日はまだ",
                modifier = Modifier.weight(1f),
                onClick = onOpenStreak,
            )
            StatCard(
                label = "自己ベスト",
                value = if (state.bestStreak > 0) "${state.bestStreak}日" else "—",
                note = null,
                modifier = Modifier.weight(1f),
                onClick = onOpenStreak,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Stat("距離", "%.1f %s".format(state.distance, state.distanceUnit.suffix))
            Stat("カロリー", state.calories?.let { "%.0f".format(it) } ?: "—")
        }
    }
}

/** 押すと先がある数字。囲いと矢印で「押せる」ことを見せる。 */
@Composable
private fun StatCard(
    label: String,
    value: String,
    note: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(modifier = modifier.clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall)
                Text(
                    "›",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(value, style = MaterialTheme.typography.titleMedium)
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

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun PeriodSelector(selected: Period, onSelect: (Period) -> Unit) {
    val options = listOf(
        Period.DAY to "日",
        Period.WEEK to "週",
        Period.MONTH to "月",
        Period.YEAR to "年",
    )
    SingleChoiceSegmentedButtonRow {
        options.forEachIndexed { index, (period, label) ->
            SegmentedButton(
                selected = selected == period,
                onClick = { onSelect(period) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(label) }
        }
    }
}

@Composable
private fun ComparisonLine(period: Period, diff: Double) {
    val unit = when (period) {
        Period.WEEK -> "先週"
        Period.MONTH -> "先月"
        Period.YEAR -> "去年"
        Period.DAY -> return
    }
    val rounded = kotlin.math.round(diff).toLong()
    val text = when {
        rounded > 0 -> "$unit の同じ時期より 1日あたり %,d 歩 多い".format(rounded)
        rounded < 0 -> "$unit の同じ時期より 1日あたり %,d 歩 少ない".format(-rounded)
        else -> "$unit の同じ時期とほぼ同じ"
    }
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

/** 今日で自己記録を更新したことを伝える。 */
@Composable
private fun NewRecordBanner(kinds: List<RecordKind>) {
    val accent = LocalAccentColors.current
    val what = kinds.joinToString("と") {
        when (it) {
            RecordKind.BEST_DAY -> "1日の歩数"
            RecordKind.LONGEST_STREAK -> "連続日数"
        }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "自己新記録",
                style = MaterialTheme.typography.titleSmall,
                color = accent.primary,
            )
            Text(
                "${what}の記録を更新しました",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * 1日のどの時間帯に歩いたか。
 *
 * **目盛りの数字は出さない。** 何時にどれだけ歩いたかまで読めるようにすると、
 * 生活のパターンがそのまま出る。ここで知りたいのは「朝に歩く人か、
 * 夜に歩く人か」という形のほうなので、縦軸は最大値で正規化するだけにする。
 */
@Composable
private fun HourlyChart(hourly: HourlySteps) {
    val accent = LocalAccentColors.current
    val max = hourly.byHour.max().coerceAtLeast(1)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("歩いた時間帯", style = MaterialTheme.typography.titleSmall)

            Row(
                modifier = Modifier.fillMaxWidth().height(64.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                hourly.byHour.forEach { steps ->
                    val ratio = (steps.toFloat() / max).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            // 歩いていない時間帯も、床として細く残す。
                            // 棒が消えると横軸のどこを見ているのか分からなくなる
                            .fillMaxHeight(if (steps > 0) ratio.coerceAtLeast(0.06f) else 0.03f)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (steps > 0) accent.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                            ),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                listOf("0", "6", "12", "18", "23").forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            busiestHoursText(hourly)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BucketList(
    period: Period,
    buckets: List<Bucket>,
    goal: Long,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
) {
    if (buckets.isEmpty()) {
        Text("まだ記録がありません", style = MaterialTheme.typography.bodySmall)
        return
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            // **表示している値そのものの最大値を使う。**
            // 週/月/年 は平均を出しているのに合計で最大値を取ると桁が合わず、
            // バーがほとんど伸びなくなる。
            // 目標も基準に含める（全部未達のとき小さな値が満杯に見えるのを防ぐ）。
            val values = buckets.map { valueOf(period, it) }
            val max = maxOf(values.max(), goal).coerceAtLeast(1)
            buckets.forEach { b ->
                BucketRow(period, b, goal, max)
            }
            // 全部いっぺんに並べると、日で見たとき1,200行を作ることになる。
            // 画面に入らない行まで作るぶんだけ切り替えが遅くなるので、少しずつ足す
            if (canLoadMore) {
                TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) {
                    Text("もっと見る")
                }
            }
        }
    }
}

/**
 * 1行 = 日付 + 目標に対するバー + 数値。
 *
 * **数値を残したままバーを足す。** 数値だけでは「目標に届いたか」「特別に
 * 多い日はどれか」を読み取るのに全部読む必要があり、バーだけでは正確さが失われる。
 * 行の高さは変わらないので、表示できる件数も減らない。
 */
@Composable
private fun BucketRow(period: Period, b: Bucket, goal: Long, max: Long) {
    val value = valueOf(period, b)
    val achieved = value >= goal
    val accent = LocalAccentColors.current
    // **達成した日をアクセント色、未達をくすんだ色にする。**
    // リングでは「同じ色相の濃淡」で正しかったが、バーが縦に並ぶと
    // 濃淡の差は見分けがつかない。ここでは彩度で差を付ける。
    val barColor = if (achieved) {
        accent.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = labelOf(period, b.key),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(86.dp),
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
                .height(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            // 0 のときはバーを出さない。0 は 0 と分かるべきで、
            // 最小幅を保証すると「少しは歩いた」ように見えてしまう
            if (value > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((value.toFloat() / max).coerceIn(0.02f, 1f))
                        .height(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(barColor),
                )
            }
            // 目標の位置に印を置き、届いたかを一目で分かるようにする
            if (goal in 1..max) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(goal.toFloat() / max)
                        .height(16.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(16.dp)
                            .background(MaterialTheme.colorScheme.onSurface),
                    )
                }
            }
        }

        Text(
            text = "%,d".format(value),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(64.dp),
            textAlign = TextAlign.End,
        )
    }
}

/**
 * その期間で見せる値。
 *
 * 日は合計（＝その日の歩数）、週/月/年 は1日あたりの平均。
 * **目標は1日あたりの値なので、平均と比べれば期間が違っても同じ物差しで見られる。**
 */
private fun valueOf(period: Period, b: Bucket): Long =
    if (period == Period.DAY) b.total else Math.round(b.average)

private val WEEKDAY = listOf("月", "火", "水", "木", "金", "土", "日")

/** 日表示のときは曜日も添える。土日にどれだけ歩いたかが読み取りやすくなる。 */
private fun labelOf(period: Period, key: String): String = when (period) {
    Period.DAY -> runCatching {
        val d = java.time.LocalDate.parse(key)
        "%s(%s)".format(key.substring(5), WEEKDAY[d.dayOfWeek.value - 1])
    }.getOrDefault(key)
    else -> key
}

/** アプリの「権限」設定を開く。ダイアログが出なくなったときの逃げ道。 */
private fun openAppSettings(context: android.content.Context) {
    val intent = android.content.Intent(
        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        android.net.Uri.fromParts("package", context.packageName, null),
    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

@Composable
private fun Warning(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
}
