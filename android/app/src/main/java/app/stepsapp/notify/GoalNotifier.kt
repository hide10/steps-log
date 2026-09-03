package app.stepsapp.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.stepsapp.MainActivity
import app.stepsapp.R
import app.stepsapp.domain.Goal
import app.stepsapp.domain.GoalNotice
import app.stepsapp.domain.goalNoticeFor
import app.stepsapp.domain.goalNoticeText
import app.stepsapp.domain.RecordKind
import app.stepsapp.domain.recordNoticeText

/**
 * 目標の達成・「あと少し」・自己記録の更新を知らせる。
 *
 * **同じ知らせは1日1回だけ。** 出した記録は日付ごとに持ち、日が変われば消える。
 * 計測異常の通知とはチャンネルを分けてある。片方だけ切りたい人がいるため。
 */
class GoalNotifier(private val context: Context) {

    private val prefs =
        context.getSharedPreferences("steps-notify", Context.MODE_PRIVATE)

    fun notifyIfNeeded(steps: Long, goal: Goal, today: String, hourOfDay: Int) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val notice = goalNoticeFor(steps, goal, hourOfDay, notifiedOn(today)) ?: return
        remember(today, notice)

        ensureChannel()
        val (title, body) = goalNoticeText(notice, steps, goal)
        val tapToOpen = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(tapToOpen)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    /**
     * 自己記録を更新したことを知らせる。
     *
     * **同じ種類は1日1回だけ。** 目標の知らせと同じ考え方。
     * チャンネルも分けていない。どちらも「歩くことの励み」の知らせで、
     * 片方だけ切りたい場面が考えにくいため。
     */
    fun notifyRecords(
        kinds: List<RecordKind>,
        steps: Long,
        streakDays: Int,
        today: String,
    ) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val done = recordsNotifiedOn(today)
        val kind = kinds.firstOrNull { it.name !in done } ?: return
        rememberRecord(today, kind)

        ensureChannel()
        val (title, body) = recordNoticeText(kind, steps, streakDays)
        val tapToOpen = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(tapToOpen)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        runCatching { manager.notify(RECORD_NOTIFICATION_ID, notification) }
    }

    private fun recordsNotifiedOn(today: String): Set<String> {
        if (prefs.getString(KEY_RECORD_DATE, "") != today) return emptySet()
        return prefs.getStringSet(KEY_RECORD_DONE, emptySet()).orEmpty()
    }

    private fun rememberRecord(today: String, kind: RecordKind) {
        val done = recordsNotifiedOn(today).toMutableSet()
        done += kind.name
        prefs.edit()
            .putString(KEY_RECORD_DATE, today)
            .putStringSet(KEY_RECORD_DONE, done)
            .apply()
    }

    /** 今日すでに出した知らせ。日付が変われば空になる。 */
    private fun notifiedOn(today: String): Set<GoalNotice> {
        if (prefs.getString(KEY_DATE, "") != today) return emptySet()
        return prefs.getStringSet(KEY_DONE, emptySet())
            .orEmpty()
            .mapNotNull { name -> GoalNotice.entries.firstOrNull { it.name == name } }
            .toSet()
    }

    private fun remember(today: String, notice: GoalNotice) {
        val done = notifiedOn(today).map { it.name }.toMutableSet()
        done += notice.name
        prefs.edit()
            .putString(KEY_DATE, today)
            .putStringSet(KEY_DONE, done)
            .apply()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "目標の進捗",
            // 邪魔にならないよう音は鳴らさない。見たときに気づけば十分
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "目標の達成、あと少し、自己記録の更新を知らせます"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "goal-progress"
        private const val NOTIFICATION_ID = 101
        private const val KEY_DATE = "goal_notice_date"
        private const val KEY_DONE = "goal_notice_done"
        private const val RECORD_NOTIFICATION_ID = 102
        private const val KEY_RECORD_DATE = "record_notice_date"
        private const val KEY_RECORD_DONE = "record_notice_done"
    }
}
