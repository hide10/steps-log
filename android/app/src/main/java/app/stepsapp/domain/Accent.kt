package app.stepsapp.domain

/**
 * 達成リングやグラフに使うアクセントカラー。
 *
 * 色は端末のテーマ（ライト/ダーク）に依らず同じものを使う。
 * Material3 の配色に馴染むよう彩度を抑えた値にしてある。
 *
 * **達成色は同じ色相の濃いほうにする。** 当初は達成時を一律で緑にしていたが、
 * それだと目標を達成している間じゅう、選んだ色がまったく見えなくなってしまった
 * （オレンジを選んでもリングは緑）。達成したことはリングが一周することと
 * 「目標達成」の文字で分かるので、色まで置き換える必要はない。
 */
enum class Accent(val label: String, val rgb: Long, val achievedRgb: Long) {
    BLUE("ブルー", 0xFF4C8BF5, 0xFF2565D8),
    GREEN("グリーン", 0xFF3FBE83, 0xFF1B7F52),
    ORANGE("オレンジ", 0xFFE8833A, 0xFFC25F16),
    PURPLE("パープル", 0xFF8A6BD1, 0xFF6242B0),
    PINK("ピンク", 0xFFD9578C, 0xFFB33366),
    TEAL("ティール", 0xFF2AA6A6, 0xFF16807F),
    ;

    companion object {
        val DEFAULT = BLUE

        fun from(name: String?): Accent =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** テーマの明るさ。端末に合わせるのが既定。 */
enum class ThemeMode(val label: String) {
    SYSTEM("端末に合わせる"),
    LIGHT("ライト"),
    DARK("ダーク"),
    ;

    companion object {
        val DEFAULT = SYSTEM

        fun from(name: String?): ThemeMode =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
