package app.stepsapp.domain

/**
 * 歩数の読み取り手段が使えるかの自己診断。
 *
 * 権限が外れた、センサーも Health Connect も使えない、といった
 * 読み取り手段の不足を知らせる。
 */
enum class Health {
    /** 問題なし */
    OK,

    /** 「身体活動」の権限が無い。センサーを一切読めない */
    NO_ACTIVITY_PERMISSION,

    /** どのソースからも読めていない */
    NO_SOURCE,

    /** 常駐センサーと Health Connect の読み取りがともに失敗した */
    READ_FAILURE,
}

enum class SensorReception { DISABLED, UNKNOWN, LISTENING, FAILED }
enum class HealthConnectRead { UNAVAILABLE, UNKNOWN, SUCCEEDED, FAILED }
enum class WorkerExecution { NOT_RUN, RUNNING, SUCCEEDED, FAILED }

data class DiagnosticEvidence(
    val sensorReception: SensorReception = SensorReception.UNKNOWN,
    val healthConnectRead: HealthConnectRead = HealthConnectRead.UNKNOWN,
    val workerExecution: WorkerExecution = WorkerExecution.NOT_RUN,
)

data class HealthStatus(
    val health: Health,
    /** 最後に記録できてからの経過時間(分)。記録が無ければ null */
    val minutesSinceLastReading: Long?,
) {
    val isProblem: Boolean get() = health != Health.OK
}

/**
 * 読み取り手段と確認できた失敗を判定する。副作用を持たない純粋関数。
 *
 * **「記録が増えていない」ことを異常の根拠にしてはいけない。**
 * TYPE_STEP_COUNTER は on-change センサーで、歩数が変わらないとイベントを
 * 返さないことがある（[app.stepsapp.data.local.StepCounterReader] 参照）。
 * つまり生ログの最終時刻は実質「最後に歩いた時刻」であり、寝ている間と
 * 朝の未歩行だけで数時間空く。ワーカーの実行状態も、実際に歩数を取得できたか
 * どうかを単独では示さないため、この判定ではどちらも異常の根拠にしない。
 *
 * @param hasActivityPermission ACTIVITY_RECOGNITION が許可されているか
 * @param sensorAvailable       歩数センサーがある端末か
 * @param healthConnectGranted  Health Connect から読めるか
 * @param lastReadingAt         最後に生ログを記録できた時刻(epoch millis)。表示用で判定には使わない
 * @param now                   現在時刻
 */
fun checkHealth(
    hasActivityPermission: Boolean,
    sensorAvailable: Boolean,
    healthConnectGranted: Boolean,
    lastReadingAt: Long?,
    now: Long,
    evidence: DiagnosticEvidence = DiagnosticEvidence(),
): HealthStatus {
    val elapsed = lastReadingAt?.let { (now - it) / 60_000 }

    // センサーが無い端末でも Health Connect があれば読めるので、
    // 「センサーが無い」だけでは問題としない
    val canReadSensor = hasActivityPermission && sensorAvailable
    return when {
        !hasActivityPermission && !healthConnectGranted ->
            HealthStatus(Health.NO_ACTIVITY_PERMISSION, elapsed)

        !canReadSensor && !healthConnectGranted ->
            HealthStatus(Health.NO_SOURCE, elapsed)

        // 実行間隔と最終歩行時刻では停止を証明できない。常駐の登録失敗と
        // HC の実際の読み取り失敗が揃った場合だけ、計測停止と判定する。
        ((!canReadSensor || evidence.sensorReception == SensorReception.FAILED) &&
            evidence.healthConnectRead == HealthConnectRead.FAILED) ||
            (evidence.sensorReception == SensorReception.FAILED && !healthConnectGranted) ->
            HealthStatus(Health.READ_FAILURE, elapsed)

        else -> HealthStatus(Health.OK, elapsed)
    }
}

/** 利用者に何をすればよいか伝える。原因ごとに対処が違う。 */
fun adviceFor(health: Health): String = when (health) {
    Health.OK -> ""
    Health.NO_ACTIVITY_PERMISSION ->
        "「身体活動」の権限がありません。設定から許可してください"
    Health.NO_SOURCE ->
        "歩数を読み取れる手段がありません。Health Connect を許可してください"
    Health.READ_FAILURE ->
        "常駐センサーと Health Connect の読み取りに失敗しました。アプリを開いて確認してください"
}
