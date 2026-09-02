package app.stepsapp.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitsTest {

    @Test
    fun `保存名から復元でき、壊れていれば既定に落ちる`() {
        assertEquals(DistanceUnit.MILE, DistanceUnit.from("MILE"))
        assertEquals(DistanceUnit.KM, DistanceUnit.from(null))
        assertEquals(DistanceUnit.KM, DistanceUnit.from("PARSEC"))
        assertEquals(WeightUnit.KG, WeightUnit.from(null))
        assertEquals(HeightUnit.CM, HeightUnit.from("なにか"))
    }

    @Test
    fun `距離を換算する`() {
        assertEquals(10.0, displayDistance(10.0, DistanceUnit.KM), 0.001)
        assertEquals(6.2137, displayDistance(10.0, DistanceUnit.MILE), 0.001)
    }

    @Test
    fun `体重を換算する`() {
        assertEquals(62.1, displayWeight(62.1, WeightUnit.KG), 0.001)
        assertEquals(136.9, displayWeight(62.1, WeightUnit.POUND), 0.1)
    }

    @Test
    fun `換算しても往復で値が戻る`() {
        // 単位を切り替えても体重がずれないことを保証する
        val kg = 62.137
        val lb = displayWeight(kg, WeightUnit.POUND)
        assertEquals(kg, storeWeight(lb, WeightUnit.POUND), 0.0001)
    }

    @Test
    fun `フィートインチの表記`() {
        assertEquals("5' 9\"", formatFeetInch(175))
        assertEquals("5' 3\"", formatFeetInch(160))
        assertEquals("", formatFeetInch(0))
    }

    @Test
    fun `歩幅は手入力があればそれを使う`() {
        assertEquals(0.78, strideMetersOf(heightCm = 170, manualStrideCm = 78), 0.001)
    }

    @Test
    fun `歩幅の手入力が無ければ身長から推定する`() {
        assertEquals(0.765, strideMetersOf(heightCm = 170, manualStrideCm = 0), 0.001)
    }

    @Test
    fun `どちらも無ければ平均的な歩幅`() {
        assertEquals(0.70, strideMetersOf(heightCm = 0, manualStrideCm = 0), 0.001)
    }
}
