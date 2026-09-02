package app.stepsapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 共有画像は一度出すと取り消せない。
 * 「オフにしたものが載らない」ことをここで固定する。
 */
class ShareCardTest {

    private fun card(options: ShareOptions) = buildShareCard(
        steps = 8_432,
        date = "2026-08-29",
        goalSteps = 6_000,
        streakDays = 12,
        distance = 6.3,
        distanceUnit = DistanceUnit.KM,
        trend = listOf(3_000L, 5_000L, 8_432L),
        options = options,
    )

    @Test
    fun `既定では歩数と日付しか載らない`() {
        val c = card(ShareOptions.DEFAULT)

        assertEquals(8_432L, c.steps)
        assertEquals("2026-08-29", c.date)
        assertNull(c.goal)
        assertNull(c.streak)
        assertNull(c.distanceValue)
        assertTrue("グラフは既定で載せない", c.trend.isEmpty())
    }

    @Test
    fun `日付を切ると日付も消える`() {
        val c = card(ShareOptions(includeDate = false))

        assertNull(c.date)
        assertTrue(c.extraLines().isEmpty())
    }

    @Test
    fun `オンにした項目だけが載る`() {
        val c = card(
            ShareOptions(
                includeDate = true,
                includeGoal = true,
                includeStreak = true,
                includeDistance = true,
            ),
        )

        assertEquals("目標 6,000 歩", c.goal)
        assertEquals("12 日連続", c.streak)
        assertEquals("6.3", c.distanceValue)
        assertEquals("km", c.distanceUnit)
        assertEquals(4, c.extraLines().size)
    }

    @Test
    fun `値が無いものはオンでも載せない`() {
        val c = buildShareCard(
            steps = 100,
            date = "2026-08-29",
            goalSteps = 6_000,
            streakDays = 0,
            distance = 0.0,
            distanceUnit = DistanceUnit.KM,
            options = ShareOptions(includeStreak = true, includeDistance = true),
        )

        // 0 日連続 / 0.0 km は誤解を招くので出さない
        assertNull(c.streak)
        assertNull(c.distanceValue)
    }

    @Test
    fun `距離は表示単位の記号で出す`() {
        val c = buildShareCard(
            steps = 100,
            date = "2026-08-29",
            goalSteps = 6_000,
            streakDays = 0,
            distance = 3.9,
            distanceUnit = DistanceUnit.MILE,
            options = ShareOptions(includeDistance = true),
        )

        assertEquals("3.9", c.distanceValue)
        assertEquals("mi", c.distanceUnit)
    }

    @Test
    fun `歩数は常に載る`() {
        val c = card(ShareOptions(includeDate = false))

        assertEquals(8_432L, c.steps)
    }

    @Test
    fun `グラフはオンにしたときだけ載る`() {
        val c = card(ShareOptions(includeTrend = true))

        assertEquals(listOf(3_000L, 5_000L, 8_432L), c.trend)
    }

    @Test
    fun `点が1つでは線にならないので載せない`() {
        val c = buildShareCard(
            steps = 100,
            date = "2026-08-30",
            goalSteps = 6_000,
            streakDays = 0,
            distance = 0.0,
            distanceUnit = DistanceUnit.KM,
            trend = listOf(100L),
            options = ShareOptions(includeTrend = true),
        )

        assertTrue(c.trend.isEmpty())
    }

    // --- 雛形 ---

    @Test
    fun `既定は雛形のシンプルと同じ`() {
        // 何も足していない状態から始める、という決めごとを崩さない
        assertEquals(SharePreset.SIMPLE, SharePreset.of(ShareOptions.DEFAULT))
    }

    @Test
    fun `しっかりは推移のグラフを含まない`() {
        // 数日ぶんの行動が一度に出るグラフは「ぜんぶ」だけにする
        assertFalse(SharePreset.DETAILED.options.includeTrend)
        assertTrue(SharePreset.DETAILED.options.includeDistance)
    }

    @Test
    fun `ぜんぶは全部オン`() {
        val o = SharePreset.ALL.options
        assertTrue(o.includeDate && o.includeGoal && o.includeStreak)
        assertTrue(o.includeDistance && o.includeTrend)
    }

    @Test
    fun `手で足し引きした状態はどの雛形とも一致しない`() {
        val 手動 = ShareOptions(includeDate = false, includeStreak = true)
        assertNull(SharePreset.of(手動))
    }
}
