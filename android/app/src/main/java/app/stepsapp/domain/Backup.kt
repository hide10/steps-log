package app.stepsapp.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** エクスポートされる1日ぶんの採用値。 */
@Serializable
data class ExportDay(
    val localDate: String,
    val stepCount: Long,
    val source: String,
    val updatedAt: Long,
)

/** エクスポートされる体重1件。 */
@Serializable
data class ExportWeight(
    val localDate: String,
    val kg: Double,
    val recordedAt: Long,
)

/** エクスポートされる睡眠1件。 */
@Serializable
data class ExportSleep(
    val localDate: String,
    val minutes: Long,
    val startAt: Long,
    val endAt: Long,
)

/**
 * エクスポートされる健康データ1件（心拍・血圧・体脂肪率など）。
 *
 * 種類は [VitalKind] の名前をそのまま入れる。知らない種類が来ても
 * 読み飛ばせるよう、文字列のまま持つ。
 */
@Serializable
data class ExportVital(
    val localDate: String,
    val kind: String,
    val value: Double,
    val updatedAt: Long,
)

/**
 * エクスポートされる目標の履歴1件。
 *
 * 目標を失うと、復元したあとで過去の達成判定が変わってしまう。
 */
@Serializable
data class ExportGoal(
    val effectiveFrom: String,
    val dailySteps: Long,
    val updatedAt: Long,
)

/** エクスポートされる生ログ1件。 */
@Serializable
data class ExportRaw(
    val localDate: String,
    val source: String,
    val stepCount: Long,
    val recordedAt: Long,
)

/**
 * バックアップ全体。
 *
 * 生ログまで含めるので、DB ファイルを丸ごとコピーするのと同等の完全性がありながら、
 * スキーマが変わっても読めて、中身を目で確認できる。
 */
@Serializable
data class Backup(
    val schemaVersion: Int = SCHEMA_VERSION,
    val appVersion: String = "",
    val timeZone: String = "",
    val exportedAt: Long = 0,
    val days: List<ExportDay> = emptyList(),
    val raw: List<ExportRaw> = emptyList(),
    val weights: List<ExportWeight> = emptyList(),
    val sleep: List<ExportSleep> = emptyList(),
    val vitals: List<ExportVital> = emptyList(),
    val goals: List<ExportGoal> = emptyList(),
) {
    companion object {
        /**
         * 3: 心拍・血圧・体脂肪率などの `vitals` を追加した。
         *    既定値が空リストなので、版 2 のバックアップもそのまま読める。
         * 4: 目標の履歴 `goals` を追加した。これが無いと、復元したあとに
         *    過去の達成判定が「いまの目標」で塗り替えられてしまう。
         */
        const val SCHEMA_VERSION = 4

        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true   // 将来フィールドが増えても古いアプリで読める
            // 既定値でも必ず書き出す。これが無いと schemaVersion が省略され、
            // 将来の読み手がバックアップの版を判別できなくなる（実機で踏んだ）
            encodeDefaults = true
        }

        fun encode(backup: Backup): String = json.encodeToString(serializer(), backup)

        fun decode(text: String): Backup = json.decodeFromString(serializer(), text)
    }
}
