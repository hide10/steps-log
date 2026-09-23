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
        evidence: DiagnosticEvidence = DiagnosticEvidence(),
    ) = checkHealth(permission, sensor, hc, lastAt, now, evidence)

    @Test
    fun `常駐の登録と Health Connect の読み取りが共に失敗したら警告する`() {
        val evidence = DiagnosticEvidence(
            sensorReception = SensorReception.FAILED,
            healthConnectRead = HealthConnectRead.FAILED,
            workerExecution = WorkerExecution.SUCCEEDED,
        )
        assertEquals(Health.READ_FAILURE, check(evidence = evidence).health)
    }

    @Test
    fun `常駐の登録失敗だけなら Health Connect で計測できる`() {
        val evidence = DiagnosticEvidence(
            sensorReception = SensorReception.FAILED,
            healthConnectRead = HealthConnectRead.SUCCEEDED,
        )
        assertEquals(Health.OK, check(evidence = evidence).health)
    }

    @Test
    fun `Health Connect の失敗だけなら常駐センサーで計測できる`() {
        val evidence = DiagnosticEvidence(
            sensorReception = SensorReception.LISTENING,
            healthConnectRead = HealthConnectRead.FAILED,
        )
        assertEquals(Health.OK, check(evidence = evidence).health)
    }

    @Test
    fun `未歩行とワーカー遅延だけでは警告しない`() {
        val evidence = DiagnosticEvidence(workerExecution = WorkerExecution.NOT_RUN)
        assertEquals(Health.OK, check(lastAt = minutesAgo(1_440), evidence = evidence).health)
    }

    @Test
    fun `ワーカー失敗だけでは計測停止を証明できない`() {
        val evidence = DiagnosticEvidence(workerExecution = WorkerExecution.FAILED)
        assertEquals(Health.OK, check(evidence = evidence).health)
    }

    @Test
    fun `両方の失敗から片方が回復したら正常に戻る`() {
        val broken = DiagnosticEvidence(SensorReception.FAILED, HealthConnectRead.FAILED)
        val recovered = broken.copy(healthConnectRead = HealthConnectRead.SUCCEEDED)
        assertEquals(Health.READ_FAILURE, check(evidence = broken).health)
        assertEquals(Health.OK, check(evidence = recovered).health)
    }

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
    fun `長時間記録が増えなくても未歩行なら警告しない`() {
        val s = check(lastAt = minutesAgo(800))
        assertEquals(Health.OK, s.health)
        assertEquals(800L, s.minutesSinceLastReading)
    }

    @Test
    fun `まだ一度も記録が無ければ権限とソースだけで判断する`() {
        val s = check(lastAt = null)
        assertEquals(Health.OK, s.health)
        assertEquals(null, s.minutesSinceLastReading)
    }

    @Test
    fun `朝一でまだ歩いていなくても警告しない`() {
        // 歩数センサーは on-change なので、歩かなければ記録は増えない。
        // 寝ている間ぶん記録が空いていても、未歩行と故障を区別できない
        val s = check(lastAt = minutesAgo(600))
        assertEquals(Health.OK, s.health)
        assertEquals(600L, s.minutesSinceLastReading)
    }

    @Test
    fun `原因ごとに違う対処を案内する`() {
        val advices = Health.entries.filter { it != Health.OK }.map { adviceFor(it) }
        assertEquals("案内が重複している", advices.size, advices.toSet().size)
        assertTrue(advices.all { it.isNotBlank() })
        assertEquals("", adviceFor(Health.OK))
    }
}
