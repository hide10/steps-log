package app.stepsapp.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.stepsapp.MainActivity
import app.stepsapp.domain.formatDuration

private fun sp(v: Int) = TextUnit(v.toFloat(), TextUnitType.Sp)

private const val ACCENT = 0xFF4C8BF5.toInt()
private const val ACHIEVED = 0xFF2E9E6B.toInt()

/** ウィジェット共通の枠。タップでアプリを開く。 */
@Composable
private fun Frame(content: @Composable () -> Unit) {
    val context = LocalContext.current
    GlanceTheme {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(12.dp)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
        ) { content() }
    }
}

@Composable
private fun Label(text: String, size: Int = 12) =
    Text(text, style = TextStyle(fontSize = sp(size), color = GlanceTheme.colors.onSurfaceVariant))

@Composable
private fun Big(text: String, size: Int = 30) =
    Text(
        text,
        style = TextStyle(
            fontSize = sp(size),
            fontWeight = FontWeight.Bold,
            color = GlanceTheme.colors.onSurface,
        ),
    )

// --- 小: リング -------------------------------------------------------------

class RingWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = WidgetData.load(context)
        provideContent {
            Frame {
                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            provider = ImageProvider(
                                WidgetRender.ring(300, d.ratio, ACCENT, ACHIEVED),
                            ),
                            contentDescription = null,
                            modifier = GlanceModifier.size(104.dp),
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Big("%,d".format(d.today), 22)
                            Label("/ %,d".format(d.goal.dailySteps), 11)
                        }
                    }
                    Label(if (d.streak > 0) "連続 ${d.streak}日" else "今日はこれから", 12)
                }
            }
        }
    }
}

class RingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RingWidget()
}

// --- 中: 今日 + 週グラフ ----------------------------------------------------

class WeekWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = WidgetData.load(context)
        provideContent {
            Frame {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    Label("今日")
                    Big("%,d".format(d.today), 32)
                    Label(
                        "/ %,d 歩".format(d.goal.dailySteps) +
                            if (d.streak > 0) " ・ 連続 ${d.streak}日" else "",
                    )
                    Image(
                        provider = ImageProvider(
                            WidgetRender.bars(900, 200, d.week, d.goal.dailySteps, ACCENT, ACHIEVED),
                        ),
                        contentDescription = null,
                        modifier = GlanceModifier.fillMaxWidth().height(52.dp).padding(top = 8.dp),
                    )
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        d.weekLabels.forEach { day ->
                            Box(
                                modifier = GlanceModifier.defaultWeight(),
                                contentAlignment = Alignment.Center,
                            ) { Label(day, 10) }
                        }
                    }
                }
            }
        }
    }
}

class WeekWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekWidget()
}

// --- 大: まとめ -------------------------------------------------------------

class SummaryWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = WidgetData.load(context)
        provideContent {
            Frame {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(contentAlignment = Alignment.Center) {
                            Image(
                                provider = ImageProvider(
                                    WidgetRender.ring(280, d.ratio, ACCENT, ACHIEVED),
                                ),
                                contentDescription = null,
                                modifier = GlanceModifier.size(88.dp),
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Big("%,d".format(d.today), 18)
                                Label("/ %,d".format(d.goal.dailySteps), 10)
                            }
                        }
                        Column(modifier = GlanceModifier.padding(start = 14.dp)) {
                            Stat("連続", if (d.streak > 0) "${d.streak}日" else "—")
                            d.weightKg?.let { Stat("体重", "%.1f kg".format(it)) }
                            d.sleepMinutes?.let { Stat("睡眠", formatDuration(it)) }
                        }
                    }
                    Image(
                        provider = ImageProvider(
                            WidgetRender.bars(900, 200, d.week, d.goal.dailySteps, ACCENT, ACHIEVED),
                        ),
                        contentDescription = null,
                        modifier = GlanceModifier.fillMaxWidth().height(54.dp).padding(top = 10.dp),
                    )
                }
            }
        }
    }

    @Composable
    private fun Stat(label: String, value: String) {
        Row(
            modifier = GlanceModifier.padding(bottom = 4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Label(label, 11)
            Box(modifier = GlanceModifier.width(8.dp)) {}
            Big(value, 16)
        }
    }
}

class SummaryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SummaryWidget()
}

// --- 大: 月グラフ -----------------------------------------------------------

class MonthWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = WidgetData.load(context)
        provideContent {
            Frame {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    Label("今月の平均")
                    Row(verticalAlignment = Alignment.Bottom) {
                        Big("%,d".format(d.monthAverage), 32)
                        Box(modifier = GlanceModifier.width(8.dp)) {}
                        Label("歩/日")
                    }
                    Image(
                        provider = ImageProvider(
                            WidgetRender.bars(
                                1200, 400, d.month, d.goal.dailySteps, ACCENT, ACHIEVED,
                            ),
                        ),
                        contentDescription = null,
                        modifier = GlanceModifier.fillMaxWidth().defaultWeight()
                            .padding(top = 10.dp),
                    )
                }
            }
        }
    }
}

class MonthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthWidget()
}
