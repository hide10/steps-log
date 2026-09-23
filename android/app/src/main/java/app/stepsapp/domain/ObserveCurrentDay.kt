package app.stepsapp.domain

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.ZonedDateTime

/**
 * 端末の暦日が変わったら、監視対象も新しい日の行へ切り替える。
 *
 * coroutine のタイムアウトは暦時計ではなく、Android の深いスリープ中は進まない。
 * 深夜まで一度で待つと翌朝も前日の行を見続けるため、起きている間は最大1分で確認する。
 * DB の更新が先に来たときも日付を確認し、古い日の値を流さず監視し直す。
 * これは表示対象の切替だけで、未計測の日に0歩のレコードを作るものではない。
 */
fun <T> observeCurrentDay(
    now: () -> ZonedDateTime = { ZonedDateTime.now() },
    observeDay: (String) -> Flow<T>,
): Flow<T> = flow {
    while (currentCoroutineContext().isActive) {
        val current = now()
        val date = current.toLocalDate()
        val untilMidnight = Duration.between(
            current,
            date.plusDays(1).atStartOfDay(current.zone),
        ).toMillis().coerceIn(1, 60_000)
        withTimeoutOrNull(untilMidnight) {
            emitAll(
                observeDay(date.toString())
                    .takeWhile { now().toLocalDate() == date },
            )
        }
    }
}.distinctUntilChanged()
