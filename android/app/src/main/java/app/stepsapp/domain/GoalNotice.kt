package app.stepsapp.domain

/** 目標に関して知らせる内容。 */
enum class GoalNotice {
    /** 目標を達成した。 */
    ACHIEVED,

    /** 日が傾いてきたが、あと少しで届く。 */
    ALMOST,
}

/**
 * 目標の達成率がこれを超えていれば「あと少し」を出す。
 *
 * 半分にも届いていない日に「あと少し」と言われても白々しい。
 */
private const val ALMOST_RATIO = 0.7f

/** 「あと少し」を出し始める時刻。これより前は一日がまだ長い。 */
private const val ALMOST_FROM_HOUR = 17

/**
 * いま出すべき知らせを決める。
 *
 * **同じ知らせは1日1回だけ。** 通知は鳴らしすぎると無視されるようになり、
 * 肝心なときに気づけなくなる（計測停止の通知と同じ考え方）。
 *
 * @param alreadyNotified 今日すでに出した知らせ
 */
fun goalNoticeFor(
    steps: Long,
    goal: Goal,
    hourOfDay: Int,
    alreadyNotified: Set<GoalNotice>,
): GoalNotice? {
    if (isAchieved(steps, goal) ) {
        return if (GoalNotice.ACHIEVED in alreadyNotified) null else GoalNotice.ACHIEVED
    }
    if (hourOfDay < ALMOST_FROM_HOUR) return null
    if (achievedRatio(steps, goal) < ALMOST_RATIO) return null
    return if (GoalNotice.ALMOST in alreadyNotified) null else GoalNotice.ALMOST
}

/** 通知に出す文言。 */
fun goalNoticeText(notice: GoalNotice, steps: Long, goal: Goal): Pair<String, String> =
    when (notice) {
        GoalNotice.ACHIEVED -> "目標達成" to "%,d 歩。今日の目標に届きました".format(steps)
        GoalNotice.ALMOST -> "あと %,d 歩".format(remainingSteps(steps, goal)) to
            "目標まであと少しです"
    }
