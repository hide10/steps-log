package app.stepsapp.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

/**
 * ウィジェット用の図を Bitmap に描く。
 *
 * Glance には Canvas が無く、リングや棒グラフを直接描けない。
 * そこで Android の Canvas で描いた Bitmap を ImageProvider 経由で渡す。
 *
 * **描画は密度に依存しないピクセル数で行う。** ウィジェットのサイズは
 * ホーム画面のグリッドで決まり事前に分からないので、十分な解像度で描いて
 * 表示側で縮めさせる。
 */
object WidgetRender {

    private const val TRACK = 0x33FFFFFF.toInt()

    /** 達成リング。中央に歩数を出す余白を残すため、文字は Glance 側で重ねる。 */
    fun ring(sizePx: Int, ratio: Float, color: Int, achievedColor: Int): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val stroke = sizePx * 0.12f
        val r = (sizePx - stroke) / 2f
        val box = RectF(stroke / 2, stroke / 2, sizePx - stroke / 2, sizePx - stroke / 2)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
        }
        paint.color = TRACK
        c.drawArc(box, 0f, 360f, false, paint)

        if (ratio > 0f) {
            paint.color = if (ratio >= 1f) achievedColor else color
            c.drawArc(box, -90f, 360f * min(ratio, 1f), false, paint)
        }
        return bmp
    }

    /**
     * 棒グラフ。
     *
     * **目標を達成した日は色を変える。** 数字を読まなくても
     * 「達成できた日がどれだけあるか」が一目で分かるようにするため。
     */
    fun bars(
        widthPx: Int,
        heightPx: Int,
        values: List<Long>,
        goal: Long,
        color: Int,
        achievedColor: Int,
    ): Bitmap {
        val bmp = Bitmap.createBitmap(max(widthPx, 1), max(heightPx, 1), Bitmap.Config.ARGB_8888)
        if (values.isEmpty()) return bmp
        val c = Canvas(bmp)

        val gap = max(2f, widthPx / (values.size * 8f))
        val barWidth = (widthPx - gap * (values.size - 1)) / values.size
        // 目標も基準に入れる。全部未達のときに小さな値が満杯に見えるのを防ぐ
        val maxValue = max(values.max(), goal).coerceAtLeast(1)
        val radius = min(barWidth / 2f, 8f)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        values.forEachIndexed { i, v ->
            val h = heightPx * (v.toFloat() / maxValue)
            val left = i * (barWidth + gap)
            paint.color = if (v >= goal) achievedColor else color
            c.drawRoundRect(
                RectF(left, heightPx - h, left + barWidth, heightPx.toFloat()),
                radius, radius, paint,
            )
        }
        return bmp
    }

    /** 進捗バー（横）。小さいウィジェット向け。 */
    fun progressBar(widthPx: Int, heightPx: Int, ratio: Float, color: Int): Bitmap {
        val bmp = Bitmap.createBitmap(max(widthPx, 1), max(heightPx, 1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = heightPx / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = TRACK
        c.drawRoundRect(RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat()), r, r, paint)
        if (ratio > 0f) {
            paint.color = color
            val w = widthPx * min(ratio, 1f)
            c.drawRoundRect(RectF(0f, 0f, max(w, heightPx.toFloat()), heightPx.toFloat()), r, r, paint)
        }
        return bmp
    }

    fun colorOf(argb: Long): Int = argb.toInt()

    val WHITE: Int = Color.WHITE
}
