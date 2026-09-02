package app.stepsapp.domain

/** 1時間ごとの歩数。0時から23時まで、必ず24個そろえる。 */
data class HourlySteps(val byHour: List<Long>) {
    init {
        require(byHour.size == HOURS) { "24時間ぶん必要: ${byHour.size}" }
    }

    val total: Long get() = byHour.sum()

    val isEmpty: Boolean get() = total == 0L

    /**
     * よく歩いた時間帯。多い順に最大2つ。
     *
     * **全体の1割に満たない時間帯は挙げない。** どの時間帯も似たような量なら
     * 「よく歩いたのは3時台」と言われても意味がないため。
     */
    fun busiestHours(): List<Int> {
        if (isEmpty) return emptyList()
        val floor = total / 10
        return byHour.withIndex()
            .filter { it.value > floor }
            .sortedByDescending { it.value }
            .take(2)
            .map { it.index }
            .sorted()
    }

    companion object {
        const val HOURS = 24

        val EMPTY = HourlySteps(List(HOURS) { 0L })

        fun of(byHour: Map<Int, Long>): HourlySteps =
            HourlySteps(List(HOURS) { byHour[it] ?: 0L })
    }
}

/**
 * 生ログ1件。累積値と、それを記録した時間帯。
 *
 * センサーは再起動以来の累積カウンタ、Health Connect はその日の合計を入れている。
 * どちらも**増えていく値**なので、差分を取れば区間の歩数になる。
 */
data class HourReading(val hour: Int, val cumulative: Long)

/**
 * 生ログの差分から1時間ごとの歩数を組み立てる。副作用を持たない純粋関数。
 *
 * **これは近似**。読み取りは15分間隔なので、その粒度より細かくは分からず、
 * ワーカーの実行が遅れた区間はまとめて後ろの時間帯に寄る。
 * 正確な時間帯が要るなら Health Connect の1時間集計を使うこと。
 *
 * **その日の最初の読み取りは基準にしかならない。** それ以前に歩いたぶんは
 * 差分の取りようがないので落ちる。合計が採用値と一致しないのはこのため。
 *
 * 今回値が前回値を下回ったら端末の再起動とみなして 0 として扱う
 * （センサーのオフセット計算と同じ考え方）。
 *
 * @param readings 時刻順に並んだ生ログ
 */
fun hourlyFromReadings(readings: List<HourReading>): HourlySteps {
    if (readings.size < 2) return HourlySteps.EMPTY

    val byHour = LongArray(HourlySteps.HOURS)
    for (i in 1 until readings.size) {
        val previous = readings[i - 1].cumulative
        val current = readings[i]
        if (current.cumulative < previous) continue   // 再起動。この区間は数えない
        byHour[current.hour] += current.cumulative - previous
    }
    return HourlySteps(byHour.toList())
}

/** 「よく歩いたのは 8時台と 18時台」のような一文。挙げる時間帯が無ければ null。 */
fun busiestHoursText(hourly: HourlySteps): String? {
    val hours = hourly.busiestHours()
    if (hours.isEmpty()) return null
    return "よく歩いたのは " + hours.joinToString("と") { "%d時台".format(it) }
}
