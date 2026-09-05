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

    /** 権限もソースもあるのに、読み取りそのものが動いていない */
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
 * **「記録が増えていない」ことを異常の根拠にしてはいけない。**
 * TYPE_STEP_COUNTER は on-change センサーで、歩数が変わらないとイベントを
 * 返さないことがある（[app.stepsapp.data.local.StepCounterReader] 参照）。
 * つまり生ログの最終時刻は実質「最後に歩いた時刻」であり、寝ている間と
 * 朝の未歩行だけで数時間空く。かつてこれで「朝一に必ず警告が出る」という
 * 誤警告を起こした。見るべきは**読み取りに行けているか**であって、
 * その結果として歩数があったかどうかではない。
 *
 * @param hasActivityPermission ACTIVITY_RECOGNITION が許可されているか
 * @param sensorAvailable       歩数センサーがある端末か
 * @param healthConnectGranted  Health Connect から読めるか
 * @param lastReadingAt         最後に生ログを記録できた時刻(epoch millis)。表示用で判定には使わない
 * @param lastAttemptAt         前回ワーカーが読み取りに動いた時刻(epoch millis)。未実行なら null
 * @param now                   現在時刻
 * @param staleAfterMinutes     これだけ読み取りが動いていなければ止まっているとみなす
 */
fun checkHealth(
    hasActivityPermission: Boolean,
    sensorAvailable: Boolean,
    healthConnectGranted: Boolean,
    lastReadingAt: Long?,
    lastAttemptAt: Long?,
    now: Long,
    staleAfterMinutes: Long = STALE_AFTER_MINUTES,
): HealthStatus {
    val elapsed = lastReadingAt?.let { (now - it) / 60_000 }
    val sinceAttempt = lastAttemptAt?.let { (now - it) / 60_000 }

    // センサーが無い端末でも Health Connect があれば読めるので、
    // 「センサーが無い」だけでは問題としない
    val canReadSensor = hasActivityPermission && sensorAvailable
    return when {
        !hasActivityPermission && !healthConnectGranted ->
            HealthStatus(Health.NO_ACTIVITY_PERMISSION, elapsed)

        !canReadSensor && !healthConnectGranted ->
            HealthStatus(Health.NO_SOURCE, elapsed)

        // 読み取り自体が長く動いていない。省電力でワーカーが殺された疑いが濃い。
        // 一度も動いていない(null)場合は入れたばかりなので、次の実行を待てばよい
        sinceAttempt != null && sinceAttempt >= staleAfterMinutes ->
            HealthStatus(Health.STALE, elapsed)

        else -> HealthStatus(Health.OK, elapsed)
    }
}

/**
 * 12時間。読み取りは15分間隔だが、Doze に入ると定期実行は
 * メンテナンス窓（深い Doze では最大6時間おき）まで先送りされる。
 * これは正常な挙動なので、数時間の飛びで警告してはいけない。
 * 半日ぶん一度も動かないのは、省電力にアプリごと止められているとみてよい。
 */
const val STALE_AFTER_MINUTES = 720L

/** 利用者に何をすればよいか伝える。原因ごとに対処が違う。 */
fun adviceFor(health: Health): String = when (health) {
    Health.OK -> ""
    Health.NO_ACTIVITY_PERMISSION ->
        "「身体活動」の権限がありません。設定から許可してください"
    Health.NO_SOURCE ->
        "歩数を読み取れる手段がありません。Health Connect を許可してください"
    Health.STALE ->
        "しばらく歩数を読み取れていません。" +
            "バッテリー使用量が「制限なし」になっているか確認してください"
}
