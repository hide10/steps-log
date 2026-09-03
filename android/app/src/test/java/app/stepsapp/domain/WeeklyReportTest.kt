package app.stepsapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class WeeklyReportTest {

    private val goals = GoalHistory.single(Goal(6_000))

    /** 2026-09-07 は月曜。この日から見た「先週」は 08-31(月)〜09-06(日)。 */
    private val monday = LocalDate.parse("2026-09-07")

    @Test
    fun `先週の合計と平均を出す`() {
        val steps = mapOf(
            "2026-08-31" to 10_000L,
            "2026-09-01" to 4_000L,
            "2026-09-02" to 10_000L,
        )
        val r = weeklyReport(steps, monday, goals)!!
        assertEquals("2026-08-31", r.weekStart)
        assertEquals(24_000L, r.total)
        // 平均の分母は記録がある日だけ。7で割らない
        assertEquals(8_000L, r.average)
        assertEquals(3, r.daysRecorded)
        assertEquals(2, r.achieved)
    }

    @Test
    fun `今週の記録は混ぜない`() {
        val steps = mapOf(
            "2026-09-06" to 10_000L,   // 先週の日曜
            "2026-09-07" to 99_000L,   // 今週の月曜。入ってはいけない
        )
        assertEquals(10_000L, weeklyReport(steps, monday, goals)!!.total)
    }

    @Test
    fun `前の週との差を出す`() {
        val steps = mapOf(
            "2026-08-24" to 6_000L,    // 前々週（比較対象）
            "2026-08-31" to 10_000L,   // 先週
        )
        assertEquals(4_000L, weeklyReport(steps, monday, goals)!!.diff)
    }

    @Test
    fun `比べる相手が無ければ差は出さない`() {
        val steps = mapOf("2026-08-31" to 10_000L)
        assertNull(weeklyReport(steps, monday, goals)!!.diff)
    }

    @Test
    fun `記録が1日も無い週は作らない`() {
        assertNull(weeklyReport(mapOf("2026-09-07" to 10_000L), monday, goals))
    }

    @Test
    fun `達成はその日に有効だった目標で数える`() {
        val history = GoalHistory.of(
            listOf(GoalPeriod("2026-01-01", 7_000), GoalPeriod("2026-09-02", 12_000)),
        )
        val steps = mapOf(
            "2026-08-31" to 8_000L,   // 当時の目標 7,000 → 達成
            "2026-09-02" to 8_000L,   // 当時の目標 12,000 → 未達
        )
        assertEquals(1, weeklyReport(steps, monday, history)!!.achieved)
    }

    @Test
    fun `文言に記録できた日数を必ず添える`() {
        // 3日しか測れていない週の平均だけ見せると、実態より良く見える
        val r = WeeklyReport("2026-08-31", 24_000, 8_000, 3, 2, 1_000)
        val (head, body) = weeklyReportText(r)
        assertEquals("先週は1日あたり 8,000 歩", head)
        assertEquals("7日中3日の記録、目標達成 2日、前週より 1,000 歩多い", body)
    }
}
