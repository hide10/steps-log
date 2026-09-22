package app.stepsapp.data.local

import android.content.Context
import app.stepsapp.domain.Accent
import app.stepsapp.domain.cachedTodaySteps
import app.stepsapp.domain.DistanceUnit
import app.stepsapp.domain.HeightUnit
import app.stepsapp.domain.WeightUnit
import app.stepsapp.domain.Goal
import app.stepsapp.domain.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 秘密を含まない設定。目標歩数や体格など。
 *
 * ドライブの認可は Google 側が保持するので、ここに秘密は入らない。
 */
class PrefsStore private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("steps-prefs", Context.MODE_PRIVATE)

    var goalSteps: Long
        get() = prefs.getLong(KEY_GOAL, Goal.DEFAULT)
        set(value) = prefs.edit().putLong(KEY_GOAL, value.coerceAtLeast(1)).apply()

    val goal: Goal get() = Goal(goalSteps)

    /** 身長(cm)。距離の推定に使う。未設定は 0。 */
    var heightCm: Int
        get() = prefs.getInt(KEY_HEIGHT, 0)
        set(value) = prefs.edit().putInt(KEY_HEIGHT, value.coerceIn(0, 250)).apply()

    /** 体重(kg)。消費カロリーの推定に使う。未設定は 0。 */
    var weightKg: Int
        get() = prefs.getInt(KEY_WEIGHT, 0)
        set(value) = prefs.edit().putInt(KEY_WEIGHT, value.coerceIn(0, 300)).apply()

    /**
     * 最後に画面へ出した歩数と、その日付。
     *
     * 起動直後は DB の Flow が最初の値を流すまで一瞬 0 が見える。
     * その間の埋め草に使うだけで、**集計や記録には絶対に使わない**。
     * 日付が変わっていれば使わず 0 から始める（前日の歩数が今日として
     * 見えるほうが害が大きい）。
     */
    var lastShownSteps: Long
        get() = prefs.getLong(KEY_LAST_STEPS, 0)
        set(value) = prefs.edit().putLong(KEY_LAST_STEPS, value.coerceAtLeast(0)).apply()

    var lastShownDate: String
        get() = prefs.getString(KEY_LAST_DATE, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_LAST_DATE, value).apply()

    /** 今日ぶんとして使える見込み値。日付が違えば 0。 */
    fun cachedStepsFor(today: String): Long =
        cachedTodaySteps(today, lastShownDate, lastShownSteps)

    /** 見た目の設定。変更を画面に即反映させるため Flow で公開する。 */
    data class Appearance(val accent: Accent, val themeMode: ThemeMode)

    private val _appearance = MutableStateFlow(
        Appearance(
            Accent.from(prefs.getString(KEY_ACCENT, null)),
            ThemeMode.from(prefs.getString(KEY_THEME, null)),
        ),
    )
    val appearance: StateFlow<Appearance> = _appearance.asStateFlow()

    var accent: Accent
        get() = _appearance.value.accent
        set(value) {
            prefs.edit().putString(KEY_ACCENT, value.name).apply()
            _appearance.value = _appearance.value.copy(accent = value)
        }

    var themeMode: ThemeMode
        get() = _appearance.value.themeMode
        set(value) {
            prefs.edit().putString(KEY_THEME, value.name).apply()
            _appearance.value = _appearance.value.copy(themeMode = value)
        }

    /** 書き出し先フォルダ（フォルダ選択で得た URI）。未設定は空文字。 */
    var folderUri: String
        get() = prefs.getString(KEY_FOLDER, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_FOLDER, value).apply()

    /**
     * 最後に書き出しに成功した時刻(epoch millis)。0 なら未実施。
     *
     * 日次ジョブが本当に回っているかを目で確かめられるようにするため持つ。
     */
    var lastUploadAt: Long
        get() = prefs.getLong(KEY_LAST_UPLOAD, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_UPLOAD, value).apply()


    // --- 単位。**保存は常に km / kg / cm のまま**で、換算は表示のときだけ行う。
    //     単位を切り替えるたびに保存値を変換すると丸め誤差が蓄積してしまう。

    var distanceUnit: DistanceUnit
        get() = DistanceUnit.from(prefs.getString(KEY_DIST_UNIT, null))
        set(value) = prefs.edit().putString(KEY_DIST_UNIT, value.name).apply()

    var heightUnit: HeightUnit
        get() = HeightUnit.from(prefs.getString(KEY_HEIGHT_UNIT, null))
        set(value) = prefs.edit().putString(KEY_HEIGHT_UNIT, value.name).apply()

    var weightUnit: WeightUnit
        get() = WeightUnit.from(prefs.getString(KEY_WEIGHT_UNIT, null))
        set(value) = prefs.edit().putString(KEY_WEIGHT_UNIT, value.name).apply()

    /** 歩幅の直接指定(cm)。0 なら身長から推定する。 */
    var strideCm: Int
        get() = prefs.getInt(KEY_STRIDE, 0)
        set(value) = prefs.edit().putInt(KEY_STRIDE, value.coerceIn(0, 200)).apply()

    /**
     * 過去データを取り込み済みか。
     *
     * Health Connect への問い合わせは日ごとなので、3年ぶんだと1000回を超える。
     * 毎回やると「いま読み取る」が何十秒も終わらない。初回だけにする。
     */
    var backfillDone: Boolean
        get() = prefs.getBoolean(KEY_BACKFILL, false)
        set(value) = prefs.edit().putBoolean(KEY_BACKFILL, value).apply()

    /** 取り込み直しを促す（設定から手動で呼ぶ）。 */
    fun resetBackfill() {
        prefs.edit().putBoolean(KEY_BACKFILL, false).apply()
    }

    /** 初回セットアップを終えたか。終わっていなければ起動時に案内を出す。 */
    var setupDone: Boolean
        get() = prefs.getBoolean(KEY_SETUP, false)
        set(value) = prefs.edit().putBoolean(KEY_SETUP, value).apply()

    companion object {
        private const val KEY_SETUP = "setup_done"
        private const val KEY_BACKFILL = "backfill_done"
        private const val KEY_DIST_UNIT = "distance_unit"
        private const val KEY_HEIGHT_UNIT = "height_unit"
        private const val KEY_WEIGHT_UNIT = "weight_unit"
        private const val KEY_STRIDE = "stride_cm"
        private const val KEY_LAST_UPLOAD = "last_upload_at"
        private const val KEY_FOLDER = "folder_uri"
        private const val KEY_LAST_STEPS = "last_shown_steps"
        private const val KEY_LAST_DATE = "last_shown_date"
        private const val KEY_ACCENT = "accent"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_GOAL = "goal_steps"
        private const val KEY_HEIGHT = "height_cm"
        private const val KEY_WEIGHT = "weight_kg"

        @Volatile
        private var instance: PrefsStore? = null

        fun getInstance(context: Context): PrefsStore =
            instance ?: synchronized(this) {
                instance ?: PrefsStore(context.applicationContext).also { instance = it }
            }
    }
}
