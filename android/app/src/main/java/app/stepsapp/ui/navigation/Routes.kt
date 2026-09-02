package app.stepsapp.ui.navigation

/**
 * 下部タブで分ける。
 *
 * 歩数タブは**スクロールなしで集計まで見える**ことを最優先にする。
 * 体重や睡眠のカードを歩数の下に積むと、主役である日/週/月/年の集計が
 * 画面外へ押し出されてしまうため、別タブへ分けた。
 */
object Routes {
    const val STEPS = "steps"
    const val BODY = "body"
    const val SETTINGS = "settings"
    const val STREAK = "streak"
    const val BACKUP = "backup"
    const val SHARE = "share"
}
