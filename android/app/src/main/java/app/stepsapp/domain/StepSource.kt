package app.stepsapp.domain

/** 歩数の取得元。1日の採用値は必ずこのうちのどれか1つで、複数を合算しない。 */
enum class StepSource {
    SENSOR,
    HEALTH_CONNECT,
    MANUAL,
    IMPORTED,
    ;

    companion object {
        fun from(raw: String): StepSource =
            entries.firstOrNull { it.name == raw } ?: MANUAL
    }
}
