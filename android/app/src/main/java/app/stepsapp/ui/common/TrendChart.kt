package app.stepsapp.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.stepsapp.domain.Trend
import app.stepsapp.ui.theme.LocalAccentColors

/**
 * 推移の折れ線。
 *
 * 体重や睡眠のように連続して変化する量は、今日の1点だけ見ても意味が薄い。
 * 増えているのか減っているのかが分かる形で見せる。
 *
 * **縦軸はデータの実際の範囲に合わせる**（0 起点にしない）。
 * 体重 62〜64kg を 0 から描くと変化がまったく見えなくなるため。
 * ただしそれだと小さな揺れが山脈のように誇張されるので、
 * 平均線を重ねて「どのくらいの幅で動いているか」を掴めるようにする。
 */
@Composable
fun TrendChart(
    trend: Trend,
    modifier: Modifier = Modifier,
    height: Int = 96,
    format: (Double) -> String = { "%.1f".format(it) },
) {
    val points = trend.points
    if (points.size < 2) {
        Text(
            "推移を出すにはもう少し記録が要ります",
            style = MaterialTheme.typography.bodySmall,
            modifier = modifier.padding(vertical = 8.dp),
        )
        return
    }

    val accent = LocalAccentColors.current
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant
    val min = trend.min ?: return
    val max = trend.max ?: return
    val avg = trend.average ?: return
    // 全部同じ値だと高さ0で割ることになる
    val span = (max - min).takeIf { it > 0.0001 } ?: 1.0

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height.dp),
        ) {
            fun yOf(v: Double) = (size.height * (1 - (v - min) / span)).toFloat()
            val stepX = size.width / (points.size - 1)

            // 平均線。どのあたりを中心に揺れているかの目安
            drawLine(
                color = gridColor.copy(alpha = 0.35f),
                start = Offset(0f, yOf(avg)),
                end = Offset(size.width, yOf(avg)),
                strokeWidth = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
            )

            val path = Path()
            points.forEachIndexed { i, p ->
                val x = stepX * i
                val y = yOf(p.value)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = accent.primary, style = Stroke(width = 4f))

            // 直近の点だけ強調する。いまどこにいるかが一番知りたい情報
            drawCircle(
                color = accent.primary,
                radius = 6f,
                center = Offset(size.width, yOf(points.last().value)),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        ) {
            Text(
                "${points.first().localDate.takeLast(5)}  最小 ${format(min)}",
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                "平均 ${format(avg)}  最大 ${format(max)}",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
