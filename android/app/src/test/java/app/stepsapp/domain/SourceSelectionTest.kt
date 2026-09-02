package app.stepsapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceSelectionTest {

    @Test
    fun `Health Connect のほうが大きければそちらを採用する`() {
        val chosen = chooseSteps(healthConnect = 8000, sensor = 7500)!!
        assertEquals(StepSource.HEALTH_CONNECT, chosen.source)
        assertEquals(8000L, chosen.stepCount)
    }

    @Test
    fun `センサーのほうが大きければセンサーを採用する`() {
        // 実機で踏んだ例（2026-08-31）。Health Connect にデータを書く側の同期が
        // 遅れ、センサーが 10,250 歩の日に HC が 5,045 しか返さなかった。
        // 以前は HC 固定優先だったので、表示が半分のままになっていた
        val chosen = chooseSteps(healthConnect = 5_045, sensor = 10_250)!!
        assertEquals(StepSource.SENSOR, chosen.source)
        assertEquals(10_250L, chosen.stepCount)
    }

    @Test
    fun `同数なら Health Connect を採用する`() {
        // OS 側の集計なので、ウェアラブルなど端末以外の歩数も含んでいる
        val chosen = chooseSteps(healthConnect = 7_500, sensor = 7_500)!!
        assertEquals(StepSource.HEALTH_CONNECT, chosen.source)
    }

    @Test
    fun `Health Connect が取れなければセンサーにフォールバックする`() {
        val chosen = chooseSteps(healthConnect = null, sensor = 7500)!!
        assertEquals(StepSource.SENSOR, chosen.source)
        assertEquals(7500L, chosen.stepCount)
    }

    @Test
    fun `どちらも取れなければ採用値なし`() {
        assertNull(chooseSteps(healthConnect = null, sensor = null))
    }

    @Test
    fun `2つのソースを決して合算しない`() {
        // これが壊れると歩数が二重計上される。最も重要な性質。
        val chosen = chooseSteps(healthConnect = 8000, sensor = 7500)!!
        assertEquals(8000L, chosen.stepCount)
        assertFalse("合算されている", chosen.stepCount == 15_500L)
    }

    @Test
    fun `Health Connect が0歩ならセンサーの値を採る`() {
        // 歩いていないのにセンサーが 7,500 を数えることはない。
        // HC の 0 は「まだ書き込まれていない」ことのほうが多い
        val chosen = chooseSteps(healthConnect = 0, sensor = 7500)!!
        assertEquals(StepSource.SENSOR, chosen.source)
        assertEquals(7500L, chosen.stepCount)
    }

    @Test
    fun `乖離が大きければ検知する`() {
        assertTrue(isDivergent(healthConnect = 10_000, sensor = 5_000))
        assertFalse(isDivergent(healthConnect = 10_000, sensor = 9_500))
    }

    @Test
    fun `片方しか無いときは乖離とみなさない`() {
        assertFalse(isDivergent(healthConnect = null, sensor = 5_000))
        assertFalse(isDivergent(healthConnect = 5_000, sensor = null))
    }

    @Test
    fun `どちらも0歩なら乖離ではない`() {
        assertFalse(isDivergent(healthConnect = 0, sensor = 0))
    }

    // --- 同じ日の差し替え規則（実機で歩数が消えたため追加） ---

    @Test
    fun `記録が無ければ書き込む`() {
        val incoming = ChosenSteps(StepSource.SENSOR, 100)
        assertTrue(shouldReplaceDay(null, null, incoming))
    }

    @Test
    fun `増えていれば書き換える`() {
        val incoming = ChosenSteps(StepSource.SENSOR, 5000)
        assertTrue(shouldReplaceDay(3000, StepSource.SENSOR, incoming))
    }

    @Test
    fun `減る方向の上書きは拒否する`() {
        // 実機で踏んだケース: バックグラウンドで HC が読めず SENSOR に落ちて
        // 保存済みの HC 3480 を小さい値で消していた
        val incoming = ChosenSteps(StepSource.SENSOR, 50)
        assertFalse(shouldReplaceDay(3480, StepSource.HEALTH_CONNECT, incoming))
    }

    @Test
    fun `センサーが保存済みを追い越したら主導権が移る`() {
        // HC の許可が恒久的に外れても、いずれセンサーが引き継げること
        val incoming = ChosenSteps(StepSource.SENSOR, 3481)
        assertTrue(shouldReplaceDay(3480, StepSource.HEALTH_CONNECT, incoming))
    }

    @Test
    fun `同数ならセンサーの記録を Health Connect に貼り替える`() {
        val incoming = ChosenSteps(StepSource.HEALTH_CONNECT, 3480)
        assertTrue(shouldReplaceDay(3480, StepSource.SENSOR, incoming))
    }

    @Test
    fun `同数で同じソースなら書き換えない`() {
        // 無駄な書き込みで syncedAt をリセットしないため
        val incoming = ChosenSteps(StepSource.HEALTH_CONNECT, 3480)
        assertFalse(shouldReplaceDay(3480, StepSource.HEALTH_CONNECT, incoming))
    }

    @Test
    fun `0歩のまま動いていない日は書き換えない`() {
        val incoming = ChosenSteps(StepSource.SENSOR, 0)
        assertFalse(shouldReplaceDay(0, StepSource.SENSOR, incoming))
    }
}
