package app.stepsapp.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 採用値。1日1レコード。UI と集計はこのテーブルだけを見る。
 *
 * レコードが無い日 = 未計測、[stepCount] が 0 のレコード = 実際に0歩、として厳密に区別する。
 * 平均の分母の扱いがこの区別に依存する。
 */
@Entity(tableName = "daily_steps")
data class DailyStepEntity(
    @PrimaryKey val localDate: String,
    val stepCount: Long,
    val source: String,
    val updatedAt: Long,
    val syncedAt: Long? = null,
)

/**
 * 生ログ。両ソースの読み取り値をそのまま残す。
 * [DailyStepEntity] は後からこのテーブルだけで作り直せるようにしておく。
 */
@Entity(
    tableName = "step_readings_raw",
    primaryKeys = ["localDate", "source", "recordedAt"],
    indices = [Index("localDate")],
)
data class StepReadingRawEntity(
    val localDate: String,
    val source: String,
    val stepCount: Long,
    val recordedAt: Long,
)

/** センサーの再起動オフセット状態。単一行しか持たない。 */
@Entity(tableName = "sensor_offset_state")
data class SensorOffsetEntity(
    @PrimaryKey val id: Int = 1,
    val baseReading: Long,
    val baseDate: String,
    val accumulated: Long,
)

/** 体重。1日1レコード（その日の最後に測った値）。 */
@Entity(tableName = "body_weight")
data class WeightEntity(
    @PrimaryKey val localDate: String,
    val kg: Double,
    val recordedAt: Long,
)

/**
 * ひと晩の睡眠。1日1レコード。
 *
 * **起床日**で紐づける（睡眠はほぼ必ず日をまたぐため）。
 * 途中で目が覚めて分かれたセッションは合算して1件にする。
 */
@Entity(tableName = "sleep_night")
data class SleepEntity(
    @PrimaryKey val localDate: String,
    val minutes: Long,
    val startAt: Long,
    val endAt: Long,
)

/**
 * 歩数・体重・睡眠以外の健康データ。
 *
 * 種類ごとに専用テーブルを作らず1枚にまとめる。
 * 種類が増えるたびに移行を書くのは割に合わないため。
 */
@Entity(tableName = "vitals", primaryKeys = ["localDate", "kind"])
data class VitalEntity(
    val localDate: String,
    val kind: String,
    val value: Double,
    val updatedAt: Long,
)

/**
 * 目標の履歴。「いつからこの目標だったか」を1件ずつ持つ。
 *
 * 目標をひとつしか持たないと、目標を変えた瞬間に過去の達成判定が
 * 全部書き換わる。達成の判定は必ずその日に有効だった目標で行う。
 */
@Entity(tableName = "goal_history")
data class GoalHistoryEntity(
    @PrimaryKey val effectiveFrom: String,
    val dailySteps: Long,
    val updatedAt: Long,
)
