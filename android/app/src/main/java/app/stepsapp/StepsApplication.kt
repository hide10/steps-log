package app.stepsapp

import android.app.Application
import app.stepsapp.work.StepSyncWorker
import app.stepsapp.work.UploadWorker

class StepsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 定期読み取りを登録する。既存のスケジュールがあれば維持される。
        StepSyncWorker.schedule(this)
        // 未設定なら Worker 側で何もしないので、常に登録しておいてよい
        UploadWorker.schedule(this)
    }
}
