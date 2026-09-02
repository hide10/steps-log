package app.stepsapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class HealthTest {

    private fun dt(s: String) = LocalDateTime.parse(s)

    // --- 睡眠の日付の紐づけ ---

    @Test
    fun `睡眠は起床日に紐づける`() {
        // 25日の23時に寝て26日の7時に起きた → 26日の睡眠
        assertEquals("2026-08-26", sleepDateOf(dt("2026-08-26T07:00")))
    }

    @Test
    fun `深夜に寝ても起床日は変わらない`() {
        // 26日の0時半に寝て26日の8時に起きた → やはり26日
        // 開始日で数えるとここが1日ずれる
        assertEquals("2026-08-26", sleepDateOf(dt("2026-08-26T08:00")))
    }

    @Test
    fun `睡眠時間を分で数える`() {
        assertEquals(480L, sleepMinutes(dt("2026-08-25T23:00"), dt("2026-08-26T07:00")))
        assertEquals(450L, sleepMinutes(dt("2026-08-26T00:30"), dt("2026-08-26T08:00")))
    }

    @Test
    fun `終了が開始より前でも負にならない`() {
        // データが壊れていても表示が壊れないように
        assertEquals(0L, sleepMinutes(dt("2026-08-26T08:00"), dt("2026-08-26T07:00")))
    }

    @Test
    fun `途中で目が覚めた分は合算する`() {
        val night = listOf(
            SleepPoint("2026-08-26", 0, 0, 300),
            SleepPoint("2026-08-26", 0, 0, 120),
        )
        assertEquals(420L, totalSleepMinutes(night))
    }

    @Test
    fun `記録が無ければ睡眠は0分`() {
        assertEquals(0L, totalSleepMinutes(emptyList()))
    }

    // --- 体重 ---

    @Test
    fun `同じ日に何度も測ったら最後の値を採る`() {
        val points = listOf(
            WeightPoint("2026-08-26", 70.5, recordedAt = 100),
            WeightPoint("2026-08-26", 70.1, recordedAt = 300),
            WeightPoint("2026-08-26", 70.8, recordedAt = 200),
        )
        assertEquals(70.1, pickWeight(points)!!.kg, 0.001)
    }

    @Test
    fun `記録が無ければ体重は無し`() {
        assertNull(pickWeight(emptyList()))
    }

    @Test
    fun `直近の体重を取り出す`() {
        val byDate = mapOf("2026-08-20" to 71.0, "2026-08-26" to 70.2, "2026-08-23" to 70.6)
        val (date, kg) = latestWeight(byDate)!!
        assertEquals("2026-08-26", date)
        assertEquals(70.2, kg, 0.001)
    }

    @Test
    fun `体重の変化を出す`() {
        val byDate = mapOf("2026-08-20" to 71.0, "2026-08-26" to 70.2)
        // 30日前まで遡って最も古い記録(71.0)との差
        assertEquals(-0.8, weightChange(byDate, LocalDate.parse("2026-08-26"), 30)!!, 0.001)
    }

    @Test
    fun `比べる相手が無ければ変化は出さない`() {
        val byDate = mapOf("2026-08-26" to 70.2)
        assertNull(weightChange(byDate, LocalDate.parse("2026-08-26"), 30))
    }

    @Test
    fun `期間より古い記録は比較に使わない`() {
        val byDate = mapOf("2026-01-01" to 80.0, "2026-08-26" to 70.2)
        // 30日前より古いので比較対象にならない
        assertNull(weightChange(byDate, LocalDate.parse("2026-08-26"), 30))
    }
}
