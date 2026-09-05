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
        attemptAt: Long? = minutesAgo(15),
    ) = checkHealth(permission, sensor, hc, lastAt, attemptAt, now)

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
    fun `読み取りが長く動いていなければ止まっているとみなす`() {
        // 15分間隔のはずのジョブが半日動いていないのは明らかに異常
        val s = check(lastAt = minutesAgo(800), attemptAt = minutesAgo(800))
        assertEquals(Health.STALE, s.health)
        assertTrue(s.isProblem)
        assertEquals(800L, s.minutesSinceLastReading)
    }

    @Test
    fun `しきい値のすぐ手前では警告しない`() {
        assertEquals(Health.OK, check(attemptAt = minutesAgo(719)).health)
        assertEquals(Health.STALE, check(attemptAt = minutesAgo(720)).health)
    }

    @Test
    fun `まだ一度も動いていなければ次の実行を待つ`() {
        // 入れた直後。ジョブが回る前に警告しても利用者にできることはない
        val s = check(lastAt = null, attemptAt = null)
        assertEquals(Health.OK, s.health)
        assertEquals(null, s.minutesSinceLastReading)
    }

    @Test
    fun `朝一でまだ歩いていなくても警告しない`() {
        // 歩数センサーは on-change なので、歩かなければ記録は増えない。
        // 寝ている間ぶん記録が空いていても、読み取りが動いていれば正常
        val s = check(lastAt = minutesAgo(600), attemptAt = minutesAgo(15))
        assertEquals(Health.OK, s.health)
        assertEquals(600L, s.minutesSinceLastReading)
    }

    @Test
    fun `Doze で数時間ずれる程度では誤警告しない`() {
        // 深い Doze だと定期実行は最大6時間おきのメンテナンス窓まで先送りされる
        assertEquals(Health.OK, check(lastAt = minutesAgo(400), attemptAt = minutesAgo(400)).health)
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
