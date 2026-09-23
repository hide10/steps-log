package app.stepsapp.ui.review

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.stepsapp.data.repository.StepsRepository
import app.stepsapp.domain.WeeklyReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReviewUiState(
    val report: WeeklyReport? = null,
    val loading: Boolean = true,
)

class ReviewViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = StepsRepository.getInstance(app)
    private val _state = MutableStateFlow(ReviewUiState())
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()

    fun load(weekStart: String) {
        _state.value = ReviewUiState()
        viewModelScope.launch {
            _state.value = ReviewUiState(
                report = repo.completedWeeklyReport(weekStart),
                loading = false,
            )
        }
    }
}
