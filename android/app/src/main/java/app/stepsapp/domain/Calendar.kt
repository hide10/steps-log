package app.stepsapp.domain

import java.time.LocalDate
import java.time.YearMonth

/** その日の状態。**未計測と0歩を区別する**ことがこの機能の肝。 */
enum class DayState {
    /** 目標を達成した */
    ACHIEVED,

    /** 記録はあるが目標に届かなかった */
    MISSED,

    /** 記録が無い。歩かなかったのではなく、測れていない */
    UNMEASURED,

    /** 未達だったが「連続を守る」で切らなかった日。黙って埋めないための印 */
    FROZEN,
}

/** カレンダーの1マス。空白のマスは [date] が null。 */
data class CalendarCell(
    val date: LocalDate?,
    val state: DayState,
)

/** ひと月ぶんのマス目。 */
data class CalendarMonth(
    val yearMonth: YearMonth,
    /** 月曜始まりで7列。先頭の空白も含む */
    val cells: List<CalendarCell>,
)

fun stateOf(steps: Long?, goal: Goal): DayState = when {
    steps == null -> DayState.UNMEASURED
    steps >= goal.dailySteps -> DayState.ACHIEVED
    else -> DayState.MISSED
}

/** その日に有効だった目標で判定する。 */
fun stateOf(steps: Long?, date: String, goals: GoalHistory): DayState =
    stateOf(steps, goals.goalOn(date))

/**
 * ひと月ぶんのマス目を作る。
 *
 * **月曜始まり。** 週の集計を月曜始まりにしているので、揃えないと
 * 「この週は達成が多かった」という見え方がカレンダーと集計でずれる。
 */
fun buildMonth(
    yearMonth: YearMonth,
    stepsByDate: Map<String, Long>,
    goals: GoalHistory,
    frozen: Set<String> = emptySet(),
): CalendarMonth {
    val first = yearMonth.atDay(1)
    // dayOfWeek は月曜=1。月曜始まりにするので、そのまま引けばよい
    val leading = first.dayOfWeek.value - 1

    val cells = buildList {
        repeat(leading) { add(CalendarCell(null, DayState.UNMEASURED)) }
        for (day in 1..yearMonth.lengthOfMonth()) {
            val date = yearMonth.atDay(day).toString()
            val state =
                if (date in frozen) DayState.FROZEN
                else stateOf(stepsByDate[date], date, goals)
            add(CalendarCell(yearMonth.atDay(day), state))
        }
    }
    return CalendarMonth(yearMonth, cells)
}

fun buildMonth(
    yearMonth: YearMonth,
    stepsByDate: Map<String, Long>,
    goal: Goal,
): CalendarMonth = buildMonth(yearMonth, stepsByDate, GoalHistory.single(goal))

/** 直近から遡って [count] か月ぶん。新しい月が先頭。 */
fun recentMonths(
    today: LocalDate,
    count: Int,
    stepsByDate: Map<String, Long>,
    goals: GoalHistory,
    frozen: Set<String> = emptySet(),
): List<CalendarMonth> {
    val current = YearMonth.from(today)
    return (0 until count).map {
        buildMonth(current.minusMonths(it.toLong()), stepsByDate, goals, frozen)
    }
}

fun recentMonths(
    today: LocalDate,
    count: Int,
    stepsByDate: Map<String, Long>,
    goal: Goal,
): List<CalendarMonth> = recentMonths(today, count, stepsByDate, GoalHistory.single(goal))

/** 達成した日の総数。 */
fun achievedCount(stepsByDate: Map<String, Long>, goals: GoalHistory): Int =
    stepsByDate.count { isAchievedOn(it.value, it.key, goals) }

fun achievedCount(stepsByDate: Map<String, Long>, goal: Goal): Int =
    achievedCount(stepsByDate, GoalHistory.single(goal))

/** いちばん長く続いた期間。 */
data class StreakSpan(val days: Int, val from: String, val to: String)

/**
 * 最長のストリークが「いつからいつまでか」を返す。
 *
 * 未計測の日はストリークを切らないという既存の規則に合わせる。
 */
fun longestSpan(stepsByDate: Map<String, Long>, goals: GoalHistory): StreakSpan? {
    val achieved = stepsByDate.filterKeys { isAchievedOn(stepsByDate.getValue(it), it, goals) }
        .keys.sorted()
    if (achieved.isEmpty()) return null

    var best = StreakSpan(1, achieved.first(), achieved.first())
    var runStart = achieved.first()
    var run = 1

    for (i in 1 until achieved.size) {
        val prev = LocalDate.parse(achieved[i - 1])
        val cur = LocalDate.parse(achieved[i])
        val gapAllUnmeasured = generateSequence(prev.plusDays(1)) { it.plusDays(1) }
            .takeWhile { it < cur }
            .all { stepsByDate[it.toString()] == null }

        if (gapAllUnmeasured) {
            run++
        } else {
            run = 1
            runStart = achieved[i]
        }
        if (run > best.days) best = StreakSpan(run, runStart, achieved[i])
    }
    return best
}

fun longestSpan(stepsByDate: Map<String, Long>, goal: Goal): StreakSpan? =
    longestSpan(stepsByDate, GoalHistory.single(goal))

/**
 * 起動直後の埋め草に使う歩数。
 *
 * **日付が変わっていたら使わない。** 前日の歩数が今日のものとして
 * 一瞬でも見えるのは、0 が見えるより害が大きい。
 */
fun cachedTodaySteps(today: String, cachedDate: String, cachedSteps: Long): Long =
    if (cachedDate == today && cachedSteps > 0) cachedSteps else 0
