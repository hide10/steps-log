package app.stepsapp.data.local

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import app.stepsapp.domain.HourlySteps
import java.time.Duration
import app.stepsapp.domain.SleepPoint
import app.stepsapp.domain.VitalKind
import app.stepsapp.domain.VitalPoint
import app.stepsapp.domain.WeightPoint
import app.stepsapp.domain.averageOf
import app.stepsapp.domain.isCumulative
import app.stepsapp.domain.sumOf
import app.stepsapp.domain.sleepDateOf
import app.stepsapp.domain.sleepMinutes
import java.time.LocalDate
import java.time.ZoneId

/**
 * Health Connect から日次の歩数を読む。
 *
 * **重複排除について**: `aggregate()` は公式ドキュメントのとおり、
 * ユーザーが設定したアプリの優先順位リストに基づいて重複データを自動で除外し、
 * 最優先アプリの値だけを残す。したがって `dataOriginFilter` で
 * 端末由来のデータを選り分ける必要はない。
 *
 * (当初 SPN を `getCurrentDeviceDataSource()` で動的取得する方針だったが、
 *  この API は connect-client 1.1.0 / 1.2.0-alpha05 のいずれにも存在せず、
 *  そもそも aggregate 側が重複を処理するため不要だった)
 */
class HealthConnectReader(private val context: Context) {

