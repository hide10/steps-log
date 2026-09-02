package app.stepsapp.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import app.stepsapp.data.repository.StepsRepository

/**
 * 端末の再起動後に定期ワーカーを組み直す。
 *
 * 再起動でセンサーの累積値が 0 に戻るが、オフセットの打ち直しは
 * 次回の読み取りで「今回値 < 前回値」として検知される。
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        StepSyncWorker.schedule(context)

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                StepsRepository.getInstance(context).onBootCompleted()
            } finally {
                pending.finish()
            }
        }
    }
}
