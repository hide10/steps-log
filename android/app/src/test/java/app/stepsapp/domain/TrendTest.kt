package app.stepsapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrendTest {

    private fun pts(vararg v: Pair<String, Double>) =
        v.map { TrendPoint(it.first, it.second) }

    @Test
    fun `記録が無ければ何も出さない`() {
        val t = summarize(emptyList())
        assertNull(t.latest); assertNull(t.average); assertNull(t.change)
        assertTrue(t.points.isEmpty())
    }

    @Test
    fun `日付順に並べ替える`() {
        val t = summarize(pts("2026-08-03" to 3.0, "2026-08-01" to 1.0, "2026-08-02" to 2.0))
        assertEquals(listOf("2026-08-01", "2026-08-02", "2026-08-03"), t.points.map { it.localDate })
        assertEquals(3.0, t.latest!!, 0.001)   // 最新は最後の日付
    }

    @Test
    fun `平均と最小最大を出す`() {
        val t = summarize(pts("2026-08-01" to 60.0, "2026-08-02" to 70.0, "2026-08-03" to 65.0))
        assertEquals(65.0, t.average!!, 0.001)
        assertEquals(60.0, t.min!!, 0.001)
        assertEquals(70.0, t.max!!, 0.001)
    }

    @Test
    fun `変化は前半と後半の平均で見る`() {
        // 最初と最後の2点だけ見ると 63.0 - 63.0 = 0 になり「変化なし」と誤る。
        // 実際には後半のほうが明らかに重い
        val t = summarize(pts(
            "2026-08-01" to 63.0, "2026-08-02" to 62.0,
            "2026-08-03" to 64.0, "2026-08-04" to 63.0,
        ))
        assertTrue("後半のほうが重いのに増加と出ていない", t.change!! > 0)
    }

    @Test
    fun `日ごとの揺れに引きずられない`() {
        // 体重は日々1kg近く揺れる。たまたま軽い日と重い日を拾っても傾向は変わらない
        val t = summarize(pts(
            "2026-08-01" to 63.5, "2026-08-02" to 62.5, "2026-08-03" to 63.5, "2026-08-04" to 62.5,
            "2026-08-05" to 63.5, "2026-08-06" to 62.5, "2026-08-07" to 63.5, "2026-08-08" to 62.5,
        ))
        assertTrue("横ばいのはずが変化と判定された", kotlin.math.abs(t.change!!) < 0.2)
    }

    @Test
    fun `点が少なすぎるときは変化を出さない`() {
        // 3点以下で前半/後半に割っても意味がない
        assertNull(summarize(pts("2026-08-01" to 1.0)).change)
        assertNull(summarize(pts("2026-08-01" to 1.0, "2026-08-02" to 2.0)).change)
        assertNull(summarize(pts(
            "2026-08-01" to 1.0, "2026-08-02" to 2.0, "2026-08-03" to 3.0,
        )).change)
    }

    @Test
    fun `小さな揺れを増減と言い張らない`() {
        assertFalse(isMeaningful(0.1, threshold = 0.3))
        assertTrue(isMeaningful(0.4, threshold = 0.3))
        assertTrue(isMeaningful(-0.5, threshold = 0.3))
        assertFalse(isMeaningful(null, threshold = 0.3))
    }

    @Test
    fun `睡眠は時間と分で読ませる`() {
        assertEquals("7時間30分", formatDuration(450))
        assertEquals("6時間0分", formatDuration(360))
        assertEquals("45分", formatDuration(45))
    }
}