    private val client: HealthConnectClient? by lazy {
        runCatching {
            if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
                HealthConnectClient.getOrCreate(context)
            } else {
                null
            }
        }.getOrNull()
    }

    fun isAvailable(): Boolean = client != null

    suspend fun hasPermission(): Boolean {
        val c = client ?: return false
        return runCatching {
            c.permissionController.getGrantedPermissions().contains(READ_STEPS)
        }.getOrDefault(false)
    }

    /**
     * 指定した暦日の歩数の合計を返す。
     *
     * 日の境界は端末ローカルの 00:00。UTC には変換しない。
     *
     * @return 歩数。権限が無い・利用不可・エラーなら null（センサーにフォールバックさせる）
     */
    suspend fun readDay(date: LocalDate): Long? {
        val c = client ?: return null
        if (!hasPermission()) return null

        val start = date.atStartOfDay()
        val end = date.plusDays(1).atStartOfDay()

        return runCatching {
            val response = c.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                ),
            )
            // データが1件も無い日は null が返る。これは「未計測」であって0歩ではない。
            response[StepsRecord.COUNT_TOTAL]
        }.onFailure {
            Log.w(TAG, "Health Connect の読み取りに失敗した", it)
        }.getOrNull()
    }

    /**
     * その日の歩数を**1時間ごとに**読む。
     *
     * `aggregateGroupByDuration` は connect-client 1.1.0 に実在する
     * （aar を展開して確認済み。過去に実在しない API を前提にして手戻りしている）。
     *
     * 生ログの差分と違い、読み取りの間隔に左右されない区切りが得られる。
     * ワーカーの実行が遅れても、歩いた時間帯がずれない。
     *
     * @return 時間帯ごとの歩数。読めなければ null（生ログの差分にフォールバックさせる）
     */
    suspend fun readHourly(date: LocalDate): HourlySteps? {
        val c = client ?: return null
        if (!hasPermission()) return null

        val start = date.atStartOfDay()
        val end = date.plusDays(1).atStartOfDay()

        return runCatching {
            val groups = c.aggregateGroupByDuration(
                AggregateGroupByDurationRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    timeRangeSlicer = Duration.ofHours(1),
                ),
            )
            if (groups.isEmpty()) return null
            HourlySteps.of(
                groups.mapNotNull { g ->
                    val steps = g.result[StepsRecord.COUNT_TOTAL] ?: return@mapNotNull null
                    // 記録時のオフセットで時間帯を決める（端末の設定ではなく）
                    g.startTime.atZone(g.zoneOffset).hour to steps
                }.toMap(),
            )
        }.onFailure {
            Log.w(TAG, "Health Connect の時間帯別の読み取りに失敗した", it)
        }.getOrNull()
    }

    /**
     * 期間内の歩数を**日ごとに**読む。
     *
     * Health Connect は過去のデータを持っているので、当日だけでなく
     * 遡って取り込める。権限を付与した時点から**既定で30日**遡れる
     * （それ以前も読むには PERMISSION_READ_HEALTH_DATA_HISTORY が要る）。
     *
     * @return 日付 -> 歩数。読めなかった日は含まれない
     */
    suspend fun readRange(from: LocalDate, to: LocalDate): Map<String, Long> {
        val c = client ?: return emptyMap()
        if (!hasPermission()) return emptyMap()

        val result = mutableMapOf<String, Long>()
        var date = from
        while (!date.isAfter(to)) {
            readDay(date)?.let { result[date.toString()] = it }
            date = date.plusDays(1)
        }
        return result
    }

    /** 履歴の読み取り権限があるか。あれば30日より前も読める。 */
    suspend fun hasHistoryPermission(): Boolean = hasPermission(READ_HISTORY)

    /**
     * 期間内の体重の記録を読む。
     *
     * @return 日付ごとの記録。権限が無い・エラーなら空
     */
    suspend fun readWeights(from: LocalDate, to: LocalDate): List<WeightPoint> {
        val c = client ?: return emptyList()
        if (!hasPermission(READ_WEIGHT)) return emptyList()

        return runCatching {
            c.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        from.atStartOfDay(),
                        to.plusDays(1).atStartOfDay(),
                    ),
                ),
            ).records.map { record ->
                val local = record.time.atZone(ZoneId.systemDefault()).toLocalDateTime()
                WeightPoint(
                    localDate = local.toLocalDate().toString(),
                    kg = record.weight.inKilograms,
                    recordedAt = record.time.toEpochMilli(),
                )
            }
        }.onFailure { Log.w(TAG, "体重の読み取りに失敗した", it) }.getOrDefault(emptyList())
    }

    /**
     * 期間内の睡眠を読む。
     *
     * **起床日で紐づける**（睡眠はほぼ必ず日をまたぐため）。
     */
    suspend fun readSleep(from: LocalDate, to: LocalDate): List<SleepPoint> {
        val c = client ?: return emptyList()
        if (!hasPermission(READ_SLEEP)) return emptyList()

        return runCatching {
            c.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        from.atStartOfDay(),
                        to.plusDays(1).atStartOfDay(),
                    ),
                ),
            ).records.map { record ->
                val zone = ZoneId.systemDefault()
                val start = record.startTime.atZone(zone).toLocalDateTime()
                val end = record.endTime.atZone(zone).toLocalDateTime()
                SleepPoint(
                    localDate = sleepDateOf(end),
                    startAt = record.startTime.toEpochMilli(),
                    endAt = record.endTime.toEpochMilli(),
                    minutes = sleepMinutes(start, end),
                )
            }
        }.onFailure { Log.w(TAG, "睡眠の読み取りに失敗した", it) }.getOrDefault(emptyList())
    }

    /**
     * 歩数・体重・睡眠以外の健康データをまとめて読む。
     *
     * **取れるものは全部取る。** ただし生の全サンプルは持たず、
     * 1日1件の代表値にまとめる（心拍だけで1日数千件になるため）。
     * 積み上がる量(距離・カロリー・階数・運動時間)は合計、
     * それ以外(心拍・血圧・体脂肪率など)は平均を採る。
     *
     * 種類ごとに権限を確かめるので、一部だけ拒否されていても他は取れる。
     */
    suspend fun readVitals(from: LocalDate, to: LocalDate): List<VitalPoint> {
        val c = client ?: return emptyList()
        val zone = ZoneId.systemDefault()
        val range = TimeRangeFilter.between(from.atStartOfDay(), to.plusDays(1).atStartOfDay())
        val buckets = mutableMapOf<Pair<String, VitalKind>, MutableList<Double>>()

        suspend fun <T : androidx.health.connect.client.records.Record> collect(
            type: kotlin.reflect.KClass<T>,
            permission: String,
            extract: (T) -> List<Pair<java.time.Instant, Pair<VitalKind, Double>>>,
        ) {
            if (!hasPermission(permission)) return
            runCatching {
                c.readRecords(ReadRecordsRequest(recordType = type, timeRangeFilter = range))
                    .records.forEach { rec ->
                        extract(rec).forEach { (instant, kv) ->
                            val day = instant.atZone(zone).toLocalDate().toString()
                            buckets.getOrPut(day to kv.first) { mutableListOf() }.add(kv.second)
                        }
                    }
            }.onFailure { Log.w(TAG, "${type.simpleName} の読み取りに失敗した", it) }
        }

        collect(RestingHeartRateRecord::class, READ_RESTING_HEART_RATE) {
            listOf(it.time to (VitalKind.RESTING_HEART_RATE to it.beatsPerMinute.toDouble()))
        }
        collect(HeartRateRecord::class, READ_HEART_RATE) { rec ->
            rec.samples.map { s ->
                s.time to (VitalKind.HEART_RATE_AVG to s.beatsPerMinute.toDouble())
            }
        }
        collect(BloodPressureRecord::class, READ_BLOOD_PRESSURE) {
            listOf(
                it.time to (VitalKind.BLOOD_PRESSURE_SYS to it.systolic.inMillimetersOfMercury),
                it.time to (VitalKind.BLOOD_PRESSURE_DIA to it.diastolic.inMillimetersOfMercury),
            )
        }
        collect(BodyFatRecord::class, READ_BODY_FAT) {
            listOf(it.time to (VitalKind.BODY_FAT to it.percentage.value))
        }
        collect(OxygenSaturationRecord::class, READ_OXYGEN_SATURATION) {
            listOf(it.time to (VitalKind.OXYGEN_SATURATION to it.percentage.value))
        }
        collect(DistanceRecord::class, READ_DISTANCE) {
            listOf(it.startTime to (VitalKind.DISTANCE to it.distance.inKilometers))
        }
        collect(ActiveCaloriesBurnedRecord::class, READ_CALORIES) {
            listOf(it.startTime to (VitalKind.CALORIES_TOTAL to it.energy.inKilocalories))
        }
        collect(FloorsClimbedRecord::class, READ_FLOORS) {
            listOf(it.startTime to (VitalKind.FLOORS_CLIMBED to it.floors))
        }
        collect(ExerciseSessionRecord::class, READ_EXERCISE) { rec ->
            val minutes =
                java.time.Duration.between(rec.startTime, rec.endTime).toMinutes().toDouble()
            listOf(rec.startTime to (VitalKind.EXERCISE_MINUTES to minutes))
        }

        return buckets.mapNotNull { (key, values) ->
            val (day, kind) = key
            val v = if (isCumulative(kind)) sumOf(values) else averageOf(values)
            v?.let { VitalPoint(day, kind, it) }
        }
    }

    private suspend fun hasPermission(permission: String): Boolean {
        val c = client ?: return false
        return runCatching {
            c.permissionController.getGrantedPermissions().contains(permission)
        }.getOrDefault(false)
    }

    companion object {
        private const val TAG = "HealthConnectReader"

        val READ_STEPS: String = HealthPermission.getReadPermission(StepsRecord::class)
        val READ_WEIGHT: String = HealthPermission.getReadPermission(WeightRecord::class)
        val READ_SLEEP: String = HealthPermission.getReadPermission(SleepSessionRecord::class)

        val READ_RESTING_HEART_RATE: String =
            HealthPermission.getReadPermission(RestingHeartRateRecord::class)
        val READ_HEART_RATE: String =
            HealthPermission.getReadPermission(HeartRateRecord::class)
        val READ_BLOOD_PRESSURE: String =
            HealthPermission.getReadPermission(BloodPressureRecord::class)
        val READ_BODY_FAT: String =
            HealthPermission.getReadPermission(BodyFatRecord::class)
        val READ_OXYGEN_SATURATION: String =
            HealthPermission.getReadPermission(OxygenSaturationRecord::class)
        val READ_DISTANCE: String =
            HealthPermission.getReadPermission(DistanceRecord::class)
        val READ_CALORIES: String =
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
        val READ_FLOORS: String =
            HealthPermission.getReadPermission(FloorsClimbedRecord::class)
        val READ_EXERCISE: String =
            HealthPermission.getReadPermission(ExerciseSessionRecord::class)

        /** 権限付与から30日より前のデータを読むのに要る。 */
        const val READ_HISTORY: String =
            "android.permission.health.READ_HEALTH_DATA_HISTORY"

        /**
         * まとめて要求する権限。
         *
         * 体重・睡眠は歩数のおまけなので、拒否されても歩数の記録は動き続ける
         * （各読み取りが個別に権限を確かめ、無ければ空を返す）。
         */
        val PERMISSIONS: Set<String> = setOf(
            READ_STEPS, READ_WEIGHT, READ_SLEEP, READ_HISTORY,
            READ_RESTING_HEART_RATE, READ_HEART_RATE, READ_BLOOD_PRESSURE,
            READ_BODY_FAT, READ_OXYGEN_SATURATION, READ_DISTANCE,
            READ_CALORIES, READ_FLOORS, READ_EXERCISE,
        )
    }
}
