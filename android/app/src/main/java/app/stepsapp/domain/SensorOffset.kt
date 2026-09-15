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
 * 日をまたいだ分の歩数は分割できない。前回の読み取りから間もなければ
 * **前日に寄せる**（ずれても [MAX_CARRY_OVER_MS] ぶんの歩行に収まる）。
 *
 * **長く空いた日跨ぎの差分は、どの日にも入れない。** 何日ぶんの歩数が
 * 混ざっているか分からないので、前日に寄せると歩いていない日に歩数が付き、
 * 歩いた日は0から数え直しになる。
 *
 * 実機で踏んだ例（2026-09-14）: バックグラウンドからはセンサーが読めず
 * （OS の sensor access restriction）、12日の朝から14日の昼にアプリを開くまで
 * 読み取りが途切れた。その間の 9,650 歩が全部12日に入り、14日は 4,130 歩になった。
 *
 * @param state     直前の状態。初回は null
 * @param reading   センサーの現在の累積値
 * @param date      いま読み取った時点の暦日 (YYYY-MM-DD、端末ローカル)
 * @param elapsedMs 前回の読み取りからの経過時間。分からなければ null（長く空いたとみなす）
 */
fun applyReading(
    state: SensorState?,
    reading: Long,
    date: String,
    elapsedMs: Long? = null,
): SensorUpdate {
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
    } else if (elapsedMs == null || elapsedMs > MAX_CARRY_OVER_MS) {
        // 長く空いた日跨ぎ: 差分の内訳が分からないので捨て、新しい日は 0 から始める。
        // 取りこぼした分は Health Connect の読み直しで埋まる
        SensorUpdate(
            newState = SensorState(baseReading = reading, baseDate = date, accumulated = 0),
            dayTotals = mapOf(date to 0L),
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

/** 日跨ぎの差分を前日に寄せてよい、前回の読み取りからの経過時間の上限。 */
const val MAX_CARRY_OVER_MS: Long = 60 * 60 * 1000L

/**
 * 常駐して受け取り続けているセンサーの値を状態に適用する。副作用を持たない純粋関数。
 *
 * [applyReading] と違い、**日跨ぎの差分は新しい日に入れる。** 受け取り続けている間は
 * 歩けば必ずイベントが来るので、前回のイベントから日付が変わっていれば、
 * その間の差分は今回のイベントの直前に歩いた分、つまり新しい日のもの。
 *
 * 常駐を始めて最初の値は止まっていた間の差分を含むので、ここではなく [applyReading] で扱う。
 */
fun applyLiveReading(state: SensorState, reading: Long, date: String): SensorUpdate {
    require(reading >= 0) { "センサーの累積値が負: $reading" }

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
        SensorUpdate(
            newState = SensorState(baseReading = reading, baseDate = date, accumulated = delta),
            dayTotals = mapOf(state.baseDate to state.accumulated, date to delta),
            rebootDetected = rebooted,
        )
    }
}
