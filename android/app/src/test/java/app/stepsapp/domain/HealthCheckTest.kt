package app.stepsapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthCheckTest {

    private val now = 1_800_000_000_000L
    private fun minutesAgo(m: Long) = now - m * 60_000

    private fun check(
        permission: Boolean = true,
        sensor: Boolean = true,
        hc: Boolean = true,
        lastAt: Long? = minutesAgo(10),
    ) = checkHealth(permission, sensor, hc, lastAt, now)

    @Test
    fun `全部そろって最近読めていれば問題なし`() {
        val s = check()
        assertEquals(Health.OK, s.health)
        assertFalse(s.isProblem)
        assertEquals(10L, s.minutesSinceLastReading)
    }

    @Test
    fun `権限もHealth Connectも無ければ権限の問題として報告する`() {
        assertEquals(
            Health.NO_ACTIVITY_PERMISSION,
            check(permission = false, hc = false).health,
        )
    }

    @Test
    fun `権限が無くても Health Connect があれば読めるので問題にしない`() {
        // センサーが読めなくても HC 経由で歩数は入る
        assertEquals(Health.OK, check(permission = false, hc = true).health)
    }

    @Test
    fun `センサーが無い端末でも Health Connect があれば問題にしない`() {
        assertEquals(Health.OK, check(sensor = false, hc = true).health)
    }

    @Test
    fun `センサーも Health Connect も使えなければソース無し`() {
        assertEquals(
            Health.NO_SOURCE,
            check(permission = true, sensor = false, hc = false).health,
        )
    }

    @Test
    fun `しばらく記録が無ければ止まっているとみなす`() {
        // 読み取りは15分間隔なので3時間空くのは明らかに異常
        val s = check(lastAt = minutesAgo(200))
        assertEquals(Health.STALE, s.health)
        assertTrue(s.isProblem)
        assertEquals(200L, s.minutesSinceLastReading)
    }

    @Test
    fun `しきい値のすぐ手前では警告しない`() {
        assertEquals(Health.OK, check(lastAt = minutesAgo(179)).health)
        assertEquals(Health.STALE, check(lastAt = minutesAgo(180)).health)
    }

    @Test
    fun `一度も記録できていなければ止まっている扱い`() {
        val s = check(lastAt = null)
        assertEquals(Health.STALE, s.health)
        assertEquals(null, s.minutesSinceLastReading)
    }

    @Test
    fun `夜間に数時間動かない程度で誤警告しない`() {
        // 端末を置いて寝ているだけなら、ワーカーは動いて記録は入る。
        // しきい値は「記録が無い」ことを見ているので歩数0でも警告しない
        assertEquals(Health.OK, check(lastAt = minutesAgo(30)).health)
    }

    @Test
    fun `原因ごとに違う対処を案内する`() {
        val advices = Health.entries.filter { it != Health.OK }.map { adviceFor(it) }
        assertEquals("案内が重複している", advices.size, advices.toSet().size)
        assertTrue(advices.all { it.isNotBlank() })
        assertEquals("", adviceFor(Health.OK))
    }

    @Test
    fun `バッテリー最適化に触れた案内をする`() {
        // 実機で最も詰まりやすいのがここ
        assertTrue(adviceFor(Health.STALE).contains("バッテリー"))
    }
}
