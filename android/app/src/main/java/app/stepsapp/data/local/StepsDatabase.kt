package app.stepsapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DailyStepEntity::class,
        StepReadingRawEntity::class,
        SensorOffsetEntity::class,
        WeightEntity::class,
        SleepEntity::class,
        VitalEntity::class,
        GoalHistoryEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class StepsDatabase : RoomDatabase() {

    abstract fun stepsDao(): StepsDao

    companion object {
        /** 体重と睡眠のテーブルを足す。既存の歩数データはそのまま残す。 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS body_weight (
                        localDate TEXT NOT NULL PRIMARY KEY,
                        kg REAL NOT NULL,
                        recordedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sleep_night (
                        localDate TEXT NOT NULL PRIMARY KEY,
                        minutes INTEGER NOT NULL,
                        startAt INTEGER NOT NULL,
                        endAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        /** そのほかの健康データの置き場を足す。既存データはそのまま残す。 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS vitals (
                        localDate TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        value REAL NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY (localDate, kind)
                    )
                    """.trimIndent(),
                )
            }
        }

        /**
         * 目標の履歴を足す。中身は空のまま作る。
         *
         * 既存の設定（現在の目標ひとつ）をどこから有効とみなすかは、
         * 最初の記録日が要るのでアプリ側で入れる（[StepsRepository.ensureGoalHistory]）。
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS goal_history (
                        effectiveFrom TEXT NOT NULL PRIMARY KEY,
                        dailySteps INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        @Volatile
        private var instance: StepsDatabase? = null

        fun getInstance(context: Context): StepsDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    StepsDatabase::class.java,
                    "steps.db",
                )
                    // 歩数の履歴を失わないよう、体重・睡眠の追加は破壊的移行にしない
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build().also { instance = it }
            }
    }
}
