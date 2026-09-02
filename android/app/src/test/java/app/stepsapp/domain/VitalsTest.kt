package app.stepsapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VitalsTest {

    @Test
    fun `保存名から復元できる`() {
        for (k in VitalKind.entries) assertEquals(k, VitalKind.from(k.name))
        assertNull(VitalKind.from("UNKNOWN"))
    }

    @Test
    fun `積み上がる量は合計、そうでないものは平均`() {
        // 距離やカロリーは足す。心拍や血圧を足しても意味がない
        assertTrue(isCumulative(VitalKind.DISTANCE))
        assertTrue(isCumulative(VitalKind.CALORIES_TOTAL))
        assertTrue(isCumulative(VitalKind.FLOORS_CLIMBED))
        assertFalse(isCumulative(VitalKind.RESTING_HEART_RATE))
        assertFalse(isCumulative(VitalKind.BLOOD_PRESSURE_SYS))
        assertFalse(isCumulative(VitalKind.BODY_FAT))
    }

    @Test
    fun `平均と合計`() {
        assertEquals(60.0, averageOf(listOf(50.0, 70.0))!!, 0.001)
        assertEquals(120.0, sumOf(listOf(50.0, 70.0))!!, 0.001)
    }

    @Test
    fun `記録が無ければ値を作らない`() {
        assertNull(averageOf(emptyList()))
        assertNull(sumOf(emptyList()))
    }

    @Test
    fun `単位ごとに意味のある桁で丸める`() {
        // 心拍を小数まで出しても意味がない
        assertEquals("62", formatVital(VitalKind.RESTING_HEART_RATE, 62.4))
        // 体脂肪率は整数だと粗い
        assertEquals("18.3", formatVital(VitalKind.BODY_FAT, 18.34))
        // 距離は2桁ほしい
        assertEquals("4.27", formatVital(VitalKind.DISTANCE, 4.2718))
    }

    @Test
    fun `すべての種類にラベルと単位がある`() {
        for (k in VitalKind.entries) {
            assertTrue(k.name, k.label.isNotBlank())
            assertTrue(k.name, k.unit.isNotBlank())
        }
        // ラベルが重複すると画面で見分けがつかない
        val labels = VitalKind.entries.map { it.label }
        assertEquals(labels.size, labels.toSet().size)
    }
}
