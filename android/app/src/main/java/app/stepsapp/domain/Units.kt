package app.stepsapp.domain

import kotlin.math.roundToInt

/** 距離の単位。 */
enum class DistanceUnit(val label: String, val suffix: String) {
    KM("キロメートル", "km"),
    MILE("マイル", "mi"),
    ;

    companion object {
        val DEFAULT = KM
        fun from(name: String?): DistanceUnit = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** 身長の単位。 */
enum class HeightUnit(val label: String) {
    CM("cm"),
    FEET_INCH("フィート・インチ"),
    ;

    companion object {
        val DEFAULT = CM
        fun from(name: String?): HeightUnit = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** 体重の単位。 */
enum class WeightUnit(val label: String, val suffix: String) {
    KG("キログラム", "kg"),
    POUND("ポンド", "lb"),
    ;

    companion object {
        val DEFAULT = KG
        fun from(name: String?): WeightUnit = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

private const val KM_PER_MILE = 1.609344
private const val KG_PER_POUND = 0.45359237
private const val CM_PER_INCH = 2.54

/**
 * 換算は**表示のときだけ行う。** 保存は常に km / kg / cm のままにする。
 * 単位を切り替えるたびに保存値を変換すると、丸め誤差が蓄積して
 * 体重が少しずつずれていく。
 */
fun displayDistance(km: Double, unit: DistanceUnit): Double = when (unit) {
    DistanceUnit.KM -> km
    DistanceUnit.MILE -> km / KM_PER_MILE
}

fun displayWeight(kg: Double, unit: WeightUnit): Double = when (unit) {
    WeightUnit.KG -> kg
    WeightUnit.POUND -> kg / KG_PER_POUND
}

/** 入力された値を保存用（kg）に戻す。 */
fun storeWeight(value: Double, unit: WeightUnit): Double = when (unit) {
    WeightUnit.KG -> value
    WeightUnit.POUND -> value * KG_PER_POUND
}

fun storeHeight(value: Double, unit: HeightUnit): Double = when (unit) {
    HeightUnit.CM -> value
    HeightUnit.FEET_INCH -> value * CM_PER_INCH   // インチ単位で受け取る
}

/** cm を「5' 9"」の形にする。 */
fun formatFeetInch(cm: Int): String {
    if (cm <= 0) return ""
    val totalInch = (cm / CM_PER_INCH).roundToInt()
    return "${totalInch / 12}' ${totalInch % 12}\""
}

/**
 * 歩幅(m)。
 *
 * 直接指定があればそれを使う。実測値を入れたい人向け。
 * 未指定なら身長から推定する（係数 0.45 は一般に使われる値）。
 */
fun strideMetersOf(heightCm: Int, manualStrideCm: Int): Double = when {
    manualStrideCm > 0 -> manualStrideCm / 100.0
    heightCm > 0 -> heightCm * 0.45 / 100.0
    else -> 0.70
}
