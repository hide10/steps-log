package app.stepsapp.domain

/** 1日の目標歩数。 */
data class Goal(val dailySteps: Long) {
    init {
        require(dailySteps > 0) { "目標歩数は正の数: $dailySteps" }
    }

    companion object {
        const val DEFAULT = 6_000L

        /** 設定画面で選べる候補。 */
        val PRESETS = listOf(3_000L, 5_000L, 6_000L, 8_000L, 10_000L, 12_000L, 15_000L)
    }
}

/**
 * 目標に対する達成率を 0.0〜1.0 で返す。
 * リング表示に使うため 1.0 で頭打ちにする（超過分は別途 [achievedRatioUncapped]）。
 */
fun achievedRatio(steps: Long, goal: Goal): Float =
    (steps.toFloat() / goal.dailySteps.toFloat()).coerceIn(0f, 1f)

/** 頭打ちしない達成率。「目標の 1.4 倍」のような表示に使う。 */
fun achievedRatioUncapped(steps: Long, goal: Goal): Float =
    (steps.toFloat() / goal.dailySteps.toFloat()).coerceAtLeast(0f)

fun isAchieved(steps: Long, goal: Goal): Boolean = steps >= goal.dailySteps

/** 目標まであと何歩か。達成済みなら 0。 */
fun remainingSteps(steps: Long, goal: Goal): Long =
    (goal.dailySteps - steps).coerceAtLeast(0)
