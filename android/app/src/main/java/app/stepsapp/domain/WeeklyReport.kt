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

/** SNS に渡す前に画面で確認できる、週と集計条件を含む文章。 */
fun weeklyReportShareText(report: WeeklyReport): String {
    val end = LocalDate.parse(report.weekStart).plusDays(6)
    val comparison = report.diff?.let {
        when {
            it > 0 -> "前週より1日平均 %,d 歩多い".format(it)
            it < 0 -> "前週より1日平均 %,d 歩少ない".format(-it)
            else -> "前週と1日平均が同じ"
        }
    }
    return buildList {
        add("歩数の振り返り ${report.weekStart}〜$end")
        add("合計 %,d 歩".format(report.total))
        add("記録した%d日の平均 %,d 歩（7日中%d日の記録）".format(
            report.daysRecorded, report.average, report.daysRecorded,
        ))
        add("目標達成 ${report.achieved}日")
        comparison?.let(::add)
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
