package app.stepsapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HourlyStepsTest {

    @Test
    fun `累積値の差分が区間の歩数になる`() {
        val readings = listOf(
            HourReading(7, 1_000),
            HourReading(8, 1_500),
            HourReading(9, 1_800),
        )
        val hourly = hourlyFromReadings(readings)
        assertEquals(500L, hourly.byHour[8])
        assertEquals(300L, hourly.byHour[9])
        // 最初の読み取りは基準にしかならない
        assertEquals(0L, hourly.byHour[7])
    }

    @Test
    fun `同じ時間帯の読み取りは足し合わせる`() {
        // 15分間隔なので1時間に4件入る
        val readings = listOf(
            HourReading(8, 1_000),
            HourReading(8, 1_100),
            HourReading(8, 1_250),
            HourReading(9, 1_300),
        )
        assertEquals(250L, hourlyFromReadings(readings).byHour[8])
    }

    @Test
    fun `減っている区間は再起動とみなして数えない`() {
        // センサーのカウンタは再起動で 0 に戻る。差を取ると負になるので捨てる
        val readings = listOf(
            HourReading(8, 60_000),
            HourReading(9, 200),      // 再起動
            HourReading(10, 900),
        )
        val hourly = hourlyFromReadings(readings)
        assertEquals(0L, hourly.byHour[9])
        assertEquals(700L, hourly.byHour[10])
    }

    @Test
    fun `読み取りが1件以下なら差分が取れない`() {
        assertTrue(hourlyFromReadings(emptyList()).isEmpty)
        assertTrue(hourlyFromReadings(listOf(HourReading(8, 1_000))).isEmpty)
    }

    @Test
    fun `必ず24時間ぶんそろう`() {
        assertEquals(24, hourlyFromReadings(emptyList()).byHour.size)
        assertEquals(24, HourlySteps.of(mapOf(8 to 500L)).byHour.size)
        assertEquals(500L, HourlySteps.of(mapOf(8 to 500L)).byHour[8])
    }

    // --- よく歩いた時間帯 ---

    @Test
    fun `よく歩いた時間帯を多い順に2つまで挙げる`() {
        val hourly = HourlySteps.of(mapOf(7 to 300L, 8 to 4_000L, 12 to 900L, 18 to 3_000L))
        assertEquals(listOf(8, 18), hourly.busiestHours())
    }

    @Test
    fun `全体の1割に満たない時間帯は挙げない`() {
        // どこも似た量なら「よく歩いた」と言えない
        val even = HourlySteps.of((0..23).associateWith { 100L })
        assertTrue(even.busiestHours().isEmpty())
    }

    @Test
    fun `歩いていない日は何も挙げない`() {
        assertTrue(HourlySteps.EMPTY.busiestHours().isEmpty())
        assertNull(busiestHoursText(HourlySteps.EMPTY))
    }

    @Test
    fun `文言は時間帯を並べる`() {
        val hourly = HourlySteps.of(mapOf(8 to 4_000L, 18 to 3_000L))
        assertEquals("よく歩いたのは 8時台と18時台", busiestHoursText(hourly))
    }
}
