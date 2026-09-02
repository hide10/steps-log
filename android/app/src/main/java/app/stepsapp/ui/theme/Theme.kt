package app.stepsapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import app.stepsapp.domain.Accent
import app.stepsapp.domain.ThemeMode

/**
 * 選ばれているアクセントカラー。
 *
 * Material3 の colorScheme に混ぜず独立して持つ。リングとグラフだけに使い、
 * ボタンや文字などの標準部品は Material3 の配色のままにしたいため。
 */
val LocalAccentColors = compositionLocalOf { AccentColors(Accent.DEFAULT) }

data class AccentColors(val accent: Accent) {
    val primary: Color get() = Color(accent.rgb)
    val achieved: Color get() = Color(accent.achievedRgb)
}

@Composable
fun StepsAppTheme(
    accent: Accent = Accent.DEFAULT,
    themeMode: ThemeMode = ThemeMode.DEFAULT,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    CompositionLocalProvider(LocalAccentColors provides AccentColors(accent)) {
        MaterialTheme(
            colorScheme = if (dark) darkColorScheme() else lightColorScheme(),
            content = content,
        )
    }
}
