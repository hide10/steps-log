package app.stepsapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CalendarTest {

    private val goal = Goal(6_000)

    @Test
    fun `未計測と0歩を区別する`() {
        // これがこの画面の肝。0歩は「歩かなかった」、未計測は「測れていない」
        assertEquals(DayState.UNMEASURED, stateOf(null, goal))
        assertEquals(DayState.MISSED, stateOf(0, goal))
        assertEquals(DayState.MISSED, stateOf(5_999, goal))
        assertEquals(DayState.ACHIEVED, stateOf(6_000, goal))
    }

    @Test
    fun `月曜始まりで先頭に空白が入る`() {
        // 2026-08-01 は土曜。月曜始まりなら前に5マス空く
        val m = buildMonth(YearMonth.of(2026, 8), emptyMap(), goal)
        assertEquals(5, m.cells.takeWhile { it.date == null }.size)
        assertEquals(LocalDate.of(2026, 8, 1), m.cells[5].date)
    }

    @Test
    fun `月曜はじまりの月は空白が入らない`() {
        // 2026-06-01 は月曜
        val m = buildMonth(YearMonth.of(2026, 6), emptyMap(), goal)
        assertEquals(LocalDate.of(2026, 6, 1), m.cells.first().date)
    }

    @Test
    fun `その月の日数ぶんのマスがある`() {
        val m = buildMonth(YearMonth.of(2026, 8), emptyMap(), goal)
        assertEquals(31, m.cells.count { it.date != null })
        val feb = buildMonth(YearMonth.of(2024, 2), emptyMap(), goal)
        assertEquals(29, feb.cells.count { it.date != null })   // 閏年
    }

    @Test
    fun `新しい月が先頭に来る`() {
        val months = recentMonths(LocalDate.of(2026, 8, 27), 3, emptyMap(), goal)
        assertEquals(YearMonth.of(2026, 8), months[0].yearMonth)
        assertEquals(YearMonth.of(2026, 7), months[1].yearMonth)
        assertEquals(YearMonth.of(2026, 6), months[2].yearMonth)
    }

    @Test
    fun `年をまたいで遡れる`() {
        val months = recentMonths(LocalDate.of(2026, 1, 15), 2, emptyMap(), goal)
        assertEquals(YearMonth.of(2026, 1), months[0].yearMonth)
        assertEquals(YearMonth.of(2025, 12), months[1].yearMonth)
    }

    @Test
    fun `達成した日を数える`() {
        val steps = mapOf("2026-08-01" to 7_000L, "2026-08-02" to 100L, "2026-08-03" to 6_000L)
        assertEquals(2, achievedCount(steps, goal))
    }

    @Test
    fun `最長の期間がいつからいつまでか分かる`() {
        val steps = mapOf(
            "2026-08-01" to 9_000L, "2026-08-02" to 9_000L, "2026-08-03" to 9_000L,
            "2026-08-04" to 100L,
            "2026-08-05" to 9_000L, "2026-08-06" to 9_000L,
        )
        val span = longestSpan(steps, goal)!!
        assertEquals(3, span.days)
        assertEquals("2026-08-01", span.from)
        assertEquals("2026-08-03", span.to)
    }

    @Test
    fun `未計測の日は最長の期間を切らない`() {
        val steps = mapOf("2026-08-01" to 9_000L, "2026-08-03" to 9_000L)
        val span = longestSpan(steps, goal)!!
        assertEquals(2, span.days)
    }

    @Test
    fun `0歩の日は最長の期間を切る`() {
        val steps = mapOf("2026-08-01" to 9_000L, "2026-08-02" to 0L, "2026-08-03" to 9_000L)
        assertEquals(1, longestSpan(steps, goal)!!.days)
    }

    @Test
    fun `達成が無ければ期間も無い`() {
        assertNull(longestSpan(mapOf("2026-08-01" to 10L), goal))
        assertNull(longestSpan(emptyMap(), goal))
    }

    @Test
    fun `起動直後の埋め草は同じ日付のときだけ使う`() {
        assertEquals(5_830, cachedTodaySteps("2026-08-30", "2026-08-30", 5_830))
    }

    @Test
    fun `日付が変わっていたら前日の歩数を使わない`() {
        // 前日の歩数が今日として見えるのは、0 が見えるより害が大きい
        assertEquals(0, cachedTodaySteps("2026-08-30", "2026-08-29", 12_000))
    }

    @Test
    fun `キャッシュが無ければ0`() {
        assertEquals(0, cachedTodaySteps("2026-08-30", "", 0))
    }
}
