package app.stepsapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GoalStreakTest {

    private val goal = Goal(6_000)
    private val today = LocalDate.parse("2026-08-26")

    // --- 目標 ---

    @Test
    fun `達成率はリング表示のため1で頭打ちにする`() {
        assertEquals(0.5f, achievedRatio(3_000, goal), 0.001f)
        assertEquals(1f, achievedRatio(6_000, goal), 0.001f)
        assertEquals(1f, achievedRatio(99_999, goal), 0.001f)
    }

    @Test
    fun `頭打ちしない達成率は超過分も返す`() {
        assertEquals(1.5f, achievedRatioUncapped(9_000, goal), 0.001f)
    }

    @Test
    fun `目標ちょうどは達成とみなす`() {
        assertTrue(isAchieved(6_000, goal))
        assertFalse(isAchieved(5_999, goal))
    }

    @Test
    fun `残り歩数は達成済みなら0`() {
        assertEquals(2_000L, remainingSteps(4_000, goal))
        assertEquals(0L, remainingSteps(6_000, goal))
        assertEquals(0L, remainingSteps(10_000, goal))
    }

    @Test
    fun `目標に0以下は設定できない`() {
        try {
            Goal(0)
            throw AssertionError("例外が投げられなかった")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("正の数"))
        }
    }

    // --- ストリーク ---

    @Test
    fun `連続で達成した日数を数える`() {
        val steps = mapOf(
            "2026-08-24" to 7_000L,
            "2026-08-25" to 8_000L,
            "2026-08-26" to 9_000L,
        )
        assertEquals(3, currentStreak(steps, today, goal))
    }

    @Test
    fun `記録があって未達の日でストリークが切れる`() {
        val steps = mapOf(
            "2026-08-23" to 9_000L,
            "2026-08-24" to 1_000L,   // 明確な未達
            "2026-08-25" to 8_000L,
            "2026-08-26" to 9_000L,
        )
        assertEquals(2, currentStreak(steps, today, goal))
    }

    @Test
    fun `未計測の日はストリークを切らない`() {
        // 端末を持たずに過ごしただけかもしれないので、歩かなかったとは断定しない
        val steps = mapOf(
            "2026-08-24" to 7_000L,
            // 2026-08-25 は記録なし
            "2026-08-26" to 9_000L,
        )
        assertEquals(2, currentStreak(steps, today, goal))
    }

    @Test
    fun `0歩と記録された日はストリークを切る`() {
        // 「未計測」と違い、0歩は実際に歩かなかったと確定している
        val steps = mapOf(
            "2026-08-24" to 7_000L,
            "2026-08-25" to 0L,
            "2026-08-26" to 9_000L,
        )
        assertEquals(1, currentStreak(steps, today, goal))
    }

    @Test
    fun `当日が未達でもストリークは切らない`() {
        // まだ歩く時間が残っているため
        val steps = mapOf(
            "2026-08-24" to 7_000L,
            "2026-08-25" to 8_000L,
            "2026-08-26" to 100L,   // 今日はまだ100歩
        )
        assertEquals(2, currentStreak(steps, today, goal))
    }

    @Test
    fun `当日が達成済みならストリークに含める`() {
        val steps = mapOf(
            "2026-08-25" to 8_000L,
            "2026-08-26" to 6_000L,
        )
        assertEquals(2, currentStreak(steps, today, goal))
    }

    @Test
    fun `記録が無ければストリークは0`() {
        assertEquals(0, currentStreak(emptyMap(), today, goal))
    }

    @Test
    fun `昨日が未達なら当日未達でストリークは0`() {
        val steps = mapOf(
            "2026-08-25" to 100L,
            "2026-08-26" to 100L,
        )
        assertEquals(0, currentStreak(steps, today, goal))
    }

    // --- 最長ストリーク ---

    @Test
    fun `最長ストリークを見つける`() {
        val steps = mapOf(
            "2026-08-01" to 9_000L,
            "2026-08-02" to 9_000L,
            "2026-08-03" to 9_000L,   // ここまで3連続
            "2026-08-04" to 100L,     // 途切れる
            "2026-08-05" to 9_000L,
            "2026-08-06" to 9_000L,   // 2連続
        )
        assertEquals(3, longestStreak(steps, goal))
    }

    @Test
    fun `最長ストリークも未計測の日で切れない`() {
        val steps = mapOf(
            "2026-08-01" to 9_000L,
            // 08-02 は記録なし
            "2026-08-03" to 9_000L,
        )
        assertEquals(2, longestStreak(steps, goal))
    }

    @Test
    fun `達成日が無ければ最長ストリークは0`() {
        assertEquals(0, longestStreak(mapOf("2026-08-01" to 10L), goal))
    }
}
