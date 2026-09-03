package app.stepsapp.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.stepsapp.data.repository.StepsRepository
import app.stepsapp.data.local.PrefsStore
import app.stepsapp.notify.GoalNotifier
import app.stepsapp.notify.HealthNotifier
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * 定期的に歩数センサーを読んで日次歩数へ反映するワーカー。
 *
 * Foreground Service は使わない。TYPE_STEP_COUNTER はハードウェアカウンタで
 * Doze 中も数え続けているため、たまに起きて読むだけで取りこぼしが起きない。
 */
class StepSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val repo = StepsRepository.getInstance(applicationContext)
            repo.sync()
            // 計測が止まっていれば知らせる（状態が変わったときだけ鳴る）
            HealthNotifier(applicationContext).notifyIfNeeded(repo.healthStatus())
            // 目標の進捗も知らせる（達成と「あと少し」を1日1回ずつ）
            val today = repo.today()
            val notifier = GoalNotifier(applicationContext)
            val todaySteps = repo.stepsOn(today)
            notifier.notifyIfNeeded(
                steps = todaySteps,
                goal = PrefsStore.getInstance(applicationContext).goal,
                today = today,
                hourOfDay = LocalTime.now().hour,
            )
            // 自己記録の更新も知らせる。過去の自分を超えたときだけ
            val newRecords = repo.todaysNewRecords()
            if (newRecords.isNotEmpty()) {
                notifier.notifyRecords(
                    kinds = newRecords,
                    steps = todaySteps,
                    streakDays = repo.currentStreakToday(),
                    today = today,
                )
            }
            // 週のまとめは月曜の朝に1回だけ。週が終わってから確定した数字を出す
            val now = LocalTime.now()
            if (LocalDate.now().dayOfWeek == DayOfWeek.MONDAY && now.hour >= WEEKLY_HOUR) {
                repo.lastWeekReport()?.let { notifier.notifyWeekly(it) }
            }

            Result.success()
        } catch (e: Exception) {
            // センサーが一時的に読めない程度なら次回に回収されるのでリトライで十分
            Result.retry()
        }
    }

    companion object {
        /** 週のまとめを出し始める時刻。朝すぎると寝ている間に埋もれる */
        private const val WEEKLY_HOUR = 8

        private const val UNIQUE_NAME = "step-sync"

        /** WorkManager が保証する最短間隔は15分。 */
        private const val INTERVAL_MINUTES = 15L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<StepSyncWorker>(
                INTERVAL_MINUTES, TimeUnit.MINUTES,
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                // 既存のスケジュールがあれば維持する（再起動のたびに間隔がリセットされないように）
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
