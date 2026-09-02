package app.stepsapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentTest {

    @Test
    fun `保存名から復元できる`() {
        for (accent in Accent.entries) {
            assertEquals(accent, Accent.from(accent.name))
        }
        for (mode in ThemeMode.entries) {
            assertEquals(mode, ThemeMode.from(mode.name))
        }
    }

    @Test
    fun `未設定や壊れた値なら既定に落ちる`() {
        // 設定が壊れていてもアプリが起動しなくなってはいけない
        assertEquals(Accent.DEFAULT, Accent.from(null))
        assertEquals(Accent.DEFAULT, Accent.from(""))
        assertEquals(Accent.DEFAULT, Accent.from("MAGENTA"))
        assertEquals(ThemeMode.DEFAULT, ThemeMode.from(null))
        assertEquals(ThemeMode.DEFAULT, ThemeMode.from("SEPIA"))
    }

    @Test
    fun `既定は端末のテーマに合わせる`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.DEFAULT)
    }

    @Test
    fun `達成色は通常色と区別がつく`() {
        // 達成した瞬間に色が変わることが分かる必要がある
        for (accent in Accent.entries) {
            assertNotEquals("${accent.name} の達成色が同じ", accent.rgb, accent.achievedRgb)
        }
    }

    @Test
    fun `達成色は選んだ色と同じ色相にする`() {
        // 当初は達成時を一律で緑にしていたため、目標を達成している間じゅう
        // 選んだ色が見えなくなっていた。色相が保たれることを固定する。
        for (accent in Accent.entries) {
            val hue = hueOf(accent.rgb)
            val achievedHue = hueOf(accent.achievedRgb)
            val diff = minOf(
                kotlin.math.abs(hue - achievedHue),
                360f - kotlin.math.abs(hue - achievedHue),
            )
            assertTrue(
                "${accent.label} の達成色が別の色相になっている ($hue -> $achievedHue)",
                diff < 30f,
            )
        }
    }

    @Test
    fun `達成色のほうが濃い`() {
        for (accent in Accent.entries) {
            assertTrue(
                "${accent.label} の達成色が濃くない",
                luminance(accent.achievedRgb) < luminance(accent.rgb),
            )
        }
    }

    private fun luminance(rgb: Long): Float {
        val r = ((rgb shr 16) and 0xFF) / 255f
        val g = ((rgb shr 8) and 0xFF) / 255f
        val b = (rgb and 0xFF) / 255f
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    private fun hueOf(rgb: Long): Float {
        val r = ((rgb shr 16) and 0xFF) / 255f
        val g = ((rgb shr 8) and 0xFF) / 255f
        val b = (rgb and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        if (d == 0f) return 0f
        val h = when (max) {
            r -> 60f * (((g - b) / d) % 6f)
            g -> 60f * (((b - r) / d) + 2f)
            else -> 60f * (((r - g) / d) + 4f)
        }
        return if (h < 0) h + 360f else h
    }

    @Test
    fun `色はすべて不透明`() {
        // 半透明だと背景に負けて見えなくなる
        for (accent in Accent.entries) {
            assertEquals(0xFF, (accent.rgb ushr 24).toInt())
            assertEquals(0xFF, (accent.achievedRgb ushr 24).toInt())
        }
    }

    @Test
    fun `選べる色が複数ある`() {
        assertTrue(Accent.entries.size >= 4)
        // ラベルが重複していると選べない
        assertEquals(Accent.entries.size, Accent.entries.map { it.label }.toSet().size)
    }
}
