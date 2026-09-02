package app.stepsapp.domain

import java.time.LocalDate

/**
 * 目標を達成した日が何日連続しているか。
 *
 * **未計測の日（レコードが無い日）はストリークを切らない。** 歩いていないと確定した
 * わけではなく、端末を持たずに過ごしただけかもしれないため。
 * 一方、記録があって目標に届かなかった日は明確な未達なので切る。
 * この扱いは「レコードが無い日 = 未計測」「0歩の記録 = 実際に0歩」という
 * スキーマ上の区別に沿っている。
 *
 * **当日は未達でも切らない。** まだ歩く時間が残っているため、
 * 「今日まだ達成していない」だけでストリークが 0 に見えるのは酷。
 * 当日が達成済みなら数に含める。
 *
 * **判定はその日に有効だった目標で行う。** 目標を上げたときに、
 * 過去の達成済みの日が未達成に変わってはいけない（[GoalHistory]）。
 *
 * @param stepsByDate 日付 -> その日の歩数（記録がある日だけを入れる）
 * @param today       今日の日付
 * @param goals       目標の履歴
 */
fun currentStreak(
    stepsByDate: Map<String, Long>,
    today: LocalDate,
    goals: GoalHistory,
): Int {
    // 最も古い記録より前まで遡ったら終わり。ここを毎回 keys の全走査で
    // 判定していたころは、記録が増えるほど遡りが二乗で効いて重くなっていた
    val oldest = stepsByDate.keys.minOrNull() ?: return 0

    var streak = 0
    var date = today

    // 当日が未達なら前日から数え始める（当日はまだ挽回できるので切らない）
    val todaySteps = stepsByDate[today.toString()]
    if (todaySteps == null || !isAchievedOn(todaySteps, today.toString(), goals)) {
        date = today.minusDays(1)
    }

    while (true) {
        val key = date.toString()
        if (key < oldest) return streak

        val steps = stepsByDate[key]
        when {
            // 未計測の日は判定を保留して遡り続ける
            steps == null -> Unit
            isAchievedOn(steps, key, goals) -> streak++
            // 記録があって未達なら、そこでストリークは途切れる
            else -> return streak
        }
        date = date.minusDays(1)
    }
}

/** 目標がひとつしか無い場合。全期間をその目標で判定する。 */
fun currentStreak(stepsByDate: Map<String, Long>, today: LocalDate, goal: Goal): Int =
    currentStreak(stepsByDate, today, GoalHistory.single(goal))

/** これまでの最長ストリーク。自己ベストとして見せる。 */
fun longestStreak(stepsByDate: Map<String, Long>, goals: GoalHistory): Int {
    val achieved = stepsByDate.filterKeys { isAchievedOn(stepsByDate.getValue(it), it, goals) }
        .keys.sorted()
    if (achieved.isEmpty()) return 0

    var best = 1
    var run = 1
    for (i in 1 until achieved.size) {
        val prev = LocalDate.parse(achieved[i - 1])
        val cur = LocalDate.parse(achieved[i])
        // 間にある未計測の日はストリークを切らないので、
        // 「間の日がすべて未計測または達成」なら連続とみなす
        val gapAllUnmeasured = generateSequence(prev.plusDays(1)) { it.plusDays(1) }
            .takeWhile { it < cur }
            .all { stepsByDate[it.toString()] == null }
        run = if (gapAllUnmeasured) run + 1 else 1
        best = maxOf(best, run)
    }
    return best
}

/** 目標がひとつしか無い場合。 */
fun longestStreak(stepsByDate: Map<String, Long>, goal: Goal): Int =
    longestStreak(stepsByDate, GoalHistory.single(goal))
