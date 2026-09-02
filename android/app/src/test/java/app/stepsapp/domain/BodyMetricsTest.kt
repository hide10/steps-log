package app.stepsapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyMetricsTest {

    @Test
    fun `身長から歩幅を推定する`() {
        assertEquals(0.765, strideMeters(170), 0.0001)
        assertEquals(0.72, strideMeters(160), 0.0001)
    }

    @Test
    fun `身長が未設定なら既定の歩幅を使う`() {
        assertEquals(0.70, strideMeters(0), 0.0001)
        assertEquals(0.70, strideMeters(-5), 0.0001)
    }

    @Test
    fun `距離を計算する`() {
        // 10000歩 × 0.765m = 7.65km
        assertEquals(7.65, distanceKm(10_000, 170), 0.01)
    }

    @Test
    fun `0歩なら距離も0`() {
        assertEquals(0.0, distanceKm(0, 170), 0.0001)
    }

    @Test
    fun `体重が未設定ならカロリーは出さない`() {
        // 当てずっぽうの数字を出さない
        assertNull(caloriesKcal(10_000, 170, 0))
    }

    @Test
    fun `カロリーを推定する`() {
        // 60kg × 7.65km × 0.5 = 229.5kcal
        assertEquals(229.5, caloriesKcal(10_000, 170, 60)!!, 0.1)
    }

    @Test
    fun `歩数が増えればカロリーも増える`() {
        val a = caloriesKcal(5_000, 170, 60)!!
        val b = caloriesKcal(10_000, 170, 60)!!
        assertTrue(b > a)
    }
}
