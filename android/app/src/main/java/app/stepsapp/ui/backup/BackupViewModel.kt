package app.stepsapp.ui.backup

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.stepsapp.data.repository.BackupRepository
import app.stepsapp.domain.Backup
import app.stepsapp.domain.Csv
import app.stepsapp.domain.ExportDay
import app.stepsapp.domain.ExportGoal
import app.stepsapp.domain.ExportRaw
import app.stepsapp.domain.ExportSleep
import app.stepsapp.domain.ExportVital
import app.stepsapp.domain.ExportWeight
import app.stepsapp.domain.ImportMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 取り込み前の確認内容。 */
data class PendingImport(
    val uri: Uri,
    val days: List<ExportDay>,
    val raw: List<ExportRaw>,
    val weights: List<ExportWeight>,
    val sleep: List<ExportSleep>,
    val vitals: List<ExportVital>,
    val goals: List<ExportGoal>,
    val changedCount: Int,
)

data class BackupUiState(
    val mode: ImportMode = ImportMode.MERGE,
    val busy: Boolean = false,
    val message: String? = null,
    val pending: PendingImport? = null,
)

class BackupViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = BackupRepository.getInstance(app)

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    fun setMode(mode: ImportMode) {
        _state.value = _state.value.copy(mode = mode)
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun cancelImport() {
        _state.value = _state.value.copy(pending = null)
    }

    fun exportTo(uri: Uri, asCsv: Boolean) {
        run {
            viewModelScope.launch {
                _state.value = _state.value.copy(busy = true)
                val result = runCatching {
                    val text = if (asCsv) repo.exportCsv() else repo.exportJson()
                    withContext(Dispatchers.IO) {
                        getApplication<Application>().contentResolver
                            .openOutputStream(uri, "wt")
                            ?.use { it.write(text.toByteArray()) }
                            ?: error("ファイルを開けなかった")
                    }
                    text.lineSequence().count()
                }
                _state.value = _state.value.copy(
                    busy = false,
                    message = result.fold(
                        onSuccess = { "書き出しました" },
                        onFailure = { "書き出しに失敗: ${it.message}" },
                    ),
                )
            }
        }
    }

    /** 読み込んで内容を確認する。実際の書き込みは [confirmImport] まで行わない。 */
    fun prepareImport(uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            val result = runCatching {
                val text = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver
                        .openInputStream(uri)
                        ?.use { it.readBytes().decodeToString() }
                        ?: error("ファイルを開けなかった")
                }
                parse(text)
            }
            _state.value = result.fold(
                onSuccess = { parsed ->
                    val (days, raw, weights, sleep, vitals, goals) = parsed
                    val changed = repo.previewImport(days, _state.value.mode)
                    _state.value.copy(
                        busy = false,
                        pending = PendingImport(
                            uri, days, raw, weights, sleep, vitals, goals, changed,
                        ),
                    )
                },
                onFailure = {
                    _state.value.copy(busy = false, message = "読み込みに失敗: ${it.message}")
                },
            )
        }
    }

    fun confirmImport() {
        val pending = _state.value.pending ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, pending = null)
            val result = runCatching {
                repo.import(
                    days = pending.days,
                    raw = pending.raw,
                    weights = pending.weights,
                    sleep = pending.sleep,
                    vitals = pending.vitals,
                    goals = pending.goals,
                    mode = _state.value.mode,
                )
            }
            _state.value = _state.value.copy(
                busy = false,
                message = result.fold(
                    onSuccess = { "${it.readCount}件を読み、${it.changedCount}件を反映しました" },
                    onFailure = { "取り込みに失敗: ${it.message}" },
                ),
            )
        }
    }

    /** 読み込んだ中身。 */
    private data class Parsed(
        val days: List<ExportDay>,
        val raw: List<ExportRaw>,
        val weights: List<ExportWeight>,
        val sleep: List<ExportSleep>,
        val vitals: List<ExportVital>,
        val goals: List<ExportGoal>,
    )

    /** JSON か CSV かは中身で判別する（拡張子に頼らない）。 */
    private fun parse(text: String): Parsed {
        val trimmed = text.trimStart()
        return if (trimmed.startsWith("{")) {
            val backup = Backup.decode(text)
            Parsed(
                backup.days, backup.raw, backup.weights, backup.sleep,
                backup.vitals, backup.goals,
            )
        } else {
            // CSV は歩数だけ。体重・睡眠は完全バックアップ（JSON）にしか入らない
            Parsed(
                Csv.decode(text, updatedAt = System.currentTimeMillis()),
                emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
            )
        }
    }
}
