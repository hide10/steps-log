package app.stepsapp.domain

/**
 * TYPE_STEP_COUNTER の累積値から日次歩数を割り出すための状態。
 *
 * TYPE_STEP_COUNTER は「最後の再起動以降の累積歩数」を返すため、
 * 端末を再起動すると値が 0 から数え直しになる。
 * そこで「最後に読んだ値」を常に基準として持ち、読むたびに差分を
 * その日の累計へ足し込んでいく(rebase 方式)。
 *
 * @param baseReading 最後に読み取ったセンサーの累積値
 * @param baseDate    その読み取りが属する暦日 (YYYY-MM-DD、端末ローカル)
 * @param accumulated baseDate のこれまでの歩数
 */
data class SensorState(
    val baseReading: Long,
    val baseDate: String,
    val accumulated: Long,
)

/**
 * 1回の読み取りを適用した結果。
 *
 * @param newState        次回の基準となる状態
 * @param dayTotals       更新すべき「日付 -> その日の歩数」
 * @param rebootDetected  再起動を検知したか(ログ用)
 */
data class SensorUpdate(
    val newState: SensorState,
    val dayTotals: Map<String, Long>,
    val rebootDetected: Boolean,
)

/**
 * センサーの読み取り値を状態に適用する。副作用を持たない純粋関数。
 *
 * 日をまたいだ分の歩数は分割できないため、**前日に寄せる**。
 * 読み取りは 15 分間隔なので誤差は最大でも 15 分程度の歩行分に収まる。
 *
 * @param state   直前の状態。初回は null
 * @param reading センサーの現在の累積値
 * @param date    いま読み取った時点の暦日 (YYYY-MM-DD、端末ローカル)
 */
fun applyReading(state: SensorState?, reading: Long, date: String): SensorUpdate {
    require(reading >= 0) { "センサーの累積値が負: $reading" }

    // 初回。この時点より前の歩数は取得しようがないので 0 から始める。
    if (state == null) {
        return SensorUpdate(
            newState = SensorState(baseReading = reading, baseDate = date, accumulated = 0),
            dayTotals = mapOf(date to 0L),
            rebootDetected = false,
        )
    }

    // 今回値が前回値を下回っていたら再起動とみなす。
    // 再起動後はカウンタが 0 から数え直しなので、今回値そのものが再起動後の歩数。
    val rebooted = reading < state.baseReading
    val delta = if (rebooted) reading else reading - state.baseReading

    return if (date == state.baseDate) {
        val acc = state.accumulated + delta
        SensorUpdate(
            newState = state.copy(baseReading = reading, accumulated = acc),
            dayTotals = mapOf(date to acc),
            rebootDetected = rebooted,
        )
    } else {
        // 日跨ぎ: 差分は前日に寄せて確定させ、新しい日は 0 から始める。
        val previousTotal = state.accumulated + delta
        SensorUpdate(
            newState = SensorState(baseReading = reading, baseDate = date, accumulated = 0),
            dayTotals = mapOf(state.baseDate to previousTotal, date to 0L),
            rebootDetected = rebooted,
        )
    }
}
