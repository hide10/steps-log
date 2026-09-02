package app.stepsapp.domain

/**
 * 分析用の CSV。表計算ソフトにそのまま食わせられる素朴な3カラム。
 *
 * 完全なバックアップには使わない（生ログを持てないため）。それは [Backup] の役目。
 */
object Csv {

    const val HEADER = "local_date,step_count,source"

    fun encode(days: List<ExportDay>): String = buildString {
        appendLine(HEADER)
        days.sortedBy { it.localDate }.forEach {
            appendLine("${it.localDate},${it.stepCount},${it.source}")
        }
    }

    /**
     * CSV を読み込む。ヘッダー行は任意。壊れた行は黙って捨てずに例外にする。
     *
     * @param updatedAt 取り込んだレコードの更新時刻として使う値
     */
    fun decode(text: String, updatedAt: Long): List<ExportDay> {
        val lines = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        return lines
            .filterIndexed { index, line -> !(index == 0 && line.startsWith("local_date")) }
            .map { line ->
                val parts = line.split(",").map { it.trim() }
                require(parts.size >= 2) { "列が足りない行: $line" }
                val steps = parts[1].toLongOrNull()
                    ?: throw IllegalArgumentException("歩数が数値でない行: $line")
                require(steps >= 0) { "歩数が負の行: $line" }
                ExportDay(
                    localDate = parts[0],
                    stepCount = steps,
                    source = parts.getOrNull(2)?.takeIf { it.isNotEmpty() }
                        ?: StepSource.IMPORTED.name,
                    updatedAt = updatedAt,
                )
            }
    }
}
