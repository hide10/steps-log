package app.stepsapp.domain

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** その日の体重(kg)。1日に複数の記録があれば最後のものを採る。 */
data class WeightPoint(val localDate: String, val kg: Double, val recordedAt: Long)

/** ひと晩の睡眠。 */
data class SleepPoint(
    /** 紐づける日付。**起床日**を使う（下記参照） */
    val localDate: String,
    val startAt: Long,
    val endAt: Long,
    val minutes: Long,
)

/**
 * 睡眠をどの日に紐づけるか。
 *
 * 睡眠はほぼ必ず日をまたぐので、開始日で数えると「23:30 に寝た日」と
 * 「0:30 に寝た日」で1日ずれてしまう。**起床日に紐づける**のが一般的な慣習で、
 * 「今朝までに何時間寝たか」という感覚にも合う。
 */
fun sleepDateOf(end: LocalDateTime): String = end.toLocalDate().toString()

fun sleepMinutes(start: LocalDateTime, end: LocalDateTime): Long =
    Duration.between(start, end).toMinutes().coerceAtLeast(0)

/**
 * 同じ日に複数ある体重の記録から採用値を選ぶ。
 *
 * 最後に測ったものを採る。体重計に何度も乗ったときは最後が落ち着いた値であることが多い。
 */
fun pickWeight(points: List<WeightPoint>): WeightPoint? =
    points.maxByOrNull { it.recordedAt }

/**
 * ひと晩ぶんの睡眠時間。
 *
 * 途中で目が覚めてセッションが分かれることがあるので、
 * **同じ起床日のセッションは合算する**。
 */
fun totalSleepMinutes(points: List<SleepPoint>): Long = points.sumOf { it.minutes }

/** 直近の体重。記録が無ければ null。 */
fun latestWeight(byDate: Map<String, Double>): Pair<String, Double>? =
    byDate.maxByOrNull { it.key }?.let { it.key to it.value }

/**
 * 体重の変化。今の値と、指定日数前までで最も古い記録との差。
 *
 * @return 差(kg)。比べる相手が無ければ null
 */
fun weightChange(byDate: Map<String, Double>, today: LocalDate, days: Long): Double? {
    val latest = latestWeight(byDate) ?: return null
    val since = today.minusDays(days).toString()
    val older = byDate.keys
        .filter { it >= since && it < latest.first }
        .minOrNull() ?: return null
    return latest.second - byDate.getValue(older)
}
