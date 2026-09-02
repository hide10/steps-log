package app.stepsapp.domain

/**
 * 「この日からこの目標だった」の1件。
 *
 * [from] は端末ローカルの暦日（`YYYY-MM-DD`）。次の [GoalPeriod] が始まる前日まで有効。
 */
data class GoalPeriod(val from: String, val dailySteps: Long) {
    init {
        require(dailySteps > 0) { "目標歩数は正の数: $dailySteps" }
    }

    val goal: Goal get() = Goal(dailySteps)
}

/**
 * 目標の履歴。
 *
 * **達成の判定は、その日に有効だった目標で行う。** 目標をひとつしか持たないと、
 * 目標を上げた瞬間に過去の達成済みの日が未達成に変わり、連続日数の自己ベストまで
 * 壊れる（本家 StepsApp のレビューで最も具体的に挙がっていた不満）。
 *
 * **記録より前の日には、いちばん古い目標をさかのぼって適用する。**
 * 履歴が始まる前の日を「目標なし」にすると達成が消えてしまうため。
 * これは移行（既存の設定を「最初の記録日から現在の目標」として1件入れる）とも噛み合う。
 */
class GoalHistory private constructor(val periods: List<GoalPeriod>) {

    /** その日に有効だった目標。履歴が空なら既定値。 */
    fun goalOn(date: String): Goal {
        val period = periods.lastOrNull { it.from <= date }
        // 履歴が始まる前の日は、いちばん古い目標で判定する
            ?: periods.firstOrNull()
        return period?.goal ?: Goal(Goal.DEFAULT)
    }

    /**
     * [from] からの目標を差し替えた履歴を返す。
     *
     * 直前と同じ歩数なら足さない。同じ目標が並ぶだけの履歴は、
     * あとから「いつ変えたか」を読むときに邪魔になる。
     */
    fun changedOn(from: String, dailySteps: Long): GoalHistory {
        if (goalOn(from).dailySteps == dailySteps &&
            periods.any { it.from <= from }
        ) {
            return this
        }
        val kept = periods.filter { it.from != from }
        return of(kept + GoalPeriod(from, dailySteps))
    }

    fun isEmpty(): Boolean = periods.isEmpty()

    override fun equals(other: Any?): Boolean =
        other is GoalHistory && other.periods == periods

    override fun hashCode(): Int = periods.hashCode()

    override fun toString(): String = "GoalHistory($periods)"

    companion object {
        val EMPTY = GoalHistory(emptyList())

        /**
         * 日付順に整えて作る。
         * 同じ日に2件あれば後勝ち、直前と同じ歩数の行は畳む。
         */
        fun of(periods: List<GoalPeriod>): GoalHistory {
            val sorted = periods
                .groupBy { it.from }
                .map { (_, sameDay) -> sameDay.last() }
                .sortedBy { it.from }

            val folded = buildList<GoalPeriod> {
                for (p in sorted) {
                    if (lastOrNull()?.dailySteps == p.dailySteps) continue
                    add(p)
                }
            }
            return GoalHistory(folded)
        }

        /**
         * 全期間ひとつの目標。履歴を持たない呼び出し側（既存のテストなど）のため。
         *
         * [GoalPeriod.from] の値は結果に影響しない。1件しか無ければ、
         * どの日を訊かれてもその目標が返るため。
         */
        fun single(goal: Goal): GoalHistory =
            GoalHistory(listOf(GoalPeriod(EPOCH, goal.dailySteps)))

        /** 履歴の起点。実際の記録がこれより古くなることはない。 */
        const val EPOCH = "1970-01-01"
    }
}

/** その日に有効だった目標で達成を判定する。 */
fun isAchievedOn(steps: Long, date: String, goals: GoalHistory): Boolean =
    isAchieved(steps, goals.goalOn(date))
