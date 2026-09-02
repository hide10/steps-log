package app.stepsapp.domain

/**
 * 歩数から距離・消費カロリーを推定する。
 *
 * どちらも**推定**であって実測ではない。歩幅も消費カロリーも個人差が大きいので、
 * 表示するときは概算だと分かる見せ方にすること。
 */

/** 身長が未設定のときに使う歩幅(m)。成人の平均的な値。 */
private const val DEFAULT_STRIDE_M = 0.70

/**
 * 歩幅(m)を身長(cm)から推定する。一般に使われる係数 0.45 を用いる。
 * 身長が未設定(0以下)なら既定値を返す。
 */
fun strideMeters(heightCm: Int): Double =
    if (heightCm <= 0) DEFAULT_STRIDE_M else heightCm * 0.45 / 100.0

/** 距離(km)。 */
fun distanceKm(steps: Long, heightCm: Int): Double =
    steps * strideMeters(heightCm) / 1000.0

/**
 * 消費カロリー(kcal)の推定。
 *
 * 歩行の概算式として広く使われる「体重(kg) × 距離(km) × 0.5」を用いる。
 * 体重が未設定なら null を返す（当てずっぽうの数字を出さない）。
 */
fun caloriesKcal(steps: Long, heightCm: Int, weightKg: Int): Double? {
    if (weightKg <= 0) return null
    return weightKg * distanceKm(steps, heightCm) * 0.5
}
