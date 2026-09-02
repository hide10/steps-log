package app.stepsapp.data.repository

import android.content.Context
import app.stepsapp.BuildConfig
import app.stepsapp.data.local.DailyStepEntity
import app.stepsapp.data.local.GoalHistoryEntity
import app.stepsapp.data.local.SleepEntity
import app.stepsapp.data.local.StepReadingRawEntity
import app.stepsapp.data.local.VitalEntity
import app.stepsapp.data.local.WeightEntity
import app.stepsapp.data.local.StepsDatabase
import app.stepsapp.domain.Backup
import app.stepsapp.domain.Csv
import app.stepsapp.domain.ExportDay
import app.stepsapp.domain.ExportGoal
import app.stepsapp.domain.ExportRaw
import app.stepsapp.domain.ExportSleep
import app.stepsapp.domain.ExportVital
import app.stepsapp.domain.ExportWeight
import app.stepsapp.domain.ImportMode
import app.stepsapp.domain.VitalKind
import app.stepsapp.domain.countChanges
import app.stepsapp.domain.resolveConflict
import java.util.TimeZone

/** インポートの実行結果。 */
data class ImportResult(
    val readCount: Int,
    val changedCount: Int,
)

/**
 * エクスポートとインポート。
 *
 * バックアップ用の JSON は生ログまで含むので完全に復元できる。
 * CSV は分析用の3カラムで、生ログは持たない。
 */
class BackupRepository private constructor(context: Context) {

    private val dao = StepsDatabase.getInstance(context).stepsDao()

    /** 完全バックアップ用の JSON を作る。 */
    suspend fun exportJson(now: Long = System.currentTimeMillis()): String {
        val backup = Backup(
            appVersion = BuildConfig.VERSION_NAME,
            timeZone = TimeZone.getDefault().id,
            exportedAt = now,
            days = dao.allDays().map { it.toExport() },
            raw = dao.allRaw().map {
                ExportRaw(it.localDate, it.source, it.stepCount, it.recordedAt)
            },
            weights = dao.allWeights().map {
                ExportWeight(it.localDate, it.kg, it.recordedAt)
            },
            sleep = dao.allSleep().map {
                ExportSleep(it.localDate, it.minutes, it.startAt, it.endAt)
            },
            vitals = dao.allVitals().map {
                ExportVital(it.localDate, it.kind, it.value, it.updatedAt)
            },
            goals = dao.allGoals().map {
                ExportGoal(it.effectiveFrom, it.dailySteps, it.updatedAt)
            },
        )
        return Backup.encode(backup)
    }

    /** 分析用の CSV を作る。 */
    suspend fun exportCsv(): String = Csv.encode(dao.allDays().map { it.toExport() })

    /** インポートせずに、何件書き換わるかだけ数える（確認ダイアログ用）。 */
    suspend fun previewImport(days: List<ExportDay>, mode: ImportMode): Int {
        val existing = dao.allDays().associate { it.localDate to it.toExport() }
        return countChanges(existing, days, mode)
    }

    /** 取り込む。生ログがあれば併せて復元する。 */
    suspend fun import(
        days: List<ExportDay>,
        raw: List<ExportRaw> = emptyList(),
        weights: List<ExportWeight> = emptyList(),
        sleep: List<ExportSleep> = emptyList(),
        vitals: List<ExportVital> = emptyList(),
        goals: List<ExportGoal> = emptyList(),
        mode: ImportMode = ImportMode.MERGE,
        now: Long = System.currentTimeMillis(),
    ): ImportResult {
        val existing = dao.allDays().associate { it.localDate to it.toExport() }

        var changed = 0
        for (incoming in days) {
            val resolved = resolveConflict(existing[incoming.localDate], incoming, mode) ?: continue
            dao.upsertDay(
                DailyStepEntity(
                    localDate = resolved.localDate,
                    stepCount = resolved.stepCount,
                    source = resolved.source,
                    updatedAt = now,
                    syncedAt = null,
                ),
            )
            changed++
        }

        // 生ログは主キーが (日付, ソース, 記録時刻) なので、重ねて入れても重複しない
        for (r in raw) {
            dao.insertRaw(
                StepReadingRawEntity(
                    localDate = r.localDate,
                    source = r.source,
                    stepCount = r.stepCount,
                    recordedAt = r.recordedAt,
                ),
            )
        }

        // 知らない種類は捨てる。新しい版のアプリで増えた種類を、古い版が
        // 意味の分からないまま DB に貯め込むのを避ける
        for (v in vitals) {
            if (VitalKind.from(v.kind) == null) continue
            dao.upsertVital(VitalEntity(v.localDate, v.kind, v.value, v.updatedAt))
        }

        // 体重と睡眠は日付が主キーなので、そのまま入れれば重複しない
        for (w in weights) dao.upsertWeight(WeightEntity(w.localDate, w.kg, w.recordedAt))
        for (s in sleep) {
            dao.upsertSleep(SleepEntity(s.localDate, s.minutes, s.startAt, s.endAt))
        }

        // 目標の履歴。同じ日の行がぶつかったら、あとから書かれたほうを採る。
        // 歩数と違って「大きいほうが正しい」とは言えないため
        val existingGoals = dao.allGoals().associateBy { it.effectiveFrom }
        for (g in goals) {
            if (g.dailySteps <= 0) continue
            val current = existingGoals[g.effectiveFrom]
            if (current != null && current.updatedAt >= g.updatedAt) continue
            dao.upsertGoal(GoalHistoryEntity(g.effectiveFrom, g.dailySteps, g.updatedAt))
        }

        return ImportResult(readCount = days.size, changedCount = changed)
    }

    private fun DailyStepEntity.toExport() =
        ExportDay(localDate, stepCount, source, updatedAt)

    companion object {
        @Volatile
        private var instance: BackupRepository? = null

        fun getInstance(context: Context): BackupRepository =
            instance ?: synchronized(this) {
                instance ?: BackupRepository(context.applicationContext).also { instance = it }
            }
    }
}
