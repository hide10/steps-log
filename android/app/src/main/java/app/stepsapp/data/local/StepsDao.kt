package app.stepsapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StepsDao {

    @Query("SELECT * FROM daily_steps WHERE localDate = :date")
    suspend fun findDay(date: String): DailyStepEntity?

    @Query("SELECT * FROM daily_steps ORDER BY localDate DESC LIMIT :limit")
    fun recentDays(limit: Int): Flow<List<DailyStepEntity>>

    @Query("SELECT * FROM daily_steps WHERE localDate = :date")
    fun observeDay(date: String): Flow<DailyStepEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDay(day: DailyStepEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRaw(reading: StepReadingRawEntity)

    @Query("SELECT * FROM daily_steps ORDER BY localDate")
    suspend fun allDays(): List<DailyStepEntity>

    @Query("SELECT * FROM step_readings_raw ORDER BY localDate, recordedAt")
    suspend fun allRaw(): List<StepReadingRawEntity>

    /** その日の生ログ。歩いた時間帯を差分から組み立てるのに使う。 */
    @Query("SELECT * FROM step_readings_raw WHERE localDate = :date ORDER BY recordedAt")
    suspend fun rawOn(date: String): List<StepReadingRawEntity>

    @Query("SELECT * FROM sensor_offset_state WHERE id = 1")
    suspend fun sensorState(): SensorOffsetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSensorState(state: SensorOffsetEntity)

    /** 最後に生ログを記録できた時刻。計測が止まっていないかの判定に使う。 */
    @Query("SELECT MAX(recordedAt) FROM step_readings_raw")
    suspend fun lastReadingAt(): Long?

    // --- 体重 ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWeight(weight: WeightEntity)

    @Query("SELECT * FROM body_weight ORDER BY localDate DESC LIMIT :limit")
    fun recentWeights(limit: Int): Flow<List<WeightEntity>>

    @Query("SELECT * FROM body_weight ORDER BY localDate")
    suspend fun allWeights(): List<WeightEntity>

    // --- 睡眠 ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSleep(sleep: SleepEntity)

    @Query("SELECT * FROM sleep_night ORDER BY localDate DESC LIMIT :limit")
    fun recentSleep(limit: Int): Flow<List<SleepEntity>>

    @Query("SELECT * FROM sleep_night ORDER BY localDate")
    suspend fun allSleep(): List<SleepEntity>

    // --- そのほかの健康データ ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVital(vital: VitalEntity)

    @Query("SELECT * FROM vitals WHERE localDate = :date")
    fun vitalsOn(date: String): Flow<List<VitalEntity>>

    /** 種類ごとの最新の記録。ホームに「直近の値」を出すのに使う。 */
    @Query(
        """
        SELECT * FROM vitals WHERE (kind, localDate) IN
          (SELECT kind, MAX(localDate) FROM vitals GROUP BY kind)
        """
    )
    fun latestVitals(): Flow<List<VitalEntity>>

    @Query(
        """
        SELECT * FROM vitals
         WHERE localDate >= date('now', 'localtime', '-' || :days || ' days')
         ORDER BY localDate
        """
    )
    fun recentVitals(days: Int): Flow<List<VitalEntity>>

    @Query("SELECT * FROM vitals ORDER BY localDate, kind")
    suspend fun allVitals(): List<VitalEntity>

    /** 直近の睡眠。今日の記録が無くても、最後に眠れた分を出すため。 */
    @Query("SELECT * FROM sleep_night ORDER BY localDate DESC LIMIT 1")
    fun latestSleep(): Flow<SleepEntity?>

    @Query("DELETE FROM sensor_offset_state")
    suspend fun clearSensorState()

    // --- 目標の履歴 ---

    @Query("SELECT * FROM goal_history ORDER BY effectiveFrom")
    suspend fun allGoals(): List<GoalHistoryEntity>

    @Query("SELECT * FROM goal_history ORDER BY effectiveFrom")
    fun observeGoals(): Flow<List<GoalHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGoal(goal: GoalHistoryEntity)

    @Query("DELETE FROM goal_history WHERE effectiveFrom = :from")
    suspend fun deleteGoal(from: String)

    /** 最初に記録できた日。目標の履歴をここから始めるために使う。 */
    @Query("SELECT MIN(localDate) FROM daily_steps")
    suspend fun firstRecordedDate(): String?
}
