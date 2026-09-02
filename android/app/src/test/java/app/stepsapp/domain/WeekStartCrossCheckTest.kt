package app.stepsapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate

/**
 * Kotlin の weekStart() と、サーバ側 SQL の
 * date(d, '-' || ((strftime('%w', d) + 6) % 7) || ' days')
 * が完全に一致することを確かめる。
 *
 * 週の集計規則がアプリと PC ダッシュボードでズレると、同じ週なのに違う数字が出る。
 * 期待値 sql-week-starts.csv は bun:sqlite で 2024-01-01 から 1200 日ぶん生成したもの。
 * 再生成するときは server 側で次を実行する:
 *
 *   select date(?1, '-' || ((strftime('%w', ?1) + 6) % 7) || ' days')
 */
class WeekStartCrossCheckTest {

    @Test
    fun `SQL の週開始日と完全に一致する`() {
        val stream = javaClass.classLoader?.getResourceAsStream("sql-week-starts.csv")
        assertNotNull("期待値ファイルが見つからない", stream)

        val lines = stream!!.bufferedReader().readLines().filter { it.isNotBlank() }
        assertEquals("期待値の行数が想定と違う", 1200, lines.size)

        for (line in lines) {
            val (dateText, expected) = line.split(",")
            val actual = weekStart(LocalDate.parse(dateText)).toString()
            assertEquals("日付 " + dateText + " で不一致", expected, actual)
        }
    }
}
