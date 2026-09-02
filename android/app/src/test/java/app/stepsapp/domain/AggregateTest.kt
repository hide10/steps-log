package app.stepsapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class AggregateTest {

    private fun d(s: String) = LocalDate.parse(s)

    // --- 週の境界（サーバ側 aggregate.ts と同じ規則でなければならない） ---

    @Test
    fun `月曜日はそれ自身が週の開始日`() {
        // 月曜日を1週前にずらすバグを防ぐ
        assertEquals(d("2026-08-24"), weekStart(d("2026-08-24")))
    }

    @Test
    fun `日曜日は前の月曜に属する`() {
        assertEquals(d("2026-08-24"), weekStart(d("2026-08-30")))
    }

    @Test
    fun `年をまたぐ週が分断されない`() {
        // 2026-12-28(月)〜2027-01-03(日) は同一週
        assertEquals(d("2026-12-28"), weekStart(d("2026-12-28")))
        assertEquals(d("2026-12-28"), weekStart(d("2026-12-31")))
        assertEquals(d("2026-12-28"), weekStart(d("2027-01-01")))
        assertEquals(d("2026-12-28"), weekStart(d("2027-01-03")))
    }

    @Test
    fun `閏日も正しい週に入る`() {
        // 2024-02-29 は木曜、その週の月曜は 2024-02-26
        assertEquals(d("2024-02-26"), weekStart(d("2024-02-29")))
    }

    // --- 集計 ---

    @Test
    fun `週ごとにまとまる`() {
        val steps = mapOf(
            "2026-08-24" to 1_000L,
            "2026-08-25" to 2_000L,
            "2026-08-30" to 3_000L,
            "2026-08-31" to 9_999L,   // 翌週
        )
        val weeks = aggregate(steps, Period.WEEK)
        val target = weeks.first { it.key == "2026-08-24" }
        assertEquals(3, target.daysRecorded)
        assertEquals(6_000L, target.total)
        assertEquals(2_000.0, target.average, 0.001)
        assertEquals(7, target.daysInPeriod)
    }

    @Test
    fun `年をまたぐ週が1つのバケットになる`() {
        val steps = mapOf(
            "2026-12-28" to 1_000L,
            "2026-12-31" to 2_000L,
            "2027-01-01" to 3_000L,
            "2027-01-03" to 4_000L,
        )
        val weeks = aggregate(steps, Period.WEEK).filter { it.key == "2026-12-28" }
        assertEquals(1, weeks.size)
        assertEquals(4, weeks[0].daysRecorded)
        assertEquals(10_000L, weeks[0].total)
    }

    @Test
    fun `平均の分母は記録がある日だけ`() {
        // 3日しか記録が無い週。7 で割らない
        val steps = mapOf(
            "2026-08-24" to 3_000L,
            "2026-08-25" to 3_000L,
            "2026-08-26" to 3_000L,
        )
        val w = aggregate(steps, Period.WEEK).first()
        assertEquals(3, w.daysRecorded)
        assertEquals(3_000.0, w.average, 0.001)
        assertEquals(7, w.daysInPeriod)
    }

    @Test
    fun `0歩と記録された日は分母に含める`() {
        val steps = mapOf(
            "2026-08-24" to 3_000L,
            "2026-08-25" to 0L,
        )
        val w = aggregate(steps, Period.WEEK).first()
        assertEquals(2, w.daysRecorded)
        assertEquals(1_500.0, w.average, 0.001)
    }

    @Test
    fun `月と年でまとまる`() {
        val steps = mapOf(
            "2026-08-01" to 1_000L,
            "2026-08-31" to 3_000L,
            "2026-09-01" to 5_000L,
        )
        val aug = aggregate(steps, Period.MONTH).first { it.key == "2026-08" }
        assertEquals(2_000.0, aug.average, 0.001)
        assertEquals(31, aug.daysInPeriod)

        val y = aggregate(steps, Period.YEAR).first()
        assertEquals(3, y.daysRecorded)
        assertEquals(365, y.daysInPeriod)
    }

    @Test
    fun `閏年の日数を正しく数える`() {
        assertEquals(366, daysInPeriod("2024", Period.YEAR))
        assertEquals(365, daysInPeriod("2026", Period.YEAR))
        assertEquals(29, daysInPeriod("2024-02", Period.MONTH))
        assertEquals(28, daysInPeriod("2026-02", Period.MONTH))
    }

    @Test
    fun `記録が無ければ空`() {
        assertEquals(emptyList<Bucket>(), aggregate(emptyMap(), Period.WEEK))
    }

    @Test
    fun `新しい期間が先頭に来る`() {
        val steps = mapOf("2026-08-01" to 1L, "2026-09-01" to 2L)
        assertEquals("2026-09", aggregate(steps, Period.MONTH).first().key)
    }

    // --- 前期間との比較 ---

    @Test
    fun `前の期間より増えていれば正の差になる`() {
        val current = mapOf("2026-08-01" to 8_000L, "2026-08-02" to 8_000L)
        val previous = mapOf("2026-07-01" to 5_000L, "2026-07-02" to 5_000L)
        assertEquals(3_000.0, compareAverages(current, previous, 2)!!, 0.001)
    }

    @Test
    fun `期間の途中では同じ経過日数までで比べる`() {
        // 月初2日目に、先月まるごとの平均と比べると不公平になる
        val current = mapOf("2026-08-01" to 10_000L, "2026-08-02" to 10_000L)
        val previous = mapOf(
            "2026-07-01" to 10_000L,
            "2026-07-02" to 10_000L,
            "2026-07-03" to 0L,        // これを含めると前月平均が下がってしまう
            "2026-07-04" to 0L,
        )
        // 経過2日ぶんだけ切り出すので差は 0
        assertEquals(0.0, compareAverages(current, previous, 2)!!, 0.001)
    }

    @Test
    fun `どちらかに記録が無ければ比較しない`() {
        assertNull(compareAverages(emptyMap(), mapOf("2026-07-01" to 1L), 1))
        assertNull(compareAverages(mapOf("2026-08-01" to 1L), emptyMap(), 1))
    }
}
