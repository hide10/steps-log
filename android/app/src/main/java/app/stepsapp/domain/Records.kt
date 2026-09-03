package app.stepsapp.domain

/** 自己記録の1項目。日付は「いつの記録か」を添えるためのもの。 */
data class Record(val value: Long, val on: String?)

/**
 * 自己記録。**過去の自分と比べるための数字**しか持たない。
 *
 * 他人と比べる機能は作らない、という前提があるので、順位や偏差ではなく
 * 「これまでの自分の最高」だけを並べる。
 */
data class Records(
    /** 1日でいちばん歩いた記録。 */
    val bestDay: Record?,
    /** 週の平均でいちばん高かった記録。日付はその週の月曜日。 */
    val bestWeek: Record?,
    /** 月の平均でいちばん高かった記録。日付は YYYY-MM。 */
    val bestMonth: Record?,
    /** 最長の連続達成日数。 */
    val longestStreak: Int,
    /** 記録がある日の合計歩数。 */
    val total: Long,
    /** 記録がある日数。0歩の日も数える。 */
    val daysRecorded: Int,
) {
    companion object {
        val EMPTY = Records(null, null, null, 0, 0, 0)
    }
}

/**
 * 自己記録を数える。副作用を持たない純粋関数。
 *
 * **平均は記録がある日だけを分母にする**（[aggregate] と同じ規則）。
 * 未計測の日を0歩として平均を下げると、記録の意味が変わってしまう。
 *
 * @param stepsByDate 日付 -> 歩数（記録がある日だけ）
 * @param goals       目標の履歴。連続日数の判定に使う
 */
fun records(stepsByDate: Map<String, Long>, goals: GoalHistory): Records {
    if (stepsByDate.isEmpty()) return Records.EMPTY

    val best = stepsByDate.maxByOrNull { it.value }
    val weeks = aggregate(stepsByDate, Period.WEEK).maxByOrNull { it.average }
    val months = aggregate(stepsByDate, Period.MONTH).maxByOrNull { it.average }

    return Records(
        bestDay = best?.let { Record(it.value, it.key) },
        bestWeek = weeks?.let { Record(Math.round(it.average), it.key) },
        bestMonth = months?.let { Record(Math.round(it.average), it.key) },
        longestStreak = longestStreak(stepsByDate, goals),
        total = stepsByDate.values.sum(),
        daysRecorded = stepsByDate.size,
    )
}

/** 更新された記録の種類。祝うときに何と言うかを決めるのに使う。 */
enum class RecordKind {
    /** 1日の最高歩数 */
    BEST_DAY,

    /** 最長の連続日数 */
    LONGEST_STREAK,
}

/**
 * 今日で自己記録を更新したかを見る。
 *
 * **週や月の平均は対象にしない。** 期間の途中で「いま最高」と言われても、
 * 残りの日で下がるので祝いようがない。確定した数字だけを祝う。
 *
 * **同値は更新とみなさない。** 前回と同じ歩数で「新記録」と言われると白々しい。
 *
 * @param previousBestDay    今日より前の1日の最高歩数
 * @param previousLongest    今日より前の最長連続日数
 * @param todaySteps         今日の歩数
 * @param streakIncludingToday 今日を含めた連続日数
 */
fun newRecordsToday(
    previousBestDay: Long,
    previousLongest: Int,
    todaySteps: Long,
    streakIncludingToday: Int,
): List<RecordKind> = buildList {
    if (todaySteps > previousBestDay && previousBestDay > 0) add(RecordKind.BEST_DAY)
    if (streakIncludingToday > previousLongest && previousLongest > 0) {
        add(RecordKind.LONGEST_STREAK)
    }
}

/** 通知に出す文言。 */
fun recordNoticeText(kind: RecordKind, steps: Long, streakDays: Int): Pair<String, String> =
    when (kind) {
        RecordKind.BEST_DAY -> "自己新記録" to "%,d 歩。1日の最高を更新しました".format(steps)
        RecordKind.LONGEST_STREAK -> "自己新記録" to "%d 日連続。最長を更新しました".format(streakDays)
    }
