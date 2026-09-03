package app.stepsapp.domain

import java.time.LocalDate

/** ひと月に使える「連続を守る」回数。 */
const val FREEZES_PER_MONTH = 2

/**
 * 連続日数と、そのために守った日。
 *
 * @param days   連続日数
 * @param frozen 守った日（記録があって未達だが、連続を切らなかった日）
 */
data class FrozenStreak(val days: Int, val frozen: List<String>)

/**
 * 連続日数を、月に [perMonth] 回まで「守り」ながら数える。
 *
 * **守った日は必ず返す。** 黙って埋めると記録を信用できなくなるので、
 * 呼び出し側はこれをカレンダーなどで見せること。
 *
 * **守る対象は「記録があって未達だった日」だけ。** 未計測の日はもともと
 * 連続を切らない（不変条件4）ので、守る必要がない。
 *
 * **数えるのは今つながっている連続だけ。** 自己ベスト（過去の最長）には使わない。
 * あちらは「実際に達成し続けた日数」として正確に保つ。守りで伸ばした数字が
 * 過去の記録と並ぶと、どちらも意味が薄れる。
 *
 * 回数は暦月ごとに戻る。月をまたいで貯めることはしない。
 */
fun currentStreakWithFreeze(
    stepsByDate: Map<String, Long>,
    today: LocalDate,
    goals: GoalHistory,
    perMonth: Int = FREEZES_PER_MONTH,
): FrozenStreak {
    val oldest = stepsByDate.keys.minOrNull() ?: return FrozenStreak(0, emptyList())

    var streak = 0
    var date = today
    val frozen = mutableListOf<String>()
    val usedByMonth = mutableMapOf<String, Int>()

    val todayKey = today.toString()
    val todaySteps = stepsByDate[todayKey]
    if (todaySteps == null || !isAchievedOn(todaySteps, todayKey, goals)) {
        date = today.minusDays(1)
    }

    while (true) {
        val key = date.toString()
        if (key < oldest) return FrozenStreak(streak, frozen)

        val steps = stepsByDate[key]
        when {
            steps == null -> Unit
            isAchievedOn(steps, key, goals) -> streak++
            else -> {
                // 記録があって未達。その月の残りがあれば守る
                val month = key.substring(0, 7)
                val used = usedByMonth.getOrDefault(month, 0)
                if (used >= perMonth) return FrozenStreak(streak, frozen)
                usedByMonth[month] = used + 1
                frozen += key
            }
        }
        date = date.minusDays(1)
    }
}

/** その月に残っている「守る」回数。設定画面などに出すため。 */
fun freezesLeftThisMonth(
    frozen: List<String>,
    month: String,
    perMonth: Int = FREEZES_PER_MONTH,
): Int = (perMonth - frozen.count { it.startsWith(month) }).coerceAtLeast(0)
