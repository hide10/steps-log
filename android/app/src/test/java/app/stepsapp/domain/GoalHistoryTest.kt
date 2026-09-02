package app.stepsapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 目標の履歴。**目標を変えても過去の達成判定が変わらない**ことを固定する。
 *
 * ここが壊れると、目標を上げた日に過去の達成済みの日が一斉に未達成へ変わり、
 * 連続日数の自己ベストまで書き換わる（静かに間違った結果になる）。
 */
class GoalHistoryTest {

    /** 1月は7000歩、2月からは8000歩。本家のレビューで挙がっていた例そのもの。 */
    private val goals = GoalHistory.of(
        listOf(
            GoalPeriod("2026-01-01", 7_000),
            GoalPeriod("2026-02-01", 8_000),
        ),
    )

    // --- その日に有効だった目標 ---

    @Test
    fun `その日に有効だった目標を返す`() {
        assertEquals(7_000L, goals.goalOn("2026-01-15").dailySteps)
        assertEquals(8_000L, goals.goalOn("2026-02-15").dailySteps)
    }

    @Test
    fun `変更した日は当日から新しい目標`() {
        assertEquals(7_000L, goals.goalOn("2026-01-31").dailySteps)
        assertEquals(8_000L, goals.goalOn("2026-02-01").dailySteps)
    }

    @Test
    fun `履歴より前の日はいちばん古い目標で判定する`() {
        // 目標なしにすると、記録はあるのに達成が消えてしまう
        assertEquals(7_000L, goals.goalOn("2025-12-01").dailySteps)
    }

    @Test
    fun `履歴が空なら既定の目標`() {
        assertEquals(Goal.DEFAULT, GoalHistory.EMPTY.goalOn("2026-01-01").dailySteps)
    }

    @Test
    fun `目標がひとつなら全期間その目標`() {
        val single = GoalHistory.single(Goal(6_000))
        assertEquals(6_000L, single.goalOn("1999-01-01").dailySteps)
        assertEquals(6_000L, single.goalOn("2099-12-31").dailySteps)
    }

    // --- 履歴の組み立て ---

    @Test
    fun `日付の順に並べ替える`() {
        val h = GoalHistory.of(
            listOf(GoalPeriod("2026-02-01", 8_000), GoalPeriod("2026-01-01", 7_000)),
        )
        assertEquals(listOf("2026-01-01", "2026-02-01"), h.periods.map { it.from })
    }

    @Test
    fun `同じ歩数が続く行は畳む`() {
        // 「いつ変えたか」を読むときに邪魔になるだけなので残さない
        val h = GoalHistory.of(
            listOf(
                GoalPeriod("2026-01-01", 7_000),
                GoalPeriod("2026-02-01", 7_000),
                GoalPeriod("2026-03-01", 8_000),
            ),
        )
        assertEquals(listOf("2026-01-01", "2026-03-01"), h.periods.map { it.from })
    }

    @Test
    fun `同じ日に変えたら後の値で置き換える`() {
        val h = goals.changedOn("2026-02-01", 9_000)
        assertEquals(2, h.periods.size)
        assertEquals(9_000L, h.goalOn("2026-02-01").dailySteps)
        assertEquals(7_000L, h.goalOn("2026-01-31").dailySteps)
    }

    @Test
    fun `いまと同じ歩数に変えても履歴は増えない`() {
        val h = goals.changedOn("2026-03-01", 8_000)
        assertEquals(goals.periods, h.periods)
    }

    @Test
    fun `目標を変えると以降だけが新しい目標になる`() {
        val h = goals.changedOn("2026-03-01", 10_000)
        assertEquals(7_000L, h.goalOn("2026-01-15").dailySteps)
        assertEquals(8_000L, h.goalOn("2026-02-15").dailySteps)
        assertEquals(10_000L, h.goalOn("2026-03-15").dailySteps)
    }

    // --- 達成判定 ---

    @Test
    fun `過去の達成は目標を上げても消えない`() {
        // 1月に 7,200 歩の日。2月から目標を 8,000 に上げても達成のまま
        assertTrue(isAchievedOn(7_200, "2026-01-15", goals))
        assertFalse(isAchievedOn(7_200, "2026-02-15", goals))
    }

    @Test
    fun `カレンダーの状態もその日の目標で決まる`() {
        assertEquals(DayState.ACHIEVED, stateOf(7_200, "2026-01-15", goals))
        assertEquals(DayState.MISSED, stateOf(7_200, "2026-02-15", goals))
        assertEquals(DayState.UNMEASURED, stateOf(null, "2026-01-15", goals))
    }

    @Test
    fun `達成日数はその日の目標で数える`() {
        val steps = mapOf(
            "2026-01-15" to 7_200L,   // 当時の目標 7,000 → 達成
            "2026-02-15" to 7_200L,   // 当時の目標 8,000 → 未達
            "2026-02-16" to 8_400L,   // 達成
        )
        assertEquals(2, achievedCount(steps, goals))
    }

    // --- ストリーク ---

    @Test
    fun `目標を上げても過去のストリークは切れない`() {
        val steps = mapOf(
            "2026-01-29" to 7_100L,
            "2026-01-30" to 7_100L,
            "2026-01-31" to 7_100L,
            "2026-02-01" to 8_100L,
            "2026-02-02" to 8_100L,
        )
        assertEquals(5, currentStreak(steps, LocalDate.parse("2026-02-02"), goals))
        assertEquals(5, longestStreak(steps, goals))
    }

    @Test
    fun `目標を上げた日から届かなければそこで切れる`() {
        val steps = mapOf(
            "2026-01-30" to 7_100L,
            "2026-01-31" to 7_100L,
            "2026-02-01" to 7_100L,   // 新しい目標 8,000 には届かない
            "2026-02-02" to 8_100L,
        )
        assertEquals(1, currentStreak(steps, LocalDate.parse("2026-02-02"), goals))
        assertEquals(2, longestStreak(steps, goals))
    }

    @Test
    fun `最長の期間もその日の目標で数える`() {
        val steps = mapOf(
            "2026-01-10" to 7_100L,
            "2026-01-11" to 7_100L,
            "2026-01-12" to 7_100L,
            "2026-01-13" to 100L,     // 記録があって未達。ここで切れる
            "2026-02-10" to 8_100L,
        )
        val span = longestSpan(steps, goals)!!
        assertEquals(3, span.days)
        assertEquals("2026-01-10", span.from)
        assertEquals("2026-01-12", span.to)
    }
}
