package app.stepsapp.domain

import java.time.LocalDate

/**
 * 1週間のまとめ。
 *
 * @param weekStart    その週の月曜日
 * @param total        合計歩数
 * @param average      記録がある日だけを分母にした平均
 * @param daysRecorded 記録がある日数
 * @param achieved     目標を達成した日数
 * @param diff         前の週の平均との差。前の週に記録が無ければ null
 */
data class WeeklyReport(
    val weekStart: String,
    val total: Long,
    val average: Long,
    val daysRecorded: Int,
    val achieved: Int,
    val diff: Long?,
)

/**
 * 直前の1週間（月曜〜日曜）のまとめを作る。副作用を持たない純粋関数。
 *
 * **平均の分母は記録がある日だけ**（[aggregate] と同じ規則）。
 * 未計測の日を0歩として平均を下げない。
 *
 * **達成の判定はその日に有効だった目標で行う**（[GoalHistory]）。
 *
 * @param today 今日。この日が含まれる週の**ひとつ前**の週をまとめる
 * @return 記録が1日も無い週なら null
 */
fun weeklyReport(
    stepsByDate: Map<String, Long>,
    today: LocalDate,
    goals: GoalHistory,
): WeeklyReport? = weeklyReportFor(stepsByDate, weekStart(today).minusWeeks(1), goals)

/** 記録のある完了済みの週だけを、新しい順に返す。 */
fun completedWeeklyReports(
    stepsByDate: Map<String, Long>,
    today: LocalDate,
    goals: GoalHistory,
): List<WeeklyReport> {
    val thisWeek = weekStart(today)
    return stepsByDate.keys.asSequence()
        .map { weekStart(LocalDate.parse(it)) }
        .filter { it < thisWeek }
        .distinct()
        .sortedDescending()
        .mapNotNull { weeklyReportFor(stepsByDate, it, goals) }
        .toList()
}

private fun weeklyReportFor(
    stepsByDate: Map<String, Long>,
    start: LocalDate,
    goals: GoalHistory,
): WeeklyReport? {
    val end = start.plusDays(6)

    val days = daysIn(stepsByDate, start, end)
    if (days.isEmpty()) return null

    val previous = daysIn(stepsByDate, start.minusWeeks(1), start.minusDays(1))
    val total = days.values.sum()

    return WeeklyReport(
        weekStart = start.toString(),
        total = total,
        average = total / days.size,
        daysRecorded = days.size,
        achieved = days.count { isAchievedOn(it.value, it.key, goals) },
        diff = if (previous.isEmpty()) {
            null
        } else {
            total / days.size - previous.values.sum() / previous.size
        },
    )
}

/** 年をまたぐ週では、終了日にも年を付けて期間を曖昧にしない。 */
fun weeklyPeriodLabel(report: WeeklyReport, separator: String = " - "): String {
    val start = LocalDate.parse(report.weekStart)
    val end = start.plusDays(6)
    val from = "%04d/%02d/%02d".format(start.year, start.monthValue, start.dayOfMonth)
    val to = if (start.year == end.year) {
        "%02d/%02d".format(end.monthValue, end.dayOfMonth)
    } else {
        "%04d/%02d/%02d".format(end.year, end.monthValue, end.dayOfMonth)
    }
    return "$from$separator$to"
}

/** 比較は記録日の1日平均同士。前週に記録がなければ null。 */
fun weeklyComparisonLabel(report: WeeklyReport): String? = report.diff?.let {
    when {
        it > 0 -> "前週比 +%,d歩/日".format(it)
        it < 0 -> "前週比 -%,d歩/日".format(-it)
        else -> "前週比 変化なし"
    }
}

/** 共有前のプレビューと送信本文に、同じ文章を使う。 */
fun weeklyReportShareText(report: WeeklyReport): String {
    val comparison = weeklyComparisonLabel(report)?.let { "（$it）" }.orEmpty()
    return buildList {
        add("【週間歩数記録】${weeklyPeriodLabel(report, "〜")}（記録${report.daysRecorded}/7日）")
        add("1日平均：%,d歩%s".format(report.average, comparison))
        add("合計：%,d歩".format(report.total))
        add("#歩数記録")
    }.joinToString("\n")
}

private fun daysIn(
    stepsByDate: Map<String, Long>,
    from: LocalDate,
    to: LocalDate,
): Map<String, Long> {
    val fromText = from.toString()
    val toText = to.toString()
    return stepsByDate.filterKeys { it in fromText..toText }
}

/**
 * 週報の文言。
 *
 * **記録できた日数を必ず添える。** 3日しか測れていない週の平均を
 * 「1日あたり12,000歩」とだけ言われると、実態より良く見えてしまう。
 */
fun weeklyReportText(report: WeeklyReport): Pair<String, String> {
    val head = "先週は1日あたり %,d 歩".format(report.average)
    val parts = buildList {
        add("%d日中%d日の記録".format(7, report.daysRecorded))
        add("目標達成 %d日".format(report.achieved))
        report.diff?.let {
            add(
                when {
                    it > 0 -> "前週より %,d 歩多い".format(it)
                    it < 0 -> "前週より %,d 歩少ない".format(-it)
                    else -> "前週とほぼ同じ"
                },
            )
        }
    }
    return head to parts.joinToString("、")
}
