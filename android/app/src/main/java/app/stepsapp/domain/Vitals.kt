package app.stepsapp.domain

/**
 * 歩数以外の健康データ。
 *
 * どれも「その日の代表値」を1日1レコードで持つ。生の全サンプルは持たない
 * （心拍だけで1日数千件になり、端末にも Drive にも重すぎる）。
 */
enum class VitalKind(val label: String, val unit: String) {
    RESTING_HEART_RATE("安静時心拍", "bpm"),
    HEART_RATE_AVG("平均心拍", "bpm"),
    BLOOD_PRESSURE_SYS("最高血圧", "mmHg"),
    BLOOD_PRESSURE_DIA("最低血圧", "mmHg"),
    BODY_FAT("体脂肪率", "%"),
    OXYGEN_SATURATION("血中酸素", "%"),
    DISTANCE("距離", "km"),
    CALORIES_TOTAL("総消費カロリー", "kcal"),
    FLOORS_CLIMBED("上った階数", "階"),
    EXERCISE_MINUTES("運動時間", "分"),
    ;

    companion object {
        fun from(raw: String): VitalKind? = entries.firstOrNull { it.name == raw }
    }
}

/** 1日1件の測定値。 */
data class VitalPoint(
    val localDate: String,
    val kind: VitalKind,
    val value: Double,
)

/**
 * 表示用に丸める。単位ごとに意味のある桁が違う。
 * 心拍を小数第1位まで出しても意味がないし、体脂肪率は整数だと粗い。
 */
fun formatVital(kind: VitalKind, value: Double): String = when (kind) {
    VitalKind.DISTANCE -> "%.2f".format(value)
    VitalKind.BODY_FAT, VitalKind.OXYGEN_SATURATION -> "%.1f".format(value)
    else -> "%.0f".format(value)
}

/** その日の代表値。同じ日に複数あれば平均を採る（心拍など連続測定するもの向け）。 */
fun averageOf(values: List<Double>): Double? =
    if (values.isEmpty()) null else values.sum() / values.size

/** 合計を採る（距離・カロリー・階数など積み上がるもの向け）。 */
fun sumOf(values: List<Double>): Double? =
    if (values.isEmpty()) null else values.sum()

/** その種類は合計すべきか（積み上がる量か）。 */
fun isCumulative(kind: VitalKind): Boolean = when (kind) {
    VitalKind.DISTANCE,
    VitalKind.CALORIES_TOTAL,
    VitalKind.FLOORS_CLIMBED,
    VitalKind.EXERCISE_MINUTES -> true
    else -> false
}
