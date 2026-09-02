package app.stepsapp.data.local

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * TYPE_STEP_COUNTER の現在値を1回だけ読む。
 *
 * TYPE_STEP_COUNTER は on-change センサーなので、登録しても
 * 歩数が変化するまでイベントが来ないことがある。そのため必ずタイムアウトを設ける。
 * 値が取れなければ null を返し、呼び出し側は「今回は読めなかった」として扱う
 * (ハードウェアが数え続けているので、次回の読み取りで取りこぼしは回収される)。
 */
class StepCounterReader(private val context: Context) {

    fun isAvailable(): Boolean = sensor() != null

    private fun sensor(): Sensor? {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        return manager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    }

    suspend fun read(timeoutMs: Long = 10_000): Long? {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return null
        val sensor = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return null

        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        val value = event.values.firstOrNull()?.toLong()
                        manager.unregisterListener(this)
                        if (cont.isActive) cont.resume(value)
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }

                manager.registerListener(
                    listener,
                    sensor,
                    SensorManager.SENSOR_DELAY_FASTEST,
                    Handler(Looper.getMainLooper()),
                )
                cont.invokeOnCancellation { manager.unregisterListener(listener) }
            }
        }
    }
}
