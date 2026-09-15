package app.stepsapp.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveBufferTest {

    private val day1 = "2026-09-14"
    private val day2 = "2026-09-15"

    private fun event(reading: Long, date: String, sec: Long) = LiveEvent(reading, date, sec * 1000)

    @Test
    fun `最初の値はすぐ書く`() {
        val b = LiveBuffer(60_000)
        assertEquals(listOf(event(100, day1, 0)), b.offer(event(100, day1, 0)))
    }

    @Test
    fun `間隔より短い値は間引き、間隔を過ぎたら最新の値を書く`() {
        val b = LiveBuffer(60_000)
        b.offer(event(100, day1, 0))

        assertEquals(emptyList<LiveEvent>(), b.offer(event(110, day1, 10)))
        assertEquals(emptyList<LiveEvent>(), b.offer(event(120, day1, 30)))
        assertEquals(listOf(event(130, day1, 60)), b.offer(event(130, day1, 60)))
    }

    @Test
    fun `日付が変わったら前の日の最後の値を先に書く`() {
        // 23:59:30 に受け取った値が間引かれて残ったまま日をまたいだ
        val b = LiveBuffer(60_000)
        b.offer(event(100, day1, 0))
        b.offer(event(150, day1, 30))

        assertEquals(
            listOf(event(150, day1, 30), event(152, day2, 40)),
            b.offer(event(152, day2, 40)),
        )
    }

    @Test
    fun `間引いて持っている値は drain で1回だけ出る`() {
        val b = LiveBuffer(60_000)
        b.offer(event(100, day1, 0))
        b.offer(event(120, day1, 10))

        assertEquals(listOf(event(120, day1, 10)), b.drain())
        assertEquals(emptyList<LiveEvent>(), b.drain())
    }
}
