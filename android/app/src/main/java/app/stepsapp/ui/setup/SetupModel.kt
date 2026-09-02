package app.stepsapp.ui.setup

/**
 * 初回セットアップの各画面。
 *
 * **どの手順も飛ばせるようにする。** 権限を強制すると、拒否したときに
 * 何もできない画面で行き止まりになる。飛ばしても歩数の記録は動くし、
 * 必要になれば本体の画面から改めて促される。
 */
enum class SetupStep(
    val title: String,
    val body: String,
    val primary: String,
    /** 飛ばすためのリンク。null なら出さない */
    val secondary: String?,
) {
    WELCOME(
        title = "歩数ログへようこそ",
        body = "歩いた記録は、この端末の中だけ。\nどこかへ送られることはありません。",
        primary = "はじめる",
        secondary = null,
    ),
    ACTIVITY(
        title = "「身体活動」を許可",
        body = "歩数を数えるのに使います。\n許可すると、今日ぶんから記録がはじまります。",
        primary = "許可する",
        secondary = "あとで",
    ),
    HEALTH_CONNECT(
        title = "Health Connect を許可",
        body = "スマホにもう入っている歩数や体重、睡眠をまとめて読み込みます。\n" +
            "アプリを開いていない間の歩数も、あとから拾えます。",
        primary = "許可する",
        secondary = "スキップ",
    ),
    BATTERY(
        title = "電池の最適化を外す",
        body = "省エネ設定が入ったままだと、アプリが途中で止められることがあります。\n" +
            "歩数が抜ける原因になるので、ここだけは外しておくと安心です。",
        primary = "設定を開く",
        secondary = "あとで",
    ),
    GOAL(
        title = "1日の目標を決める",
        body = "まずは無理のないところから。\nあとからいつでも変えられます。",
        primary = "この目標ではじめる",
        secondary = null,
    ),
    ;

    val index: Int get() = ordinal

    companion object {
        val ALL = entries
        val COUNT = entries.size
    }
}
