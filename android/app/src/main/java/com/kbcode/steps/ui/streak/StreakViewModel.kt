package com.kbcode.steps.ui.streak

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kbcode.steps.data.local.PrefsStore
import com.kbcode.steps.data.repository.StepsRepository
import com.kbcode.steps.domain.CalendarMonth
import com.kbcode.steps.domain.Goal
import com.kbcode.steps.domain.GoalHistory
import com.kbcode.steps.domain.Records
import com.kbcode.steps.domain.YearRecord
import com.kbcode.steps.domain.yearlyRecords
import com.kbcode.steps.domain.StreakSpan
import com.kbcode.steps.domain.achievedCount
import com.kbcode.steps.domain.currentStreak
import com.kbcode.steps.domain.longestSpan
import com.kbcode.steps.domain.recentMonths
import com.kbcode.steps.domain.records
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class StreakUiState(
    val goal: Goal = Goal(Goal.DEFAULT),
    val current: Int = 0,
    val best: Int = 0,
    val achieved: Int = 0,
    val bestSpan: StreakSpan? = null,
    val months: List<CalendarMonth> = emptyList(),
    /** これ以上遡れるか */
    val canLoadMore: Boolean = true,
    /** 自己記録。過去の自分と比べるための数字 */
    val records: Records = Records.EMPTY,
    /** 年ごとの記録。新しい年が先頭 */
    val years: List<YearRecord> = emptyList(),
)

class StreakViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = StepsRepository.getInstance(app)
    private val prefs = PrefsStore.getInstance(app)

    private val _state = MutableStateFlow(StreakUiState())
    val state: StateFlow<StreakUiState> = _state.asStateFlow()

    private var stepsByDate: Map<String, Long> = emptyMap()

    /** 目標の履歴。カレンダーの達成マークは**その日の目標**で決まる。 */
    private var goals: GoalHistory = GoalHistory.single(prefs.goal)
    private var monthCount = INITIAL_MONTHS

    init {
        viewModelScope.launch {
            goals = repo.goalHistory()
            recompute()
        }
        viewModelScope.launch {
            repo.recentDays(HISTORY_DAYS).collect { days ->
                stepsByDate = days.associate { it.localDate to it.stepCount }
                recompute()
            }
        }
    }

    /** さらに過去を見る。一度に全部作ると重いので少しずつ。 */
    fun loadMore() {
        monthCount += MORE_MONTHS
        viewModelScope.launch { recompute() }
    }

    private suspend fun recompute() {
        val goal = prefs.goal
        val today = LocalDate.now()
        val oldest = stepsByDate.keys.minOrNull()

        // マス目作りと最長期間の探索は UI スレッドでやると引っかかる。
        // 同じ計算を2度3度呼ばないよう、いちど作って使い回す
        _state.value = withContext(Dispatchers.Default) {
            val span = longestSpan(stepsByDate, goals)
            val months = recentMonths(today, monthCount, stepsByDate, goals)
            val allRecords = records(stepsByDate, goals)
            val byYear = yearlyRecords(stepsByDate)
            StreakUiState(
                goal = goal,
                current = currentStreak(stepsByDate, today, goals),
                best = span?.days ?: 0,
                achieved = achievedCount(stepsByDate, goals),
                bestSpan = span,
                months = months,
                // 記録の最も古い月まで出したら、それ以上は遡っても空になる
                canLoadMore = oldest != null &&
                    months.last().yearMonth.atDay(1).toString() > oldest,
                records = allRecords,
                years = byYear,
            )
        }
    }

    companion object {
        const val HISTORY_DAYS = 1200
        const val INITIAL_MONTHS = 3
        const val MORE_MONTHS = 6
    }
}
