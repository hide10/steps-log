package app.stepsapp.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ウィジェットの描画そのもの（Bitmap）は Android の Canvas が要るので
 * JVM テストでは動かせない。ここでは**寸法の決め方**だけを固定する。
 * 描画の見た目はエミュレータで確認する。
 */
class WidgetRenderTest {

    @Test
    fun `棒グラフの本数ぶん幅が分配される`() {
        // 7本なら隙間込みで幅を割り切れること（1本が潰れない）
        val width = 900
        val n = 7
        val gap = maxOf(2f, width / (n * 8f))
        val barWidth = (width - gap * (n - 1)) / n
        assertTrue("棒が細すぎる", barWidth > gap * 2)
        assertEquals(width.toFloat(), barWidth * n + gap * (n - 1), 0.5f)
    }

    @Test
    fun `1か月ぶんでも棒が潰れない`() {
        val width = 1200
        val n = 31
        val gap = maxOf(2f, width / (n * 8f))
        val barWidth = (width - gap * (n - 1)) / n
        assertTrue("31本で棒が消える (幅=$barWidth)", barWidth >= 4f)
    }

    @Test
    fun `全部未達でも最大値が目標になる`() {
        // これが無いと、少ししか歩いていない日が満杯の棒に見えてしまう
        val values = listOf(100L, 200L, 300L)
        val goal = 6000L
        assertEquals(goal, maxOf(values.max(), goal))
    }

    @Test
    fun `目標を超えた日があればそれが最大値`() {
        val values = listOf(100L, 12000L)
        val goal = 6000L
        assertEquals(12000L, maxOf(values.max(), goal))
    }

    @Test
    fun `達成率は1で頭打ち`() {
        fun ratio(steps: Long, goal: Long) =
            (steps.toFloat() / goal).coerceIn(0f, 1f)
        assertEquals(0.5f, ratio(3000, 6000), 0.001f)
        assertEquals(1f, ratio(99999, 6000), 0.001f)
        assertEquals(0f, ratio(0, 6000), 0.001f)
    }
}
