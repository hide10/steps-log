package app.stepsapp.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.stepsapp.MainActivity
import app.stepsapp.R
import app.stepsapp.domain.Health
import app.stepsapp.domain.HealthStatus
import app.stepsapp.domain.adviceFor

/**
 * 計測が止まっていることを知らせる。
 *
 * **同じ問題で何度も鳴らさない。** 状態が変わったときだけ出す。
 * 毎回鳴らすと無視されるようになり、肝心なときに気づけなくなる。
 */
class HealthNotifier(private val context: Context) {

    private val prefs =
        context.getSharedPreferences("steps-notify", Context.MODE_PRIVATE)

    fun notifyIfNeeded(status: HealthStatus) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val last = prefs.getString(KEY_LAST_HEALTH, Health.OK.name)
        if (status.health.name == last) return   // 状態が変わっていないので鳴らさない
        prefs.edit().putString(KEY_LAST_HEALTH, status.health.name).apply()

        if (status.health == Health.OK) {
            manager.cancel(NOTIFICATION_ID)
            return
        }

        ensureChannel()
        val tapToOpen = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("歩数を記録できていません")
            .setContentText(adviceFor(status.health))
            .setStyle(NotificationCompat.BigTextStyle().bigText(adviceFor(status.health)))
            .setContentIntent(tapToOpen)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "計測の異常",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "歩数を記録できていないときに知らせます"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "health-check"
        private const val NOTIFICATION_ID = 100
        private const val KEY_LAST_HEALTH = "last_health"
    }
}
