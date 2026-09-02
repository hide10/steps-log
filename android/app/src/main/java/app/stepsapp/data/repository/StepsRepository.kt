package app.stepsapp.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import android.util.Log
import app.stepsapp.data.local.DailyStepEntity
import app.stepsapp.data.local.HealthConnectReader
import app.stepsapp.data.local.SensorOffsetEntity
import app.stepsapp.data.local.SleepEntity
import app.stepsapp.data.local.VitalEntity
import app.stepsapp.data.local.WeightEntity
import app.stepsapp.data.local.StepCounterReader
import app.stepsapp.data.local.StepReadingRawEntity
import app.stepsapp.data.local.GoalHistoryEntity
import app.stepsapp.data.local.StepsDatabase
import app.stepsapp.domain.GoalHistory
import app.stepsapp.domain.HourReading
import app.stepsapp.domain.RecordKind
import app.stepsapp.domain.Records
import app.stepsapp.domain.HourlySteps
import app.stepsapp.domain.GoalPeriod
import app.stepsapp.domain.SensorState
import app.stepsapp.domain.StepSource
import app.stepsapp.domain.applyReading
import app.stepsapp.domain.HealthStatus
import app.stepsapp.domain.checkHealth
import app.stepsapp.domain.ChosenSteps
import app.stepsapp.domain.chooseSteps
import app.stepsapp.domain.hourlyFromReadings
import app.stepsapp.domain.currentStreak
import app.stepsapp.domain.longestStreak
import app.stepsapp.domain.newRecordsToday
import app.stepsapp.domain.records
import app.stepsapp.domain.isDivergent
import app.stepsapp.domain.pickWeight
import app.stepsapp.domain.shouldReplaceDay
import app.stepsapp.domain.totalSleepMinutes
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 歩数の読み取りと保存をまとめる。
 *
 * DI フレームワークは使わず、locapin と同じ `@Volatile` シングルトン方式で通す。
 */
class StepsRepository private constructor(context: Context) {

    private val appContext = context.applicationContext

    private val dao = StepsDatabase.getInstance(context).stepsDao()
    private val prefs = app.stepsapp.data.local.PrefsStore.getInstance(context)
    private val sensorReader = StepCounterReader(context)
    private val healthConnect = HealthConnectReader(context)

    fun sensorAvailable(): Boolean = sensorReader.isAvailable()

    fun healthConnectAvailable(): Boolean = healthConnect.isAvailable()

    suspend fun healthConnectGranted(): Boolean = healthConnect.hasPermission()

    fun observeDay(date: String): Flow<DailyStepEntity?> = dao.observeDay(date)

    fun recentDays(limit: Int): Flow<List<DailyStepEntity>> = dao.recentDays(limit)

    fun recentWeights(limit: Int): Flow<List<WeightEntity>> = dao.recentWeights(limit)

    fun recentSleep(limit: Int): Flow<List<SleepEntity>> = dao.recentSleep(limit)

    /** 今日の記録が無くても最後に眠れた分を出せるようにする。 */
    fun latestSleep(): Flow<SleepEntity?> = dao.latestSleep()

    fun latestVitals(): Flow<List<VitalEntity>> = dao.latestVitals()

    fun recentVitals(days: Int): Flow<List<VitalEntity>> = dao.recentVitals(days)

    fun today(): String = LocalDate.now().toString()

    /** その日の採用値。記録が無ければ 0。 */
    suspend fun stepsOn(date: String): Long = dao.findDay(date)?.stepCount ?: 0L

    /**
     * 計測が止まっていないかを診断する。
     *
     * 歩数計は静かに壊れるので、利用者が自分で気づけるようにする。
     */
    suspend fun healthStatus(now: Long = System.currentTimeMillis()): HealthStatus =
        checkHealth(
            hasActivityPermission = activityPermissionGranted(),
            sensorAvailable = sensorReader.isAvailable(),
            healthConnectGranted = healthConnect.hasPermission(),
            lastReadingAt = dao.lastReadingAt(),
            now = now,
        )

