package app.stepsapp.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.stepsapp.ui.theme.LocalAccentColors

/**
 * 今日の歩数と目標達成率を示すリング。
 *
 * 達成率は 1.0 で頭打ちにして、リングが一周を超えて描かれないようにする。
 * 目標を超えた場合はリングを一周させたうえで、中央のテキストで超過を伝える。
 */
@Composable
fun GoalRing(
    steps: Long,
    goalSteps: Long,
    ratio: Float,
    achieved: Boolean,
    modifier: Modifier = Modifier,
    diameter: Int = 220,
) {
    val animated by animateFloatAsState(
        targetValue = ratio,
        animationSpec = tween(durationMillis = 700),
        label = "goal-ring",
    )

    val accent = LocalAccentColors.current
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = if (achieved) accent.achieved else accent.primary

    Box(modifier = modifier.size(diameter.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(diameter.dp)) {
            val stroke = size.minDimension * 0.09f
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)

            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke),
            )
            if (animated > 0f) {
                drawArc(
                    color = progressColor,
                    // 12時の位置から時計回り
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text("今日", style = MaterialTheme.typography.labelLarge, color = progressColor)
            Text(
                text = "%,d".format(steps),
                style = MaterialTheme.typography.displayMedium,
            )
            Text(
                text = if (achieved) "目標達成" else "%,d 歩".format(goalSteps),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = if (achieved) progressColor else Color.Unspecified,
            )
        }
    }
}
