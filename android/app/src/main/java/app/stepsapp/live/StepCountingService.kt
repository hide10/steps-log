package app.stepsapp.live

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.stepsapp.MainActivity
import app.stepsapp.R
import app.stepsapp.data.local.PrefsStore
import app.stepsapp.data.repository.StepsRepository
import app.stepsapp.domain.LiveBuffer
import app.stepsapp.domain.LiveEvent
import app.stepsapp.domain.SensorReception
import app.stepsapp.domain.observeCurrentDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

/**
 * 常駐して歩数センサーを受け取り続ける。
 *
 * **バックグラウンドのワーカーからはセンサーを読めない。** OS の sensor access restriction で
 * イベントが止められる（2026-09-15、Pixel 11 Pro の dumpsys sensorservice で確認）。
 * health 型の Foreground Service で動いている間は止められないので、ここで受け取る。
 *
 * 受け取り続けていれば「歩けば必ずイベントが来る」ので、日跨ぎの差分を正しい日に
 * 振り分けられる（[app.stepsapp.domain.applyLiveReading]）。
 *
 * 常駐の通知は消せないので、せめて見る価値のあるもの（今日の歩数）にする。
 * 設定で切れる（[PrefsStore.liveCounting]、既定オン）。
 */
class StepCountingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val events = Channel<LiveEvent>(Channel.UNLIMITED)
    private var notificationJob: Job? = null
    private var listening = false

    private val repo by lazy { StepsRepository.getInstance(this) }
    private val prefs by lazy { PrefsStore.getInstance(this) }

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val value = event.values.firstOrNull()?.toLong() ?: return
            // 日付は受け取った時点で決める。書き込みが遅れても日がずれないように
            events.trySend(
                LiveEvent(value, LocalDate.now().toString(), System.currentTimeMillis()),
            )
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 開始を頼まれたら数秒以内に startForeground しないと落とされるので、何より先にやる
        val foreground = runCatching {
            ensureChannel()
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(prefs.cachedStepsFor(LocalDate.now().toString())),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
                } else {
                    0
                },
            )
        }.onFailure { Log.w(TAG, "常駐を始められなかった", it) }.isSuccess

        if (!foreground || !prefs.liveCounting) {
            if (!foreground && prefs.liveCounting) reception = SensorReception.FAILED
            stopSelf()
            return START_NOT_STICKY
        }
        if (!listening) startListening()
        return START_STICKY
    }

    private fun startListening() {
        val manager = getSystemService(SensorManager::class.java)
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (manager == null || sensor == null ||
            !manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        ) {
            reception = SensorReception.FAILED
            Log.w(TAG, "歩数センサーを受け取れないので常駐しない")
            stopSelf()
            return
        }
        listening = true
        running = true
        reception = SensorReception.LISTENING
        scope.launch { consume() }
        notificationJob = scope.launch { followToday() }
    }

    override fun onDestroy() {
        if (listening) {
            getSystemService(SensorManager::class.java)?.unregisterListener(listener)
        }
        running = false
        if (listening) reception = SensorReception.UNKNOWN
        notificationJob?.cancel()
        // 間引いて持っている値は、consume() が閉じたのを見て書いてから終わる
        events.close()
        super.onDestroy()
    }

    /** 受け取った値を間引きながら順に記録する。順番が入れ替わると差分が狂うので1本で回す。 */
    private suspend fun consume() {
        val buffer = LiveBuffer(WRITE_INTERVAL_MS)
        // 常駐を始めて最初の値は、止まっていた間の差分を含む
        var resumed = true

        suspend fun write(batch: List<LiveEvent>) {
            for (e in batch) {
                runCatching { repo.recordLiveReading(e.reading, e.date, e.at, resumed) }
                    .onSuccess { resumed = false }
                    .onFailure { Log.w(TAG, "歩数の記録に失敗した", it) }
            }
        }

        while (true) {
            val result = withTimeoutOrNull(WRITE_INTERVAL_MS) { events.receiveCatching() }
            when {
                // しばらく歩いていない。持っている値を書いて通知を追いつかせる
                result == null -> write(buffer.drain())
                result.isClosed -> {
                    write(buffer.drain())
                    return
                }
                else -> write(buffer.offer(result.getOrThrow()))
            }
        }
    }

    /** 通知の歩数を今日の採用値に合わせ続ける。日付が変わったら今日の行を見直す。 */
    private suspend fun followToday() {
        val manager = NotificationManagerCompat.from(this)
        observeCurrentDay(observeDay = repo::observeDay).collect { day ->
            runCatching {
                manager.notify(NOTIFICATION_ID, buildNotification(day?.stepCount ?: 0))
            }.onFailure { Log.w(TAG, "歩数の通知を更新できなかった", it) }
        }
    }

    private fun buildNotification(steps: Long): Notification {
        val goal = prefs.goalSteps
        val tapToOpen = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("今日 %,d 歩".format(steps))
            .setContentText(
                if (steps >= goal) {
                    "目標 %,d 歩を達成".format(goal)
                } else {
                    "目標 %,d 歩まで あと %,d 歩".format(goal, goal - steps)
                },
            )
            .setContentIntent(tapToOpen)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "歩数の記録中",
            // 出続ける通知なので音もバイブも出さない
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "常駐して歩数センサーを読んでいる間、今日の歩数を表示します"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "StepCountingService"
        private const val CHANNEL_ID = "step-counting"
        private const val NOTIFICATION_ID = 201

        /** DB へ書く間隔の下限。歩いている間は1歩ごとにイベントが来るので間引く */
        private const val WRITE_INTERVAL_MS = 60_000L

        /** このプロセスで常駐が動いているか。立て直しを重ねて頼まないために持つ */
        @Volatile
        var running: Boolean = false
            private set

        @Volatile
        private var reception: SensorReception = SensorReception.UNKNOWN

        fun reception(context: Context): SensorReception =
            if (PrefsStore.getInstance(context).liveCounting) reception else SensorReception.DISABLED

        /**
         * 設定がオンなら常駐を始める。動いていれば何もしない。
         *
         * 裏から始めるのは OS に拒まれることがある（バッテリー最適化の対象外なら通る）。
         * 拒まれても、次にアプリを開いたときに始まる。
         */
        fun startIfEnabled(context: Context) {
            val app = context.applicationContext
            if (running || !PrefsStore.getInstance(app).liveCounting) return
            // health 型の常駐には身体活動の権限が要る。無いまま始めると落ちる
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(app, Manifest.permission.ACTIVITY_RECOGNITION) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            runCatching {
                ContextCompat.startForegroundService(app, Intent(app, StepCountingService::class.java))
            }.onFailure {
                reception = SensorReception.FAILED
                Log.w(TAG, "常駐を始められなかった", it)
            }
        }

        fun stop(context: Context) {
            val app = context.applicationContext
            app.stopService(Intent(app, StepCountingService::class.java))
        }
    }
}
