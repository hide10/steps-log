package app.stepsapp.domain

/**
 * 計測が止まっていないかの自己診断。
 *
 * **歩数計は静かに壊れる。** 権限が外れた、省電力でアプリが眠らされた、
 * センサーが読めない ── どれも「アプリは起動するが数字が増えない」という
 * 形で現れるので、利用者は気づけない。
 * 実際このアプリでも「Health Connect が読めずセンサーが小さい値で上書きして
 * 歩数が消える」というバグを踏んでいる。だから明示的に警告する。
 */
enum class Health {
    /** 問題なし */
    OK,

    /** 「身体活動」の権限が無い。センサーを一切読めない */
    NO_ACTIVITY_PERMISSION,

    /** どのソースからも読めていない */
    NO_SOURCE,

    /** 権限もソースもあるのに、しばらく記録が増えていない */
    STALE,
}

data class HealthStatus(
    val health: Health,
    /** 最後に記録できてからの経過時間(分)。記録が無ければ null */
    val minutesSinceLastReading: Long?,
) {
    val isProblem: Boolean get() = health != Health.OK
}

/**
 * 計測が正常に回っているかを判定する。副作用を持たない純粋関数。
 *
 * @param hasActivityPermission ACTIVITY_RECOGNITION が許可されているか
 * @param sensorAvailable       歩数センサーがある端末か
 * @param healthConnectGranted  Health Connect から読めるか
 * @param lastReadingAt         最後に生ログを記録できた時刻(epoch millis)。無ければ null
 * @param now                   現在時刻
 * @param staleAfterMinutes     これだけ記録が無ければ止まっているとみなす
 */
fun checkHealth(
    hasActivityPermission: Boolean,
    sensorAvailable: Boolean,
    healthConnectGranted: Boolean,
    lastReadingAt: Long?,
    now: Long,
    staleAfterMinutes: Long = STALE_AFTER_MINUTES,
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

        // 一度も記録できていない、または長く途絶えている
        elapsed == null || elapsed >= staleAfterMinutes ->
            HealthStatus(Health.STALE, elapsed)

        else -> HealthStatus(Health.OK, elapsed)
    }
}

/**
 * 3時間。読み取りは15分間隔なので、これだけ空くのは明らかに異常。
 * 短すぎると（端末を置いて寝ているだけで）誤警告になるため余裕を持たせる。
 */
const val STALE_AFTER_MINUTES = 180L

/** 利用者に何をすればよいか伝える。原因ごとに対処が違う。 */
fun adviceFor(health: Health): String = when (health) {
    Health.OK -> ""
    Health.NO_ACTIVITY_PERMISSION ->
        "「身体活動」の権限がありません。設定から許可してください"
    Health.NO_SOURCE ->
        "歩数を読み取れる手段がありません。Health Connect を許可してください"
    Health.STALE ->
        "しばらく歩数を記録できていません。" +
            "バッテリー使用量が「制限なし」になっているか確認してください"
}
