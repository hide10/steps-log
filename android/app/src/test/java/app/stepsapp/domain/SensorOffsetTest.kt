package app.stepsapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorOffsetTest {

    private val day1 = "2026-08-25"
    private val day2 = "2026-08-26"

    @Test
    fun `初回の読み取りは基準を作るだけで0歩から始まる`() {
        // 端末を再起動せずに使い続けていると累積値は大きい。
        // その値を歩数として計上してはいけない。
        val u = applyReading(state = null, reading = 123_456, date = day1)

        assertEquals(0L, u.dayTotals[day1])
        assertEquals(123_456L, u.newState.baseReading)
        assertEquals(day1, u.newState.baseDate)
        assertEquals(0L, u.newState.accumulated)
        assertFalse(u.rebootDetected)
    }

    @Test
    fun `同じ日の連続した読み取りは差分が積み上がる`() {
        var s = applyReading(null, 1000, day1).newState

        var u = applyReading(s, 1100, day1)
        assertEquals(100L, u.dayTotals[day1])
        s = u.newState

        u = applyReading(s, 1350, day1)
        assertEquals(350L, u.dayTotals[day1])
        s = u.newState

        // 歩いていなければ増えない
        u = applyReading(s, 1350, day1)
        assertEquals(350L, u.dayTotals[day1])
    }

    @Test
    fun `再起動を検知したら今回値をそのまま再起動後の歩数として足し込む`() {
        var s = applyReading(null, 5000, day1).newState
        s = applyReading(s, 5200, day1).newState   // その日 200 歩

        // 再起動でカウンタが 0 に戻り、そこから 30 歩あるいた
        val u = applyReading(s, 30, day1)

        assertTrue(u.rebootDetected)
        assertEquals(230L, u.dayTotals[day1])
        assertEquals(30L, u.newState.baseReading)
    }

    @Test
    fun `再起動直後に0歩で読み取っても歩数は減らない`() {
        var s = applyReading(null, 5000, day1).newState
        s = applyReading(s, 5200, day1).newState

        val u = applyReading(s, 0, day1)

        assertTrue(u.rebootDetected)
        assertEquals(200L, u.dayTotals[day1])
    }

    @Test
    fun `日をまたいだ差分は前日に寄せて確定し新しい日は0から始まる`() {
        var s = applyReading(null, 1000, day1).newState
        s = applyReading(s, 1500, day1).newState   // day1 は 500 歩

        val u = applyReading(s, 1560, day2)

        // 日跨ぎ分の 60 歩は前日に寄せる
        assertEquals(560L, u.dayTotals[day1])
        assertEquals(0L, u.dayTotals[day2])
        assertEquals(day2, u.newState.baseDate)
        assertEquals(0L, u.newState.accumulated)
        assertEquals(1560L, u.newState.baseReading)
    }

    @Test
    fun `日跨ぎと再起動が同時に起きても歩数を失わない`() {
        var s = applyReading(null, 8000, day1).newState
        s = applyReading(s, 8300, day1).newState   // day1 は 300 歩

        // 日付が変わり、かつ再起動していて、再起動後 45 歩
        val u = applyReading(s, 45, day2)

        assertTrue(u.rebootDetected)
        assertEquals(345L, u.dayTotals[day1])
        assertEquals(0L, u.dayTotals[day2])
    }

    @Test
    fun `新しい日に入ってからの読み取りはその日に積み上がる`() {
        var s = applyReading(null, 1000, day1).newState
        s = applyReading(s, 1500, day1).newState
        s = applyReading(s, 1560, day2).newState   // 日跨ぎ、day2 は 0 から

        val u = applyReading(s, 1700, day2)
        assertEquals(140L, u.dayTotals[day2])
    }

    @Test
    fun `負の累積値は不正な入力として弾く`() {
        try {
            applyReading(null, -1, day1)
            throw AssertionError("例外が投げられなかった")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("負"))
        }
    }
}
