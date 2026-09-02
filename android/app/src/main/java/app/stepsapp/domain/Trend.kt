package app.stepsapp.domain

import kotlin.math.abs

/** 折れ線に描く1点。 */
data class TrendPoint(val localDate: String, val value: Double)

/**
 * 推移の要約。数値1点では分からない「増えているのか減っているのか」を伝える。
 */
data class Trend(
    val points: List<TrendPoint>,
    /** 直近の値 */
    val latest: Double?,
    /** 期間内の平均 */
    val average: Double?,
    val min: Double?,
    val max: Double?,
    /** 期間の前半と後半の平均の差。正なら増加傾向 */
    val change: Double?,
)

/**
 * 推移を要約する。副作用を持たない純粋関数。
 *
 * **変化は「最初と最後の2点の差」では見ない。** 体重は日ごとの揺れが大きく、
 * たまたま測った2日で1kg以上ぶれることがある。
 * 前半と後半の平均を比べれば、揺れをならした傾向が出る。
 */
fun summarize(points: List<TrendPoint>): Trend {
    val sorted = points.sortedBy { it.localDate }
    if (sorted.isEmpty()) return Trend(emptyList(), null, null, null, null, null)

    val values = sorted.map { it.value }
    val change = if (sorted.size < 4) {
        null   // 点が少なすぎると前半/後半に割っても意味がない
    } else {
        val half = sorted.size / 2
        val first = values.take(half).average()
        val second = values.drop(half).average()
        second - first
    }

    return Trend(
        points = sorted,
        latest = values.last(),
        average = values.average(),
        min = values.min(),
        max = values.max(),
        change = change,
    )
}

/** 変化が「意味のある大きさ」か。小さな揺れを増減と言い張らないため。 */
fun isMeaningful(change: Double?, threshold: Double): Boolean =
    change != null && abs(change) >= threshold

/** 睡眠を「7時間30分」の形にする。分だけの表示は読み取りにくい。 */
fun formatDuration(minutes: Long): String =
    if (minutes < 60) "${minutes}分" else "${minutes / 60}時間${minutes % 60}分"
