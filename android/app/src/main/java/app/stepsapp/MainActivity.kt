package app.stepsapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.stepsapp.data.local.PrefsStore
import app.stepsapp.ui.navigation.StepsNavHost
import app.stepsapp.ui.navigation.Routes
import app.stepsapp.ui.setup.SetupScreen
import app.stepsapp.ui.theme.StepsAppTheme
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private var weeklyReviewRequest by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        weeklyReviewRequest = weekFromNotification(intent)
        val prefs = PrefsStore.getInstance(this)
        setContent {
            val appearance by prefs.appearance.collectAsState()
            StepsAppTheme(accent = appearance.accent, themeMode = appearance.themeMode) {
                // 初回だけ案内を出す。終えたら二度と出さない
                var needsSetup by remember { mutableStateOf(!prefs.setupDone) }
                if (needsSetup) {
                    SetupScreen(onDone = { needsSetup = false })
                } else {
                    StepsNavHost(
                        weeklyReviewWeek = weeklyReviewRequest,
                        onWeeklyReviewHandled = {
                            weeklyReviewRequest = null
                            intent?.removeExtra(Routes.EXTRA_WEEK_START)
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        weeklyReviewRequest = weekFromNotification(intent)
    }

    private fun weekFromNotification(intent: Intent?): String? {
        if (intent?.action != Routes.WEEKLY_REVIEW_ACTION) return null
        val week = intent.getStringExtra(Routes.EXTRA_WEEK_START) ?: return null
        return week.takeIf { runCatching { LocalDate.parse(it) }.isSuccess }
    }
}
