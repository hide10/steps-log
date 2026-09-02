package app.stepsapp.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.stepsapp.data.remote.UploadResult
import app.stepsapp.data.repository.UploadRepository
import java.util.concurrent.TimeUnit

/**
 * 1日1回 steps.json を、選ばれたフォルダ（ドライブなど）へ書き出す。
 *
 * フォルダの権限は永続化されているので、バックグラウンドでも書ける。
 * 未設定なら何もしない。
 */
class UploadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = UploadRepository.getInstance(applicationContext)
        if (!repo.configured) return Result.success()

        return try {
            when (repo.upload()) {
                is UploadResult.Success -> Result.success()
                is UploadResult.Failure -> Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "steps-upload"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UploadWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
