package app.stepsapp.domain

import java.time.LocalDate
import java.time.YearMonth

/** 集計の単位。 */
enum class Period { DAY, WEEK, MONTH, YEAR }

/**
 * 集計結果。
 *
 * @param key          期間の識別子。週は「その週の月曜日」、月は YYYY-MM、年は YYYY
 * @param average      記録がある日だけを分母にした平均（記録日平均）
 * @param total        合計
 * @param daysRecorded 記録がある日数。0歩と記録された日も含む
 * @param daysInPeriod その期間の暦日数。欠損量を見せるために使う
 */
data class Bucket(
    val key: String,
    val average: Double,
    val total: Long,
    val daysRecorded: Int,
    val daysInPeriod: Int,
)

/**
 * その日が属する週の月曜日。
 *
 * サーバ側 `server/src/aggregate.ts` の SQL と**同じ規則**でなければならない
 * （`date(d, '-' || ((strftime('%w', d) + 6) % 7) || ' days')`）。
 * 年をまたぐ週を分断しないため、週は「月曜日の日付」で識別する。
 */
fun weekStart(date: LocalDate): LocalDate =
    date.minusDays(((date.dayOfWeek.value + 6) % 7).toLong())

/**
 * 日次の記録を期間ごとにまとめる。
 *
 * **平均の分母は記録がある日だけ。** 未計測の日を0歩として平均を下げない。
 * 0歩と記録された日は分母に含める。
 *
 * @param stepsByDate 日付 -> 歩数（記録がある日だけ）
 * @return 新しい期間が先頭に来る順
 */
fun aggregate(stepsByDate: Map<String, Long>, period: Period): List<Bucket> {
    if (stepsByDate.isEmpty()) return emptyList()

    val grouped = stepsByDate.entries.groupBy { (dateText, _) ->
        when (period) {
            Period.DAY -> dateText
            // 週だけは月曜日を求めるのに暦が要る。ほかは文字列の切り出しで足りる。
            // 全日を LocalDate.parse していたころは、期間を切り替えるたびに
            // 1,200 回の暦計算が走って画面が引っかかっていた
            Period.WEEK -> weekStart(LocalDate.parse(dateText)).toString()
            Period.MONTH -> dateText.substring(0, 7)
            Period.YEAR -> dateText.substring(0, 4)
        }
    }

    return grouped.map { (key, entries) ->
        val total = entries.sumOf { it.value }
        Bucket(
            key = key,
            average = total.toDouble() / entries.size,
            total = total,
            daysRecorded = entries.size,
            daysInPeriod = daysInPeriod(key, period),
        )
    }.sortedByDescending { it.key }
}

fun daysInPeriod(key: String, period: Period): Int = when (period) {
    Period.DAY -> 1
    Period.WEEK -> 7
    Period.MONTH -> YearMonth.parse(key).lengthOfMonth()
    Period.YEAR -> if (LocalDate.of(key.toInt(), 1, 1).isLeapYear) 366 else 365
}

/**
 * 直前の期間との比較。
 *
 * **期間の途中では「同じ経過日数までの前期間」と比べる。** 月初に
 * 「先月より大幅に少ない」と出るのは不公平で、意味のある比較にならないため。
 *
 * @param current  今の期間の記録（日付 -> 歩数）
 * @param previous 前の期間の記録
 * @param elapsedDays 今の期間の経過日数。前期間もこの日数ぶんだけ切り出して比べる
 * @return 平均の差（今 - 前）。どちらかに記録が無ければ null
 */
fun compareAverages(
    current: Map<String, Long>,
    previous: Map<String, Long>,
    elapsedDays: Int,
): Double? {
    if (current.isEmpty()) return null

    val previousClipped = previous.entries
        .sortedBy { it.key }
        .take(elapsedDays)
    if (previousClipped.isEmpty()) return null

    val currentAvg = current.values.average()
    val previousAvg = previousClipped.sumOf { it.value }.toDouble() / previousClipped.size
    return currentAvg - previousAvg
}
