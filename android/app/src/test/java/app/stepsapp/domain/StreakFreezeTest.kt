package app.stepsapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StreakFreezeTest {

    private val goals = GoalHistory.single(Goal(6_000))
    private val today = LocalDate.parse("2026-09-10")

    @Test
    fun `未達の日を守って連続をつなぐ`() {
        val steps = mapOf(
            "2026-09-08" to 8_000L,
            "2026-09-09" to 1_000L,   // 未達。ここを守る
            "2026-09-10" to 8_000L,
        )
        val r = currentStreakWithFreeze(steps, today, goals)
        assertEquals(2, r.days)
        assertEquals(listOf("2026-09-09"), r.frozen)
    }

    @Test
    fun `守った日は連続日数に数えない`() {
        // 守るのは「切らない」ためであって、歩いたことにはしない
        val steps = mapOf(
            "2026-09-09" to 1_000L,
            "2026-09-10" to 8_000L,
        )
        assertEquals(1, currentStreakWithFreeze(steps, today, goals).days)
    }

    @Test
    fun `月に2回まで`() {
        val steps = mapOf(
            "2026-09-05" to 8_000L,
            "2026-09-06" to 1_000L,   // 1回目
            "2026-09-07" to 8_000L,
            "2026-09-08" to 1_000L,   // 2回目
            "2026-09-09" to 8_000L,
            "2026-09-10" to 8_000L,
        )
        val r = currentStreakWithFreeze(steps, today, goals)
        assertEquals(4, r.days)
        assertEquals(2, r.frozen.size)
    }

    @Test
    fun `3回目は守らずそこで切れる`() {
        val steps = mapOf(
            "2026-09-03" to 8_000L,
            "2026-09-04" to 1_000L,   // 3回目にあたる。守れない
            "2026-09-05" to 8_000L,
            "2026-09-06" to 1_000L,
            "2026-09-07" to 8_000L,
            "2026-09-08" to 1_000L,
            "2026-09-09" to 8_000L,
            "2026-09-10" to 8_000L,
        )
        val r = currentStreakWithFreeze(steps, today, goals)
        assertEquals(4, r.days)
        assertEquals(listOf("2026-09-08", "2026-09-06"), r.frozen)
    }

    @Test
    fun `回数は月ごとに戻る`() {
        val steps = mapOf(
            "2026-08-30" to 8_000L,
            "2026-08-31" to 1_000L,   // 8月ぶんの1回目
            "2026-09-01" to 1_000L,   // 9月ぶんの1回目
            "2026-09-02" to 8_000L,
            "2026-09-10" to 8_000L,
        )
        val r = currentStreakWithFreeze(steps, today, goals)
        assertEquals(2, r.frozen.size)
        assertTrue(r.frozen.any { it.startsWith("2026-08") })
        assertTrue(r.frozen.any { it.startsWith("2026-09") })
    }

    @Test
    fun `未計測の日は守る回数を消費しない`() {
        // もともと連続を切らないので、守る必要がない
        val steps = mapOf(
            "2026-09-01" to 8_000L,
            // 09-02 から 09-09 は記録なし
            "2026-09-10" to 8_000L,
        )
        val r = currentStreakWithFreeze(steps, today, goals)
        assertEquals(2, r.days)
        assertTrue(r.frozen.isEmpty())
    }

    @Test
    fun `守らない設定なら素の連続と同じ`() {
        val steps = mapOf(
            "2026-09-08" to 8_000L,
            "2026-09-09" to 1_000L,
            "2026-09-10" to 8_000L,
        )
        val r = currentStreakWithFreeze(steps, today, goals, perMonth = 0)
        assertEquals(currentStreak(steps, today, goals), r.days)
        assertTrue(r.frozen.isEmpty())
    }

    @Test
    fun `その月の残り回数を数える`() {
        val frozen = listOf("2026-09-06", "2026-08-31")
        assertEquals(1, freezesLeftThisMonth(frozen, "2026-09"))
        assertEquals(1, freezesLeftThisMonth(frozen, "2026-08"))
        assertEquals(2, freezesLeftThisMonth(frozen, "2026-07"))
    }
}
