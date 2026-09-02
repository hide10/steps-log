package app.stepsapp.domain

/** 1日の歩数として採用する値と、その取得元。 */
data class ChosenSteps(
    val source: StepSource,
    val stepCount: Long,
)

/**
 * その日の採用値を決める。副作用を持たない純粋関数。
 *
 * **2つのソースを決して合算しない。** 必ずどちらか一方を選ぶ。
 * これが重複カウントを構造的に防ぐ唯一の砦になっている。
 *
 * **そのうえで大きいほうを採る。** 歩数の食い違いはほぼ「取りこぼし」なので、
 * 大きい値のほうが実態に近い（不変条件2やインポートの MERGE と同じ考え方）。
 *
 * 実機で踏んだ例（2026-08-31）: センサーが 10,250 歩を数えている日に、
 * Health Connect にデータを書く側の同期が遅れて 5,045 しか返らなかった。
 * 当時は Health Connect 固定優先だったため、その日の表示は半分のままだった。
 * 「読めない」だけでなく「読めるが少ない」場合があることが分かっていなかった。
 *
 * 同数なら Health Connect を採る。OS 側の集計なので、
 * ウェアラブルなど端末以外で歩いた分も含んでいるため。
 *
 * @param healthConnect Health Connect から得た値。取得できなければ null
 * @param sensor        センサーから得た値。取得できなければ null
 * @return 採用値。どちらも取得できなければ null
 */
fun chooseSteps(healthConnect: Long?, sensor: Long?): ChosenSteps? = when {
    healthConnect == null && sensor == null -> null
    healthConnect == null -> ChosenSteps(StepSource.SENSOR, sensor!!)
    sensor == null -> ChosenSteps(StepSource.HEALTH_CONNECT, healthConnect)
    sensor > healthConnect -> ChosenSteps(StepSource.SENSOR, sensor)
    else -> ChosenSteps(StepSource.HEALTH_CONNECT, healthConnect)
}

/** 2つのソースの乖離が大きいか。自動修正はせず、ログに残して気づけるようにするだけ。 */
fun isDivergent(healthConnect: Long?, sensor: Long?, ratio: Double = 0.2): Boolean {
    if (healthConnect == null || sensor == null) return false
    val larger = maxOf(healthConnect, sensor)
    if (larger == 0L) return false
    val diff = kotlin.math.abs(healthConnect - sensor)
    return diff.toDouble() / larger.toDouble() > ratio
}

/**
 * 同じ日の記録を差し替えてよいか。副作用を持たない純粋関数。
 *
 * **歩数は1日のなかで減らない。** だから今回の値が保存済みより小さいなら、
 * それは「本当に減った」のではなく「より信頼できるソースが一時的に読めなかった」ことを意味する。
 * そのまま上書きすると歩数が静かに消えるので、減る方向の書き換えは拒否する。
 *
 * 実機で踏んだ例: READ_HEALTH_DATA_IN_BACKGROUND が未許可だと、
 * バックグラウンド実行時に Health Connect が読めず SENSOR にフォールバックする。
 * SENSOR はその日の途中から数え始めているため値が小さく、
 * 保存済みの Health Connect の値を消してしまっていた。
 *
 * 逆に、この規則なら Health Connect の許可が恒久的に外れた場合も、
 * センサーの値が保存済みを追い越した時点で自然に主導権が移る。
 */
fun shouldReplaceDay(
    existingCount: Long?,
    existingSource: StepSource?,
    incoming: ChosenSteps,
): Boolean {
    if (existingCount == null) return true
    if (incoming.stepCount > existingCount) return true

    // 同数なら、より信頼できるソースの記録に貼り替えるときだけ書き換える
    if (incoming.stepCount == existingCount) {
        return incoming.source == StepSource.HEALTH_CONNECT &&
            existingSource != StepSource.HEALTH_CONNECT
    }
    return false
}
