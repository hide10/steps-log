package app.stepsapp.widget

import android.content.Context
import app.stepsapp.data.local.PrefsStore
import app.stepsapp.data.local.StepsDatabase
import app.stepsapp.domain.Goal
import app.stepsapp.domain.GoalHistory
import app.stepsapp.domain.GoalPeriod
import app.stepsapp.domain.Period
import app.stepsapp.domain.aggregate
import app.stepsapp.domain.currentStreak
import java.time.LocalDate

/** ウィジェットが必要とする値をまとめて取る。 */
data class WidgetData(
    val today: Long,
    val goal: Goal,
    val streak: Int,
    /** 直近7日。古い順。 */
    val week: List<Long>,
    val weekLabels: List<String>,
    val weekAverage: Long,
    val monthAverage: Long,
    /** 今月の日別。古い順。 */
    val month: List<Long>,
    val weightKg: Double?,
    val sleepMinutes: Long?,
) {
    val ratio: Float
        get() = if (goal.dailySteps <= 0) 0f
        else (today.toFloat() / goal.dailySteps).coerceIn(0f, 1f)

    companion object {
        private val WEEKDAY = listOf("月", "火", "水", "木", "金", "土", "日")

        suspend fun load(context: Context): WidgetData {
            val dao = StepsDatabase.getInstance(context).stepsDao()
            val prefs = PrefsStore.getInstance(context)
            val goal = prefs.goal
            // ストリークはその日に有効だった目標で数える。
            // 履歴がまだ無い端末では、いまの目標ひとつで代用する
            val goals = dao.allGoals()
                .map { GoalPeriod(it.effectiveFrom, it.dailySteps) }
                .takeIf { it.isNotEmpty() }
                ?.let { GoalHistory.of(it) }
                ?: GoalHistory.single(goal)
            val today = LocalDate.now()

            val all = dao.allDays().associate { it.localDate to it.stepCount }

            // 直近7日は「記録が無い日も0として並べる」。
            // グラフは日付の並びが揃っていないと形が読めないため。
            val week = (6 downTo 0).map { today.minusDays(it.toLong()) }
            val weekValues = week.map { all[it.toString()] ?: 0L }
            val labels = week.map { WEEKDAY[it.dayOfWeek.value - 1] }

            val monthStart = today.withDayOfMonth(1)
            val monthDays = generateSequence(monthStart) { it.plusDays(1) }
                .takeWhile { !it.isAfter(today) }
                .toList()

            val weekAvg = aggregate(all, Period.WEEK).firstOrNull()?.average ?: 0.0
            val monthAvg = aggregate(all, Period.MONTH).firstOrNull()?.average ?: 0.0

            return WidgetData(
                today = all[today.toString()] ?: 0L,
                goal = goal,
                streak = currentStreak(all, today, goals),
                week = weekValues,
                weekLabels = labels,
                weekAverage = Math.round(weekAvg),
                monthAverage = Math.round(monthAvg),
                month = monthDays.map { all[it.toString()] ?: 0L },
                weightKg = dao.allWeights().maxByOrNull { it.localDate }?.kg,
                sleepMinutes = dao.allSleep().maxByOrNull { it.localDate }?.minutes,
            )
        }
    }
}
