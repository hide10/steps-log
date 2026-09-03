package app.stepsapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordsTest {

    private val goals = GoalHistory.single(Goal(6_000))

    @Test
    fun `記録が無ければ空`() {
        val r = records(emptyMap(), goals)
        assertNull(r.bestDay)
        assertEquals(0L, r.total)
        assertEquals(0, r.daysRecorded)
    }

    @Test
    fun `1日の最高歩数とその日付を持つ`() {
        val steps = mapOf(
            "2026-05-01" to 8_000L,
            "2026-05-03" to 23_104L,
            "2026-05-04" to 9_000L,
        )
        val best = records(steps, goals).bestDay!!
        assertEquals(23_104L, best.value)
        assertEquals("2026-05-03", best.on)
    }

    @Test
    fun `週と月の最高は平均で比べる`() {
        // 5月4日(月)から3日だけの週（平均10,000）と、
        // 5月11日(月)から1日だけの週（平均20,000）なら、後者が最高
        val steps = mapOf(
            "2026-05-04" to 10_000L,
            "2026-05-05" to 10_000L,
            "2026-05-06" to 10_000L,
            "2026-05-11" to 20_000L,
        )
        val r = records(steps, goals)
        assertEquals(20_000L, r.bestWeek!!.value)
        assertEquals("2026-05-11", r.bestWeek.on)
        // 月はひとつしか無いので、その平均（合計50,000 / 4日）
        assertEquals(12_500L, r.bestMonth!!.value)
        assertEquals("2026-05", r.bestMonth.on)
    }

    @Test
    fun `平均の分母は記録がある日だけ`() {
        // 記録が2日しか無い月を、30日で割ってはいけない
        val steps = mapOf("2026-05-01" to 10_000L, "2026-05-02" to 20_000L)
        assertEquals(15_000L, records(steps, goals).bestMonth!!.value)
    }

    @Test
    fun `累計と記録日数を数える`() {
        val steps = mapOf(
            "2026-05-01" to 10_000L,
            "2026-05-02" to 0L,   // 0歩の記録も日数には数える
            "2026-05-03" to 5_000L,
        )
        val r = records(steps, goals)
        assertEquals(15_000L, r.total)
        assertEquals(3, r.daysRecorded)
    }

    @Test
    fun `最長の連続日数はその日の目標で数える`() {
        val history = GoalHistory.of(
            listOf(GoalPeriod("2026-01-01", 7_000), GoalPeriod("2026-02-01", 8_000)),
        )
        val steps = mapOf(
            "2026-01-30" to 7_100L,
            "2026-01-31" to 7_100L,
            "2026-02-01" to 7_100L,   // 新しい目標には届かない
        )
        assertEquals(2, records(steps, history).longestStreak)
    }

    // --- 記録の更新 ---

    @Test
    fun `1日の最高を超えたら新記録`() {
        val kinds = newRecordsToday(
            previousBestDay = 20_000,
            previousLongest = 10,
            todaySteps = 20_001,
            streakIncludingToday = 5,
        )
        assertEquals(listOf(RecordKind.BEST_DAY), kinds)
    }

    @Test
    fun `同値は更新とみなさない`() {
        // 前回と同じ歩数で「新記録」と言われると白々しい
        val kinds = newRecordsToday(
            previousBestDay = 20_000,
            previousLongest = 10,
            todaySteps = 20_000,
            streakIncludingToday = 10,
        )
        assertTrue(kinds.isEmpty())
    }

    @Test
    fun `最長の連続日数を超えたら新記録`() {
        val kinds = newRecordsToday(
            previousBestDay = 30_000,
            previousLongest = 10,
            todaySteps = 9_000,
            streakIncludingToday = 11,
        )
        assertEquals(listOf(RecordKind.LONGEST_STREAK), kinds)
    }

    @Test
    fun `記録がまだ無いうちは祝わない`() {
        // 使いはじめの1日目に「自己新記録」と言われても意味がない
        val kinds = newRecordsToday(
            previousBestDay = 0,
            previousLongest = 0,
            todaySteps = 12_000,
            streakIncludingToday = 1,
        )
        assertTrue(kinds.isEmpty())
    }

    @Test
    fun `両方いっぺんに更新することもある`() {
        val kinds = newRecordsToday(
            previousBestDay = 10_000,
            previousLongest = 5,
            todaySteps = 12_000,
            streakIncludingToday = 6,
        )
        assertEquals(listOf(RecordKind.BEST_DAY, RecordKind.LONGEST_STREAK), kinds)
    }

    // --- 年ごとの記録 ---

    @Test
    fun `年ごとに平均と最高と記録日数を出す`() {
        val steps = mapOf(
            "2025-01-01" to 3_000L,
            "2025-01-02" to 5_000L,
            "2026-01-01" to 10_000L,
        )
        val years = yearlyRecords(steps)
        assertEquals(listOf("2026", "2025"), years.map { it.year })
        assertEquals(4_000L, years[1].average)
        assertEquals(5_000L, years[1].best)
        assertEquals(2, years[1].daysRecorded)
    }

    @Test
    fun `年の平均も分母は記録がある日だけ`() {
        // 使いはじめの年は記録が少ない。365で割ると不当に低く出る
        val steps = mapOf("2023-12-30" to 10_000L, "2023-12-31" to 20_000L)
        assertEquals(15_000L, yearlyRecords(steps).single().average)
    }

    @Test
    fun `年ごとの記録も記録が無ければ空`() {
        assertTrue(yearlyRecords(emptyMap()).isEmpty())
    }

    @Test
    fun `文言に数字が入る`() {
        assertEquals(
            "自己新記録" to "12,345 歩。1日の最高を更新しました",
            recordNoticeText(RecordKind.BEST_DAY, 12_345, 3),
        )
        assertEquals(
            "自己新記録" to "30 日連続。最長を更新しました",
            recordNoticeText(RecordKind.LONGEST_STREAK, 100, 30),
        )
    }
}
