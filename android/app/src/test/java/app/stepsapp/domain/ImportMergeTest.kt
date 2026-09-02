package app.stepsapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImportMergeTest {

    private fun day(date: String, steps: Long, updatedAt: Long = 100) =
        ExportDay(date, steps, StepSource.SENSOR.name, updatedAt)

    @Test
    fun `既存が無ければそのまま取り込む`() {
        val incoming = day("2026-08-25", 5000)
        for (mode in ImportMode.entries) {
            assertEquals("mode=$mode", incoming, resolveConflict(null, incoming, mode))
        }
    }

    @Test
    fun `SKIP は既存を守る`() {
        val existing = day("2026-08-25", 3000)
        val incoming = day("2026-08-25", 9000)
        assertNull(resolveConflict(existing, incoming, ImportMode.SKIP))
    }

    @Test
    fun `OVERWRITE は歩数が少なくてもインポート側で上書きする`() {
        val existing = day("2026-08-25", 9000)
        val incoming = day("2026-08-25", 100)
        assertEquals(incoming, resolveConflict(existing, incoming, ImportMode.OVERWRITE))
    }

    @Test
    fun `MERGE は大きいほうを採用する`() {
        val existing = day("2026-08-25", 3000)
        val incoming = day("2026-08-25", 9000)
        assertEquals(incoming, resolveConflict(existing, incoming, ImportMode.MERGE))
    }

    @Test
    fun `MERGE はインポート側が小さければ既存を守る`() {
        val existing = day("2026-08-25", 9000)
        val incoming = day("2026-08-25", 3000)
        assertNull(resolveConflict(existing, incoming, ImportMode.MERGE))
    }

    @Test
    fun `MERGE は同数なら書き換えない`() {
        val existing = day("2026-08-25", 5000)
        val incoming = day("2026-08-25", 5000, updatedAt = 999)
        assertNull(resolveConflict(existing, incoming, ImportMode.MERGE))
    }

    @Test
    fun `MERGE は0歩の記録を大きい値で上書きできる`() {
        // 「実際に0歩」の日でも、後から取りこぼしが判明したら大きいほうを採る
        val existing = day("2026-08-25", 0)
        val incoming = day("2026-08-25", 4200)
        assertEquals(incoming, resolveConflict(existing, incoming, ImportMode.MERGE))
    }

    @Test
    fun `書き換わる件数を事前に数えられる`() {
        val existing = mapOf(
            "2026-08-24" to day("2026-08-24", 9000),
            "2026-08-25" to day("2026-08-25", 1000),
        )
        val incoming = listOf(
            day("2026-08-24", 3000),   // MERGE では書き換わらない
            day("2026-08-25", 8000),   // 書き換わる
            day("2026-08-26", 4000),   // 新規なので書き込む
        )
        assertEquals(2, countChanges(existing, incoming, ImportMode.MERGE))
        assertEquals(3, countChanges(existing, incoming, ImportMode.OVERWRITE))
        assertEquals(1, countChanges(existing, incoming, ImportMode.SKIP))
    }
}
