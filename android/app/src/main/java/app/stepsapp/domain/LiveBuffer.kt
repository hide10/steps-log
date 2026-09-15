package app.stepsapp.domain

/** 常駐中に受け取ったセンサーの値1件。日付は受け取った時点で決める。 */
data class LiveEvent(
    val reading: Long,
    val date: String,
    val at: Long,
)

/**
 * 常駐中のセンサーの値を、DB に書く単位へ間引く。Android に依存しない。
 *
 * 歩いている間は1歩ごとにイベントが来るので、全部を書くと重い。
 * [minIntervalMs] に1回まで間引き、間の値は最新の1件だけを持っておく。
 *
 * **日付が変わったら、前の日の最後の値を必ず先に書く。** 間引いたまま日をまたぐと、
 * 前の日の最後に歩いた分が新しい日に入ってしまう。
 */
class LiveBuffer(private val minIntervalMs: Long) {

    private var pending: LiveEvent? = null
    private var lastWriteAt: Long? = null

    /** 値を受け取り、いま書くべきものを古い順に返す。 */
    fun offer(event: LiveEvent): List<LiveEvent> {
        val out = mutableListOf<LiveEvent>()
        pending?.let { if (it.date != event.date) out += it }

        val last = lastWriteAt
        if (out.isNotEmpty() || last == null || event.at - last >= minIntervalMs) {
            out += event
            lastWriteAt = event.at
            pending = null
        } else {
            pending = event
        }
        return out
    }

    /** 間引いて持っている値。しばらくイベントが来ないときと、止まるときに書く。 */
    fun drain(): List<LiveEvent> {
        val p = pending ?: return emptyList()
        pending = null
        lastWriteAt = p.at
        return listOf(p)
    }
}