    private fun activityPermissionGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                appContext,
                android.Manifest.permission.ACTIVITY_RECOGNITION,
            ) == PackageManager.PERMISSION_GRANTED

    /**
     * 両方のソースを読んで日次歩数へ反映する。
     *
     * センサーは「読むたびに差分を積む」性質上、読んだ値を必ず状態へ反映する必要があるが、
     * **日次の採用値は [chooseSteps] が選んだ片方だけ**を書き込む。合算はしない。
     *
     * @return 何らかのソースを反映できたら true
     */
    suspend fun sync(now: Long = System.currentTimeMillis()): Boolean {
        val date = today()

        val sensorTotals = readSensor(date, now)
        val hcSteps = readHealthConnect(date, now)

        // センサー由来で更新が必要な日（日跨ぎの場合は前日も含む）
        val days = buildSet {
            addAll(sensorTotals.keys)
            add(date)
        }

        // 体重と睡眠も取り込む。歩数のおまけなので、失敗しても歩数の処理は止めない
        syncHealth()
        // 過去データの取り込みは重いので初回だけ。以降は当日ぶんで足りる
        if (!prefs.backfillDone) backfillFromHealthConnect(now)

        var applied = false
        for (day in days) {
            // Health Connect の値は当日ぶんしか読んでいないので、他の日はセンサーのみで判断する
            val hcForDay = if (day == date) hcSteps else null
            val sensorForDay = sensorTotals[day]

            if (day == date && isDivergent(hcForDay, sensorForDay)) {
                Log.i(TAG, "$day: HC=$hcForDay と SENSOR=$sensorForDay の乖離が大きい")
            }

            val chosen = chooseSteps(healthConnect = hcForDay, sensor = sensorForDay) ?: continue

            val existing = dao.findDay(day)
            // 歩数は1日のなかで減らない。減る方向の上書きは
            // 「信頼できるソースが一時的に読めなかった」ことを意味するので拒否する。
            if (!shouldReplaceDay(
                    existingCount = existing?.stepCount,
                    existingSource = existing?.source?.let { StepSource.from(it) },
                    incoming = chosen,
                )
            ) {
                applied = true
                continue
            }

            dao.upsertDay(
                DailyStepEntity(
                    localDate = day,
                    stepCount = chosen.stepCount,
                    source = chosen.source.name,
                    updatedAt = now,
                    syncedAt = null,
                ),
            )
            applied = true
        }
        return applied
    }

    /**
     * Health Connect から体重と睡眠を取り込む。
     *
     * 権限が無ければ読み取りが空を返すだけなので、拒否されていても害はない。
     */
    private suspend fun syncHealth(days: Long = HEALTH_LOOKBACK_DAYS) {
        val to = LocalDate.now()
        val from = to.minusDays(days)

        runCatching {
            healthConnect.readWeights(from, to)
                .groupBy { it.localDate }
                .forEach { (date, points) ->
                    // その日の最後に測った値を採る
                    pickWeight(points)?.let {
                        dao.upsertWeight(WeightEntity(date, it.kg, it.recordedAt))
                    }
                }
        }.onFailure { Log.w(TAG, "体重の取り込みに失敗した", it) }

        runCatching {
            healthConnect.readSleep(from, to)
                .groupBy { it.localDate }
                .forEach { (date, points) ->
                    // 途中で目が覚めて分かれたセッションは合算する
                    dao.upsertSleep(
                        SleepEntity(
                            localDate = date,
                            minutes = totalSleepMinutes(points),
                            startAt = points.minOf { it.startAt },
                            endAt = points.maxOf { it.endAt },
                        ),
                    )
                }
        }.onFailure { Log.w(TAG, "睡眠の取り込みに失敗した", it) }

        // 心拍・血圧・距離など、取れるものは全部取る
        runCatching {
            val vitals = healthConnect.readVitals(from, to)
            for (v in vitals) {
                dao.upsertVital(
                    VitalEntity(
                        localDate = v.localDate,
                        kind = v.kind.name,
                        value = v.value,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
            if (vitals.isNotEmpty()) {
                Log.i(TAG, "そのほかの健康データを ${vitals.size} 件取り込んだ")
            }
        }.onFailure { Log.w(TAG, "健康データの取り込みに失敗した", it) }
    }

    /** センサーを1回読み、状態を進めて「日付 -> その日の歩数」を返す。 */
    private suspend fun readSensor(date: String, now: Long): Map<String, Long> {
        val reading = sensorReader.read() ?: run {
            Log.w(TAG, "歩数センサーを読めなかった（今回はスキップ）")
            return emptyMap()
        }

        val stored = dao.sensorState()
        val previous = stored?.let {
            SensorState(
                baseReading = it.baseReading,
                baseDate = it.baseDate,
                accumulated = it.accumulated,
            )
        }

        val update = applyReading(previous, reading, date)
        if (update.rebootDetected) {
            Log.i(TAG, "端末の再起動を検知したためオフセットを打ち直した")
        }

        dao.insertRaw(
            StepReadingRawEntity(
                localDate = date,
                source = StepSource.SENSOR.name,
                stepCount = reading,
                recordedAt = now,
            ),
        )
        dao.saveSensorState(
            SensorOffsetEntity(
                baseReading = update.newState.baseReading,
                baseDate = update.newState.baseDate,
                accumulated = update.newState.accumulated,
            ),
        )
        return update.dayTotals
    }

    /**
     * Health Connect の過去データを取り込む。
     *
     * **当日しか読まないと、それ以前の記録を取りこぼす。**
     * Health Connect には権限付与前のデータも入っているため、
     * アプリを入れた時点で過去分がまとめて手に入る。
     *
     * 既存の値は [shouldReplaceDay] を通すので、
     * 大きい値を小さい値で上書きすることはない。
     */
    private suspend fun backfillFromHealthConnect(now: Long) {
        val to = LocalDate.now()
        val days = if (healthConnect.hasHistoryPermission()) {
            HISTORY_LOOKBACK_DAYS
        } else {
            // 履歴権限が無ければ、権限付与から遡れるのは既定で30日
            HEALTH_LOOKBACK_DAYS
        }
        val from = to.minusDays(days)

        runCatching {
            val range = healthConnect.readRange(from, to)
            for ((day, steps) in range) {
                val chosen = ChosenSteps(StepSource.HEALTH_CONNECT, steps)
                val existing = dao.findDay(day)
                if (!shouldReplaceDay(
                        existingCount = existing?.stepCount,
                        existingSource = existing?.source?.let { StepSource.from(it) },
                        incoming = chosen,
                    )
                ) continue

                dao.upsertDay(
                    DailyStepEntity(
                        localDate = day,
                        stepCount = steps,
                        source = StepSource.HEALTH_CONNECT.name,
                        updatedAt = now,
                        syncedAt = null,
                    ),
                )
            }
            if (range.isNotEmpty()) {
                Log.i(TAG, "Health Connect から ${range.size} 日分を取り込んだ")
            }
            // 成功したときだけ済みにする。途中で失敗したら次回やり直す
            prefs.backfillDone = true
        }.onFailure { Log.w(TAG, "過去データの取り込みに失敗した", it) }
    }

    /** Health Connect から当日の歩数を読む。取れなければ null。 */
    private suspend fun readHealthConnect(date: String, now: Long): Long? {
        val steps = healthConnect.readDay(LocalDate.parse(date)) ?: return null
        dao.insertRaw(
            StepReadingRawEntity(
                localDate = date,
                source = StepSource.HEALTH_CONNECT.name,
                stepCount = steps,
                recordedAt = now,
            ),
        )
        return steps
    }

    /**
     * 端末再起動後に呼ぶ。
     *
     * センサーの状態は消さない。次回の読み取りで「今回値 < 前回値」として
     * 再起動が検知されるのに任せることで、その日のそれまでの歩数を失わずに済む。
     */
    suspend fun onBootCompleted() {
        Log.i(TAG, "BOOT_COMPLETED: 次回の読み取りで再起動として処理される")
    }

    /**
     * その日の1時間ごとの歩数。
     *
     * **Health Connect で読めるならそちらを使う。** 1時間ちょうどで区切った集計が
     * 返るので、読み取りの間隔やワーカーの遅れに左右されない。
     * 読めなければ生ログの差分で近似する。
     *
     * 差分を取るときは**採用値と同じソースの行だけ**を使う。センサーの累積カウンタと
     * Health Connect の日合計は桁がまるで違うので、混ぜると意味のない値になる。
     */
    suspend fun hourlySteps(date: String): HourlySteps {
        healthConnect.readHourly(LocalDate.parse(date))
            ?.takeIf { !it.isEmpty }
            ?.let { return it }

        val source = dao.findDay(date)?.source ?: StepSource.SENSOR.name
        val zone = ZoneId.systemDefault()
        val readings = dao.rawOn(date)
            .filter { it.source == source }
            .map {
                HourReading(
                    hour = Instant.ofEpochMilli(it.recordedAt).atZone(zone).hour,
                    cumulative = it.stepCount,
                )
            }
        return hourlyFromReadings(readings)
    }

    // --- 自己記録 ---

    /** 全期間の記録。過去の自分と比べるための数字だけを持つ。 */
    suspend fun records(): Records {
        val all = dao.allDays().associate { it.localDate to it.stepCount }
        return records(all, goalHistory())
    }

    /**
     * 今日で自己記録を更新したか。
     *
     * **「今日を除いた過去の最高」と比べる。** 今日を含めて数えた最高と比べても、
     * 自分自身と比較することになって永遠に更新されない。
     */
    suspend fun todaysNewRecords(): List<RecordKind> {
        val today = today()
        val all = dao.allDays().associate { it.localDate to it.stepCount }
        val past = all - today
        if (past.isEmpty()) return emptyList()

        val goals = goalHistory()
        return newRecordsToday(
            previousBestDay = past.values.maxOrNull() ?: 0,
            previousLongest = longestStreak(past, goals),
            todaySteps = all[today] ?: 0,
            streakIncludingToday = currentStreak(all, LocalDate.parse(today), goals),
        )
    }

    /** 今日を含めた連続日数。通知の文面に使う。 */
    suspend fun currentStreakToday(): Int {
        val all = dao.allDays().associate { it.localDate to it.stepCount }
        return currentStreak(all, LocalDate.parse(today()), goalHistory())
    }

    // --- 目標の履歴 ---

    /**
     * 目標の履歴。**達成の判定はすべてこれを通す。**
     *
     * 履歴が空なら、いまの設定を「最初の記録日から」の1件として作って移行する。
     * 記録より前の日にはいちばん古い目標がさかのぼって適用されるので、
     * 移行で過去の達成が消えることはない。
     */
    suspend fun goalHistory(now: Long = System.currentTimeMillis()): GoalHistory {
        ensureGoalHistory(now)
        return GoalHistory.of(dao.allGoals().map { GoalPeriod(it.effectiveFrom, it.dailySteps) })
    }

    /**
     * 目標を変える。**その日から有効**で、それ以前の判定は変わらない。
     *
     * 目標を上げた瞬間に過去の達成済みの日が未達成に変わるのを防ぐのが、
     * 履歴を持つ唯一の目的。
     */
    suspend fun setGoal(
        steps: Long,
        from: String = today(),
        now: Long = System.currentTimeMillis(),
    ) {
        require(steps > 0) { "目標歩数は正の数: $steps" }

        val next = goalHistory(now).changedOn(from, steps)
        val before = dao.allGoals()
        val wanted = next.periods.associate { it.from to it.dailySteps }

        // 畳まれて要らなくなった行は消す。同じ目標が並ぶ履歴は「いつ変えたか」を読みにくくする
        before.filter { it.effectiveFrom !in wanted }.forEach { dao.deleteGoal(it.effectiveFrom) }

        val existing = before.associate { it.effectiveFrom to it.dailySteps }
        wanted.filter { existing[it.key] != it.value }
            .forEach { dao.upsertGoal(GoalHistoryEntity(it.key, it.value, now)) }

        // ウィジェットや設定画面が同期的に読む「現在の目標」も合わせる
        prefs.goalSteps = steps
    }

    /** 履歴が無い端末（版 3 までの DB）を1件だけ入れて移行する。 */
    private suspend fun ensureGoalHistory(now: Long) {
        if (dao.allGoals().isNotEmpty()) return
        val from = dao.firstRecordedDate() ?: today()
        dao.upsertGoal(GoalHistoryEntity(from, prefs.goalSteps, now))
    }

    companion object {
        private const val TAG = "StepsRepository"

        /** 体重・睡眠を遡って取り込む日数。Health Connect の既定の遡り上限に合わせる。 */
        private const val HEALTH_LOOKBACK_DAYS = 30L

        /** 履歴権限があるときに遡る日数。約3年。 */
        private const val HISTORY_LOOKBACK_DAYS = 1100L

        @Volatile
        private var instance: StepsRepository? = null

        fun getInstance(context: Context): StepsRepository =
            instance ?: synchronized(this) {
                instance ?: StepsRepository(context.applicationContext).also { instance = it }
            }
    }
}
