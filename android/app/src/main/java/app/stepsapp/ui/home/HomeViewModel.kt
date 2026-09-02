package app.stepsapp.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.stepsapp.data.local.PrefsStore
import app.stepsapp.data.repository.StepsRepository
import app.stepsapp.domain.Bucket
import app.stepsapp.domain.Goal
import app.stepsapp.domain.GoalHistory
import app.stepsapp.domain.HourlySteps
import app.stepsapp.domain.Period
import app.stepsapp.domain.aggregate
import app.stepsapp.domain.caloriesKcal
import app.stepsapp.domain.compareAverages
import app.stepsapp.domain.currentStreak
import app.stepsapp.domain.DistanceUnit
import app.stepsapp.domain.displayDistance
import app.stepsapp.domain.strideMetersOf
import app.stepsapp.domain.longestStreak
import app.stepsapp.domain.Trend
import app.stepsapp.domain.TrendPoint
import app.stepsapp.domain.latestWeight
import app.stepsapp.domain.summarize
import app.stepsapp.domain.weekStart
import app.stepsapp.domain.weightChange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

data class HomeUiState(
    val today: String = "",
    val todaySteps: Long = 0,
    val goal: Goal = Goal(Goal.DEFAULT),
    val streak: Int = 0,
    val bestStreak: Int = 0,
    /** 表示用の距離（単位は [distanceUnit] に従う） */
    val distance: Double = 0.0,
    val distanceUnit: DistanceUnit = DistanceUnit.DEFAULT,
    val calories: Double? = null,
    val period: Period = Period.DAY,
    /** 一覧に出しているぶん。全部ではない（[HomeViewModel.loadMoreBuckets]） */
    val buckets: List<Bucket> = emptyList(),
    /** まだ先の期間が残っているか。「もっと見る」を出すかの判断に使う */
    val canLoadMore: Boolean = false,
    /** 直前の同期間との平均の差。比較できなければ null */
    val comparison: Double? = null,
    val sensorAvailable: Boolean = true,
    val permissionGranted: Boolean = false,
    val healthConnectAvailable: Boolean = false,
    val healthConnectGranted: Boolean = false,
    val syncing: Boolean = false,
    /** 直近の体重(kg)。記録が無ければ null */
    val weightKg: Double? = null,
    /** 30日以内の記録と比べた増減(kg)。比べる相手が無ければ null */
    val weightChange: Double? = null,
    /** 直近の睡眠(分)。記録が無ければ null */
    val sleepMinutes: Long? = null,
    /** 直近の睡眠がいつのものか */
    val sleepDate: String? = null,
    /** 今日の1時間ごとの歩数。「日」表示のときだけ使う */
    val hourly: HourlySteps = HourlySteps.EMPTY,
    val weightTrend: Trend = Trend(emptyList(), null, null, null, null, null),
    val sleepTrend: Trend = Trend(emptyList(), null, null, null, null, null),
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = StepsRepository.getInstance(app)
    private val prefs = PrefsStore.getInstance(app)

    private val _state = MutableStateFlow(
        HomeUiState(
            today = repo.today(),
            // DB の Flow が最初の値を流すまで一瞬 0 が見えていた。
            // 前回出した値で埋めておく（日付が違えば 0 のまま）
            todaySteps = prefs.cachedStepsFor(repo.today()),
            goal = prefs.goal,
            sensorAvailable = repo.sensorAvailable(),
            healthConnectAvailable = repo.healthConnectAvailable(),
        ),
    )
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /** 全期間の記録。集計・ストリークの計算はここから行う。 */
    private var stepsByDate: Map<String, Long> = emptyMap()

    /**
     * 目標の履歴。ストリークは**その日に有効だった目標**で数える。
     *
     * DB から届くまでは現在の目標ひとつで代用する。目標を変えた直後の一瞬だけ
     * 古い数え方になるが、直後に読み直して上書きされる。
     */
    private var goals: GoalHistory = GoalHistory.single(prefs.goal)

    /**
     * 期間ごとの集計と前期間比較。
     *
     * **切り替えのたびに計算し直さない。** 記録が変わったときに4期間ぶんまとめて
     * 作っておき、タップでは取り出して差し替えるだけにする。
     */
    private var bucketsByPeriod: Map<Period, List<Bucket>> = emptyMap()
    private var comparisonByPeriod: Map<Period, Double?> = emptyMap()

    /**
     * 一覧に出している件数。
     *
     * **全部いっぺんに並べない。** 日で見ると 1,200 行になり、
     * 画面に入らない行まで作るぶんだけ切り替えが遅くなる。
     */
    private var shownRows = initialRowsFor(Period.DAY)

    /**
     * 歩数の読み込みが一度でも終わったか。
     *
     * これが false の間の recompute() で歩数を 0 にしてはいけない。
     * 起動直後は refreshGoal() などから DB より先に recompute() が走り、
     * 空の stepsByDate で埋め草のキャッシュを 0 に潰していた。
     */
    private var stepsLoaded = false
    private var weightByDate: Map<String, Double> = emptyMap()
    private var sleepByDate: Map<String, Long> = emptyMap()

    init {
        viewModelScope.launch {
            goals = repo.goalHistory()
            recompute()
        }
        loadHourly()
        viewModelScope.launch {
            // 集計とストリークに全期間が要るので、直近数年ぶんをまとめて監視する
            repo.recentDays(HISTORY_DAYS).collect { days ->
                stepsByDate = days.associate { it.localDate to it.stepCount }
                stepsLoaded = true
                recompute()
            }
        }
        viewModelScope.launch {
            repo.recentWeights(HISTORY_DAYS).collect { rows ->
                weightByDate = rows.associate { it.localDate to it.kg }
                recompute()
            }
        }
        viewModelScope.launch {
            repo.recentSleep(HISTORY_DAYS).collect { rows ->
                sleepByDate = rows.associate { it.localDate to it.minutes }
                recompute()
            }
        }
    }

    /**
     * 期間を切り替える。
     *
     * ここでは何も計算しない。1,200 日ぶんの集計とストリークを毎回やり直していたころは、
     * タップのたびに画面が引っかかっていた。
     */
    fun setPeriod(period: Period) {
        shownRows = initialRowsFor(period)
        val all = bucketsByPeriod[period] ?: emptyList()
        _state.value = _state.value.copy(
            period = period,
            buckets = all.take(shownRows),
            canLoadMore = all.size > shownRows,
            comparison = comparisonByPeriod[period],
        )
    }

    /** さらに過去を見る。ストリークのカレンダーと同じ考え方で少しずつ足す。 */
    fun loadMoreBuckets() {
        val period = _state.value.period
        shownRows += initialRowsFor(period)
        val all = bucketsByPeriod[period] ?: emptyList()
        _state.value = _state.value.copy(
            buckets = all.take(shownRows),
            canLoadMore = all.size > shownRows,
        )
    }

    fun refreshGoal() {
        // 目標の数字だけは待たせずに反映する
        _state.value = _state.value.copy(goal = prefs.goal)
        // 設定で目標を変えたときは履歴も増えているので読み直す
        viewModelScope.launch {
            goals = repo.goalHistory()
            recompute()
        }
    }

    fun refreshHealthConnect() {
        viewModelScope.launch {
            _state.value = _state.value.copy(healthConnectGranted = repo.healthConnectGranted())
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _state.value = _state.value.copy(permissionGranted = granted)
        if (granted) sync()
    }

    fun sync() {
        if (_state.value.syncing) return
        viewModelScope.launch {
            _state.value = _state.value.copy(syncing = true)
            try {
                // 何かの拍子に終わらなくても「更新中…」のまま固まらないようにする
                withTimeoutOrNull(SYNC_TIMEOUT_MS) { repo.sync() }
            } finally {
                _state.value = _state.value.copy(syncing = false, today = repo.today())
                loadHourly()
            }
        }
    }

    /**
     * 今日の歩いた時間帯を読み直す。
     *
     * Health Connect への問い合わせが入るので、集計とは別に非同期で取る。
     * 出るまでは空のまま（グラフの枠だけ出て、値が後から入る）。
     */
    private fun loadHourly() {
        viewModelScope.launch {
            val hourly = repo.hourlySteps(repo.today())
            _state.value = _state.value.copy(hourly = hourly)
        }
    }

    /** 記録が変わったときに1回だけ走る、重いほうの計算。 */
    private class Computed(
        val streak: Int,
        val bestStreak: Int,
        val buckets: Map<Period, List<Bucket>>,
        val comparisons: Map<Period, Double?>,
        val weightTrend: Trend,
        val sleepTrend: Trend,
        val weightChange: Double?,
        val weightKg: Double?,
        val sleepMinutes: Long?,
        val sleepDate: String?,
    )

    private suspend fun recompute() {
        val today = LocalDate.parse(repo.today())
        val goal = prefs.goal
        // 読み込み前は今出ている値（起動時の埋め草）を保つ
        val todaySteps =
            if (stepsLoaded) stepsByDate[today.toString()] ?: 0L else _state.value.todaySteps

        // 1,200 日ぶんの集計とストリークは UI スレッドでやると引っかかる。
        // 4期間ぶんまとめてここで作り、切り替えでは使い回す
        val c = withContext(Dispatchers.Default) {
            val latestSleep = sleepByDate.maxByOrNull { it.key }
            Computed(
                streak = currentStreak(stepsByDate, today, goals),
                bestStreak = longestStreak(stepsByDate, goals),
                buckets = Period.entries.associateWith { aggregate(stepsByDate, it) },
                comparisons = Period.entries.associateWith { comparisonFor(it, today) },
                weightTrend = summarize(weightByDate.map { TrendPoint(it.key, it.value) }),
                sleepTrend = summarize(
                    sleepByDate.map { TrendPoint(it.key, it.value.toDouble()) },
                ),
                weightChange = weightChange(weightByDate, today, WEIGHT_COMPARE_DAYS),
                weightKg = latestWeight(weightByDate)?.second,
                // 今日の記録が無くても、最後に眠れた分を出す
                sleepMinutes = latestSleep?.value,
                sleepDate = latestSleep?.key,
            )
        }

        bucketsByPeriod = c.buckets
        comparisonByPeriod = c.comparisons

        // 計算のあいだに他の更新が入っていることがあるので、状態は取り直す
        val s = _state.value
        _state.value = s.copy(
            today = today.toString(),
            todaySteps = todaySteps,
            goal = goal,
            streak = c.streak,
            bestStreak = c.bestStreak,
            distance = displayDistance(
                todaySteps * strideMetersOf(prefs.heightCm, prefs.strideCm) / 1000.0,
                prefs.distanceUnit,
            ),
            distanceUnit = prefs.distanceUnit,
            calories = caloriesKcal(todaySteps, prefs.heightCm, prefs.weightKg),
            buckets = (c.buckets[s.period] ?: emptyList()).take(shownRows),
            canLoadMore = (c.buckets[s.period]?.size ?: 0) > shownRows,
            comparison = c.comparisons[s.period],
            weightTrend = c.weightTrend,
            sleepTrend = c.sleepTrend,
            weightKg = c.weightKg,
            weightChange = c.weightChange,
            sleepMinutes = c.sleepMinutes,
            sleepDate = c.sleepDate,
        )

        // 次の起動で 0 が見えないよう、出した値を控えておく。
        // 0 では上書きしない。読み込みが済む前の recompute() で
        // せっかくのキャッシュを潰してしまうため
        if (todaySteps > 0) {
            prefs.lastShownDate = today.toString()
            prefs.lastShownSteps = todaySteps
        }
    }

    /** 直前の同じ期間と、同じ経過日数までで比べる。 */
    private fun comparisonFor(period: Period, today: LocalDate): Double? {
        val (currentRange, previousRange) = when (period) {
            Period.DAY -> return null   // 日単位の比較は意味が薄いので出さない
            Period.WEEK -> {
                val start = weekStart(today)
                start to start.minusWeeks(1)
            }
            Period.MONTH -> {
                val start = today.withDayOfMonth(1)
                start to start.minusMonths(1)
            }
            Period.YEAR -> {
                val start = today.withDayOfYear(1)
                start to start.minusYears(1)
            }
        }

        val current = stepsByDate.filterKeys { it >= currentRange.toString() }
        val elapsed = current.size.coerceAtLeast(1)
        val previous = stepsByDate.filterKeys {
            it >= previousRange.toString() && it < currentRange.toString()
        }
        return compareAverages(current, previous, elapsed)
    }

    companion object {
        /** 同期が終わらないときに諦める時間。 */
        const val SYNC_TIMEOUT_MS = 60_000L

        /** ストリークと年集計のため数年ぶんを見る。 */
        const val HISTORY_DAYS = 1200
        /** 体重の増減を比べる期間。 */
        const val WEIGHT_COMPARE_DAYS = 30L

        /**
         * 一覧に最初に出す件数。「もっと見る」で同じだけ足していく。
         *
         * 日は3か月、週は1年、月は2年ぶん。年は数が知れているので全部出す。
         */
        fun initialRowsFor(period: Period): Int = when (period) {
            Period.DAY -> 90
            Period.WEEK -> 52
            Period.MONTH -> 24
            Period.YEAR -> 50
        }
    }
}
