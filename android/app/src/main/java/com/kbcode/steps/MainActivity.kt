package com.kbcode.steps

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kbcode.steps.billing.LogoBillingViewModel
import com.kbcode.steps.ui.billing.LogoOverlay
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kbcode.steps.data.local.PrefsStore
import com.kbcode.steps.ui.navigation.StepsNavHost
import com.kbcode.steps.ui.navigation.Routes
import com.kbcode.steps.ui.setup.SetupScreen
import com.kbcode.steps.ui.theme.StepsAppTheme
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val billing: LogoBillingViewModel by viewModels()
    private var weeklyReviewRequest by mutableStateOf<String?>(null)

    override fun onResume() {
        super.onResume()
        // 起動時と再開時に購入状態を照会する（復元の導線。KB Code 課金テンプレート）
        billing.manager.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        weeklyReviewRequest = weekFromNotification(intent)
        val prefs = PrefsStore.getInstance(this)
        setContent {
            val appearance by prefs.appearance.collectAsStateWithLifecycle()
            val billingState by billing.manager.state.collectAsStateWithLifecycle()
            StepsAppTheme(accent = appearance.accent, themeMode = appearance.themeMode) {
                // 未購入の間だけ試用版ロゴを左下に出す（KB Code 課金テンプレート）
                LogoOverlay(owned = billingState.owned) {
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
                            billingState = billingState,
                            onBuyLogo = { billing.manager.buy(this@MainActivity) },
                            onRestoreLogo = { billing.manager.refresh(manual = true) },
                        )
                    }
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
