package app.stepsapp.ui.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.stepsapp.data.local.PrefsStore
import app.stepsapp.domain.Accent
import app.stepsapp.domain.DistanceUnit
import app.stepsapp.domain.HeightUnit
import app.stepsapp.domain.WeightUnit
import app.stepsapp.domain.ThemeMode
import app.stepsapp.data.remote.UploadResult
import app.stepsapp.data.repository.StepsRepository
import app.stepsapp.data.repository.UploadRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val accent: Accent = Accent.DEFAULT,
    val themeMode: ThemeMode = ThemeMode.DEFAULT,
    val goalSteps: Long = 0,
    val heightCm: String = "",
    val weightKg: String = "",
    val strideCm: String = "",
    val distanceUnit: DistanceUnit = DistanceUnit.DEFAULT,
    val heightUnit: HeightUnit = HeightUnit.DEFAULT,
    val weightUnit: WeightUnit = WeightUnit.DEFAULT,
    val busy: Boolean = false,
    val message: String? = null,
    /** 選ばれている保存先フォルダの名前。未設定なら null */
    val folderLabel: String? = null,
    /** 最後に書き出せた時刻。未実施なら null */
    val lastUploadAt: Long? = null,
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = PrefsStore.getInstance(app)
    private val uploads = UploadRepository.getInstance(app)
    private val steps = StepsRepository.getInstance(app)

    private val _state = MutableStateFlow(
        SettingsUiState(
            accent = prefs.accent,
            themeMode = prefs.themeMode,
            goalSteps = prefs.goalSteps,
            heightCm = prefs.heightCm.takeIf { it > 0 }?.toString().orEmpty(),
            weightKg = prefs.weightKg.takeIf { it > 0 }?.toString().orEmpty(),
            strideCm = prefs.strideCm.takeIf { it > 0 }?.toString().orEmpty(),
            distanceUnit = prefs.distanceUnit,
            heightUnit = prefs.heightUnit,
            weightUnit = prefs.weightUnit,
            folderLabel = uploads.folderLabel(),
            lastUploadAt = uploads.lastUploadAt(),
        ),
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun setAccent(accent: Accent) {
        prefs.accent = accent
        _state.value = _state.value.copy(accent = accent)
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.themeMode = mode
        _state.value = _state.value.copy(themeMode = mode)
    }

    fun setDistanceUnit(u: DistanceUnit) {
        prefs.distanceUnit = u
        _state.value = _state.value.copy(distanceUnit = u)
    }

    fun setHeightUnit(u: HeightUnit) {
        prefs.heightUnit = u
        _state.value = _state.value.copy(heightUnit = u)
    }

    fun setWeightUnit(u: WeightUnit) {
        prefs.weightUnit = u
        _state.value = _state.value.copy(weightUnit = u)
    }

    /** 歩幅の直接指定。空なら身長からの推定に戻る。 */
    fun setStride(value: String) {
        val digits = value.filter { it.isDigit() }.take(3)
        _state.value = _state.value.copy(strideCm = digits)
        prefs.strideCm = digits.toIntOrNull() ?: 0
    }

    /**
     * 目標を変える。**変更は今日から有効**で、過去の達成判定は変わらない。
     */
    fun setGoal(goalSteps: Long) {
        viewModelScope.launch {
            steps.setGoal(goalSteps)
            _state.value = _state.value.copy(goalSteps = prefs.goalSteps)
        }
    }

    /** 身長・体重は空欄を許す（未設定なら距離は既定の歩幅、カロリーは非表示）。 */
    fun setHeight(value: String) {
        val digits = value.filter { it.isDigit() }.take(3)
        _state.value = _state.value.copy(heightCm = digits)
        prefs.heightCm = digits.toIntOrNull() ?: 0
    }

    fun setWeight(value: String) {
        val digits = value.filter { it.isDigit() }.take(3)
        _state.value = _state.value.copy(weightKg = digits)
        prefs.weightKg = digits.toIntOrNull() ?: 0
    }

    /** フォルダ選択から戻ってきたら覚えて、そのまま一度書き出してみる。 */
    fun onFolderPicked(uri: Uri?) {
        if (uri == null) return
        uploads.rememberFolder(uri)
        _state.value = _state.value.copy(folderLabel = uploads.folderLabel())
        uploadNow()
    }

    fun forgetFolder() {
        uploads.forgetFolder()
        _state.value = _state.value.copy(folderLabel = null, message = "保存先を解除しました")
    }

    fun uploadNow() {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            report(
                runCatching { uploads.upload() }
                    .getOrElse { UploadResult.Failure(it.message ?: it.toString()) },
            )
        }
    }

    private fun report(result: UploadResult) {
        _state.value = _state.value.copy(
            busy = false,
            lastUploadAt = uploads.lastUploadAt(),
            message = when (result) {
                is UploadResult.Success -> "${result.fileName} を書き出しました"
                is UploadResult.Failure -> "失敗: ${result.message}"
            },
        )
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
