package app.stepsapp.domain

/**
 * 共有画像に何を載せるか。
 *
 * **既定では歩数と日付しか載せない。** 歩数は行動の記録でもあり、
 * 連続日数・距離・推移まで一緒に出すと生活パターン（在宅/外出、
 * 出かける曜日の傾向）が読み取れてしまう。増やす側をユーザーの
 * 明示的な操作にしておく。
 */
data class ShareOptions(
    val includeDate: Boolean = true,
    val includeGoal: Boolean = false,
    val includeStreak: Boolean = false,
    val includeDistance: Boolean = false,
    /** 直近の推移をグラフで載せる。数日ぶんの行動が一度に出るので既定は off。 */
    val includeTrend: Boolean = false,
) {
    companion object {
        /** 何も足していない状態。共有画面を開くたびにここから始める。 */
        val DEFAULT = ShareOptions()
    }
}

/**
 * 載せる項目の雛形。
 *
 * 毎回チェックを付け直さずに済むように用意する。
 * **既定は [SIMPLE] のまま。** 増やす側をユーザーの明示的な操作にしておく、
 * という [ShareOptions] の方針は変えない。
 */
enum class SharePreset(val label: String, val options: ShareOptions) {
    /** 歩数と日付だけ。何も足していない状態。 */
    SIMPLE("シンプル", ShareOptions()),

    /** 目標・連続日数・距離まで。歩数計アプリらしい一枚になる。 */
    DETAILED(
        "しっかり",
        ShareOptions(
            includeDate = true,
            includeGoal = true,
            includeStreak = true,
            includeDistance = true,
        ),
    ),

    /** 推移のグラフも含めて全部。 */
    ALL(
        "ぜんぶ",
        ShareOptions(
            includeDate = true,
            includeGoal = true,
            includeStreak = true,
            includeDistance = true,
            includeTrend = true,
        ),
    ),
    ;

    companion object {
        /** いまの設定と一致する雛形。どれとも違えば null（手で足し引きした状態）。 */
        fun of(options: ShareOptions): SharePreset? =
            entries.firstOrNull { it.options == options }
    }
}

/**
 * 共有画像に実際に描く内容。
 *
 * **オフにした項目は null（グラフは空リスト）になる。** 描画側はこの型だけを
 * 見て描くこと。元データを直接参照すると、オフにしたはずの値が画像に載る
 * 事故が起きる。
 */
data class ShareCard(
    val steps: Long,
    val date: String?,
    val goal: String?,
    val streak: String?,
    /** 距離の数値部分。単位と分けてあるのは、数値だけ大きく描くため */
    val distanceValue: String?,
    val distanceUnit: String?,
    /** 折れ線グラフに描く直近の値。空なら描かない */
    val trend: List<Long> = emptyList(),
) {
    /** 歩数以外に載っている項目。プレビューの「これが写ります」表示に使う。 */
    fun extraLines(): List<String> = listOfNotNull(
        date,
        goal,
        streak,
        distanceValue?.let { "$it ${distanceUnit.orEmpty()}".trim() },
        if (trend.isNotEmpty()) "推移のグラフ" else null,
    )
}

/** グラフに使う点の数。これ以上増やしても小さくて読めない。 */
private const val TREND_POINTS = 7

/**
 * 共有カードを組み立てる。
 *
 * 値が無い（未計測・未設定）ものは、オンにしていても載せない。
 * 「0 km」や「0 日」が写って誤解を招くのを避けるため。
 */
fun buildShareCard(
    steps: Long,
    date: String,
    goalSteps: Long,
    streakDays: Int,
    distance: Double,
    distanceUnit: DistanceUnit,
    trend: List<Long> = emptyList(),
    options: ShareOptions = ShareOptions.DEFAULT,
): ShareCard = ShareCard(
    steps = steps,
    date = if (options.includeDate) date else null,
    goal = if (options.includeGoal && goalSteps > 0) "目標 %,d 歩".format(goalSteps) else null,
    streak = if (options.includeStreak && streakDays > 0) "%d 日連続".format(streakDays) else null,
    distanceValue = if (options.includeDistance && distance > 0) "%.1f".format(distance) else null,
    distanceUnit = if (options.includeDistance && distance > 0) distanceUnit.suffix else null,
    // 点が1つでは線にならないので、2点未満なら描かない
    trend = if (options.includeTrend && trend.size >= 2) trend.takeLast(TREND_POINTS) else emptyList(),
)
