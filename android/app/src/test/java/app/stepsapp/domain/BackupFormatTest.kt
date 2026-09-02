package app.stepsapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFormatTest {

    private val sample = Backup(
        appVersion = "0.1.0",
        timeZone = "Asia/Tokyo",
        exportedAt = 1_700_000_000_000,
        days = listOf(
            ExportDay("2026-08-24", 8123, StepSource.HEALTH_CONNECT.name, 1),
            ExportDay("2026-08-25", 0, StepSource.SENSOR.name, 2),
        ),
        raw = listOf(
            ExportRaw("2026-08-24", StepSource.SENSOR.name, 40_000, 10),
        ),
        weights = listOf(ExportWeight("2026-08-24", 70.4, 111)),
        sleep = listOf(ExportSleep("2026-08-24", 431, 100, 200)),
    )

    @Test
    fun `JSON は往復しても内容が変わらない`() {
        val restored = Backup.decode(Backup.encode(sample))
        assertEquals(sample, restored)
    }

    @Test
    fun `JSON は生ログも保持する`() {
        val restored = Backup.decode(Backup.encode(sample))
        assertEquals(1, restored.raw.size)
        assertEquals(40_000L, restored.raw.first().stepCount)
    }

    @Test
    fun `JSON には必ず schemaVersion が入る`() {
        // 既定値だからと省略されると、将来の読み手が版を判別できなくなる
        val text = Backup.encode(Backup())
        assertTrue("schemaVersion が省略されている", text.contains("\"schemaVersion\""))
        assertTrue(text.contains("\"days\""))
        assertTrue(text.contains("\"weights\""))
        assertTrue(text.contains("\"sleep\""))
    }

    @Test
    fun `JSON は体重と睡眠も保持する`() {
        // バックアップの目的は完全復元なので、歩数以外も落としてはいけない
        val restored = Backup.decode(Backup.encode(sample))
        assertEquals(70.4, restored.weights.single().kg, 0.001)
        assertEquals(431L, restored.sleep.single().minutes)
    }

    @Test
    fun `体重や睡眠が無い古いバックアップも読める`() {
        // schemaVersion 1 の頃に書き出したファイルが読めなくなってはいけない
        val old = """
            {"schemaVersion":1,"appVersion":"0.1.0","timeZone":"Asia/Tokyo",
             "exportedAt":1,"days":[],"raw":[]}
        """.trimIndent()
        val restored = Backup.decode(old)
        assertTrue(restored.weights.isEmpty())
        assertTrue(restored.sleep.isEmpty())
    }

    @Test
    fun `知らないフィールドがあっても読める`() {
        // 将来のアプリが増やしたフィールドで壊れないこと
        val text = """
            {"schemaVersion":1,"appVersion":"9.9.9","timeZone":"Asia/Tokyo",
             "exportedAt":1,"days":[],"raw":[],"futureField":"なにか"}
        """.trimIndent()
        val restored = Backup.decode(text)
        assertEquals("9.9.9", restored.appVersion)
    }

    @Test
    fun `CSV は往復しても歩数と日付が変わらない`() {
        val text = Csv.encode(sample.days)
        val restored = Csv.decode(text, updatedAt = 0)
        assertEquals(sample.days.map { it.localDate }, restored.map { it.localDate })
        assertEquals(sample.days.map { it.stepCount }, restored.map { it.stepCount })
        assertEquals(sample.days.map { it.source }, restored.map { it.source })
    }

    @Test
    fun `CSV はヘッダー行を出力する`() {
        assertTrue(Csv.encode(sample.days).startsWith(Csv.HEADER))
    }

    @Test
    fun `CSV は日付順に並べて出力する`() {
        val shuffled = listOf(
            ExportDay("2026-08-26", 1, "SENSOR", 0),
            ExportDay("2026-08-24", 2, "SENSOR", 0),
        )
        val lines = Csv.encode(shuffled).lines()
        assertTrue(lines[1].startsWith("2026-08-24"))
        assertTrue(lines[2].startsWith("2026-08-26"))
    }

    @Test
    fun `CSV はヘッダーが無くても読める`() {
        val restored = Csv.decode("2026-08-25,1234,SENSOR", updatedAt = 0)
        assertEquals(1, restored.size)
        assertEquals(1234L, restored.first().stepCount)
    }

    @Test
    fun `CSV はソース列が無ければ IMPORTED 扱いにする`() {
        val restored = Csv.decode("2026-08-25,1234", updatedAt = 0)
        assertEquals(StepSource.IMPORTED.name, restored.first().source)
    }

    @Test
    fun `CSV の0歩は取りこぼしではなく0歩として読む`() {
        val restored = Csv.decode("2026-08-25,0,SENSOR", updatedAt = 0)
        assertEquals(0L, restored.first().stepCount)
    }

    @Test
    fun `CSV の壊れた行は例外にする`() {
        try {
            Csv.decode("2026-08-25,あ,SENSOR", updatedAt = 0)
            throw AssertionError("例外が投げられなかった")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("数値でない"))
        }
    }

    @Test
    fun `CSV の負の歩数は例外にする`() {
        try {
            Csv.decode("2026-08-25,-5,SENSOR", updatedAt = 0)
            throw AssertionError("例外が投げられなかった")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("負"))
        }
    }

    @Test
    fun `JSON は心拍などの健康データも保持する`() {
        val backup = Backup(
            days = listOf(ExportDay("2026-08-29", 5_830, "HEALTH_CONNECT", 1L)),
            vitals = listOf(
                ExportVital("2026-08-29", VitalKind.RESTING_HEART_RATE.name, 58.0, 2L),
                ExportVital("2026-08-29", VitalKind.BLOOD_PRESSURE_SYS.name, 118.0, 2L),
            ),
        )

        val back = Backup.decode(Backup.encode(backup))

        assertEquals(backup.vitals, back.vitals)
    }

    @Test
    fun `vitals が無い版2のバックアップも読める`() {
        // 版 3 で vitals を足した。それ以前のファイルが読めなくなってはいけない
        val text = """
            {"schemaVersion":2,"appVersion":"0.9.0","timeZone":"Asia/Tokyo",
             "exportedAt":1,
             "days":[{"localDate":"2026-08-29","stepCount":5830,
                      "source":"SENSOR","updatedAt":1}]}
        """.trimIndent()

        val back = Backup.decode(text)

        assertEquals(1, back.days.size)
        assertTrue("vitals は空のはず", back.vitals.isEmpty())
    }

    @Test
    fun `目標の履歴も書き出して読み戻せる`() {
        // 目標を失うと、復元したあとに過去の達成判定が「いまの目標」で塗り替わる
        val backup = Backup(
            goals = listOf(
                ExportGoal("2026-01-01", 7_000, 100),
                ExportGoal("2026-02-01", 8_000, 200),
            ),
        )
        val back = Backup.decode(Backup.encode(backup))
        assertEquals(backup.goals, back.goals)
    }

    @Test
    fun `目標の履歴が無い古いバックアップも読める`() {
        val text = """
            {"schemaVersion":3,"appVersion":"0.9.0","timeZone":"Asia/Tokyo",
             "exportedAt":1,"days":[{"localDate":"2026-08-24","stepCount":8000,
             "source":"SENSOR","updatedAt":1}]}
        """.trimIndent()
        val back = Backup.decode(text)
        assertEquals(1, back.days.size)
        assertTrue("goals は空のはず", back.goals.isEmpty())
    }
}
