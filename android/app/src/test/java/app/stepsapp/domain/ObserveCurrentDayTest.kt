package app.stepsapp.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveCurrentDayTest {
    private val yesterday = "2026-09-17"
    private val today = "2026-09-18"

    @Test
    fun `スリープで暦日だけ進んでもDB更新時に当日の監視へ切り替える`() = runTest {
        var wallTime = ZonedDateTime.parse("2026-09-17T22:00:00+09:00[Asia/Tokyo]")
        val rows = MutableStateFlow(mapOf(yesterday to 1_234L))
        val seen = mutableListOf<Long?>()
        backgroundScope.launch {
            observeCurrentDay(now = { wallTime }) { date -> rows.map { it[date] } }
                .collect { seen += it }
        }
        runCurrent()
        assertEquals(listOf(1_234L), seen)

        // coroutine の待ち時間は進めず、端末の暦日だけ翌朝にする。
        wallTime = wallTime.plusHours(8)
        rows.value = rows.value + (today to 25L)
        runCurrent()
        assertEquals(25L, seen.last())

        rows.value = rows.value + (today to 40L)
        runCurrent()
        assertEquals(40L, seen.last())
    }

    @Test
    fun `翌日にDB更新が無くても復帰後1分以内に前日の値を外す`() = runTest {
        var wallTime = ZonedDateTime.parse("2026-09-17T22:00:00+09:00[Asia/Tokyo]")
        val rows = MutableStateFlow(mapOf(yesterday to 1_234L))
        val seen = mutableListOf<Long?>()
        backgroundScope.launch {
            observeCurrentDay(now = { wallTime }) { date -> rows.map { it[date] } }
                .collect { seen += it }
        }
        runCurrent()

        wallTime = wallTime.plusHours(8)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(listOf(1_234L, null), seen)
    }

    @Test
    fun `通常の日跨ぎは歩数イベントが無くても深夜に切り替える`() = runTest {
        val start = ZonedDateTime.parse("2026-09-17T23:59:59+09:00[Asia/Tokyo]")
        val rows = MutableStateFlow(mapOf(yesterday to 1_234L, today to 0L))
        val seen = mutableListOf<Long?>()
        backgroundScope.launch {
            observeCurrentDay(now = { start.plusNanos(testScheduler.currentTime * 1_000_000) }) {
                date -> rows.map { it[date] }
            }.collect { seen += it }
        }
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf(1_234L, 0L), seen)
    }

    @Test
    fun `未計測と0歩を区別し同日中の変更に追随する`() = runTest {
        val wallTime = ZonedDateTime.parse("2026-09-18T09:00:00+09:00[Asia/Tokyo]")
        val rows = MutableStateFlow(mapOf(yesterday to 1_234L))
        val seen = mutableListOf<Long?>()
        backgroundScope.launch {
            observeCurrentDay(now = { wallTime }) { date -> rows.map { it[date] } }
                .collect { seen += it }
        }
        runCurrent()
        rows.value = rows.value + (today to 0L)
        runCurrent()
        rows.value = rows.value + (today to 20L)
        runCurrent()
        assertEquals(listOf(null, 0L, 20L), seen)
    }

    @Test
    fun `タイムゾーン変更で前日に戻っても現地の日付を監視する`() = runTest {
        var wallTime = ZonedDateTime.parse("2026-09-18T01:00:00+09:00[Asia/Tokyo]")
        val rows = MutableStateFlow(mapOf(yesterday to 1_234L, today to 20L))
        val seen = mutableListOf<Long?>()
        backgroundScope.launch {
            observeCurrentDay(now = { wallTime }) { date -> rows.map { it[date] } }
                .collect { seen += it }
        }
        runCurrent()
        wallTime = wallTime.withZoneSameInstant(java.time.ZoneId.of("America/Los_Angeles"))
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(listOf(20L, 1_234L), seen)
    }

    @Test
    fun `値が変わらない再確認で重複通知せず終了時に監視を解除する`() = runTest {
        val wallTime = ZonedDateTime.parse("2026-09-18T09:00:00+09:00[Asia/Tokyo]")
        val rows = MutableStateFlow(mapOf(today to 20L))
        val seen = mutableListOf<Long?>()
        val job = backgroundScope.launch {
            observeCurrentDay(now = { wallTime }) { date -> rows.map { it[date] } }
                .collect { seen += it }
        }
        runCurrent()
        advanceTimeBy(120_000)
        runCurrent()
        assertEquals(listOf(20L), seen)
        assertEquals(1, rows.subscriptionCount.value)
        job.cancel()
        runCurrent()
        assertEquals(0, rows.subscriptionCount.value)
    }
}
