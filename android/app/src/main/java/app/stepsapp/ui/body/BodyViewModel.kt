package app.stepsapp.ui.body

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.stepsapp.data.repository.StepsRepository
import app.stepsapp.domain.Trend
import app.stepsapp.domain.TrendPoint
import app.stepsapp.data.local.PrefsStore
import app.stepsapp.domain.VitalKind
import app.stepsapp.domain.WeightUnit
import app.stepsapp.domain.displayWeight
import app.stepsapp.domain.summarize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 一覧に並べる1項目。値と、増えているか減っているかだけを見せる。 */
data class VitalRow(
    val kind: VitalKind,
    val value: Double,
    val date: String,
    val change: Double?,
)

data class BodyUiState(
    val weightLatest: Double? = null,
    val weightUnit: WeightUnit = WeightUnit.DEFAULT,
    val weightTrend: Trend = EMPTY,
    val sleepLatest: Long? = null,
    val sleepDate: String? = null,
    val sleepTrend: Trend = EMPTY,
    val vitals: List<VitalRow> = emptyList(),
    /** 展開してグラフを見せている項目 */
    val expanded: VitalKind? = null,
    val expandedTrend: Trend = EMPTY,
) {
    companion object {
        val EMPTY = Trend(emptyList(), null, null, null, null, null)
    }
}

class BodyViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = StepsRepository.getInstance(app)
    private val prefs = PrefsStore.getInstance(app)

    private val _state = MutableStateFlow(BodyUiState())
    val state: StateFlow<BodyUiState> = _state.asStateFlow()

    private var vitalHistory: Map<VitalKind, List<TrendPoint>> = emptyMap()

    init {
        viewModelScope.launch {
            repo.recentWeights(HISTORY_DAYS).collect { rows ->
                // 保存は kg のまま。表示のときだけ換算する
                val unit = prefs.weightUnit
                val points = rows.map { TrendPoint(it.localDate, displayWeight(it.kg, unit)) }
                _state.value = _state.value.copy(
                    weightLatest = points.maxByOrNull { it.localDate }?.value,
                    weightTrend = summarize(points),
                    weightUnit = unit,
                )
            }
        }
        viewModelScope.launch {
            repo.recentSleep(HISTORY_DAYS).collect { rows ->
                val latest = rows.maxByOrNull { it.localDate }
                _state.value = _state.value.copy(
                    sleepLatest = latest?.minutes,
                    sleepDate = latest?.localDate,
                    sleepTrend = summarize(rows.map { TrendPoint(it.localDate, it.minutes.toDouble()) }),
                )
            }
        }
        viewModelScope.launch {
            repo.recentVitals(HISTORY_DAYS).collect { rows ->
                vitalHistory = rows
                    .mapNotNull { r -> VitalKind.from(r.kind)?.let { it to TrendPoint(r.localDate, r.value) } }
                    .groupBy({ it.first }, { it.second })

                // 種類ごとに最新の1件を一覧に出す。並び順は定義順にして毎回同じ場所に出す
                val rowsOut = VitalKind.entries.mapNotNull { kind ->
                    val points = vitalHistory[kind] ?: return@mapNotNull null
                    val latest = points.maxByOrNull { it.localDate } ?: return@mapNotNull null
                    VitalRow(kind, latest.value, latest.localDate, summarize(points).change)
                }
                _state.value = _state.value.copy(
                    vitals = rowsOut,
                    expandedTrend = _state.value.expanded
                        ?.let { summarize(vitalHistory[it].orEmpty()) } ?: BodyUiState.EMPTY,
                )
            }
        }
    }

    /** 同じ項目をもう一度押したら閉じる。開けるのは一度に1つだけ。 */
    fun toggle(kind: VitalKind) {
        val next = if (_state.value.expanded == kind) null else kind
        _state.value = _state.value.copy(
            expanded = next,
            expandedTrend = next?.let { summarize(vitalHistory[it].orEmpty()) } ?: BodyUiState.EMPTY,
        )
    }

    companion object {
        const val HISTORY_DAYS = 90
    }
}
