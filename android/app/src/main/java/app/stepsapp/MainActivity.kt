package app.stepsapp

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
import app.stepsapp.ui.setup.SetupScreen
import app.stepsapp.ui.theme.StepsAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = PrefsStore.getInstance(this)
        setContent {
            val appearance by prefs.appearance.collectAsState()
            StepsAppTheme(accent = appearance.accent, themeMode = appearance.themeMode) {
                // 初回だけ案内を出す。終えたら二度と出さない
                var needsSetup by remember { mutableStateOf(!prefs.setupDone) }
                if (needsSetup) {
                    SetupScreen(onDone = { needsSetup = false })
                } else {
                    StepsNavHost()
                }
            }
        }
    }
}
