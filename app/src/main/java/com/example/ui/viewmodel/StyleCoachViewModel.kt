package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.core.db.entities.StyleProfileEntity
import com.example.core.db.entities.StyleProfileHistoryEntity
import com.example.domain.repository.StyleProfileRepository
import com.example.domain.usecase.StyleAnalysisUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StyleCoachUiState(
    val profile: StyleProfileEntity? = null,
    val history: List<StyleProfileHistoryEntity> = emptyList(),
    val isTraining: Boolean = false,
    val isAutoAnalyzing: Boolean = false,
    val trainSuccess: Boolean = false,
    val statusMessageRes: Int? = null,
    val statusIsError: Boolean = false,
)

@HiltViewModel
class StyleCoachViewModel @Inject constructor(
    private val styleProfileRepository: StyleProfileRepository,
    private val styleAnalysisUseCase: StyleAnalysisUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(StyleCoachUiState())
    val uiState: StateFlow<StyleCoachUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            styleProfileRepository.getProfile().collect { profile ->
                val historyList = styleProfileRepository.getHistory()
                _uiState.value = _uiState.value.copy(
                    profile = profile,
                    history = historyList
                )
            }
        }
    }

    fun trainStyle(samples: List<String>) {
        if (samples.isEmpty()) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isTraining = true,
                    trainSuccess = false,
                    statusMessageRes = null,
                    statusIsError = false,
                )
            }
            try {
                styleAnalysisUseCase.analyzeAndSave(samples, "MANUAL_TRAINING")
                refreshProfileState(
                    isTraining = false,
                    trainSuccess = true,
                    statusMessageRes = R.string.style_coach_status_manual_success,
                    statusIsError = false,
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isTraining = false,
                        trainSuccess = false,
                        statusMessageRes = R.string.style_coach_error_manual_failed,
                        statusIsError = true,
                    )
                }
            }
        }
    }

    fun analyzeRecentSentMessages() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isAutoAnalyzing = true,
                    trainSuccess = false,
                    statusMessageRes = null,
                    statusIsError = false,
                )
            }
            try {
                val analyzed = styleAnalysisUseCase()
                refreshProfileState(
                    isAutoAnalyzing = false,
                    trainSuccess = analyzed,
                    statusMessageRes = if (analyzed) {
                        R.string.style_coach_status_auto_success
                    } else {
                        R.string.style_coach_status_auto_empty
                    },
                    statusIsError = false,
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isAutoAnalyzing = false,
                        trainSuccess = false,
                        statusMessageRes = R.string.style_coach_error_auto_failed,
                        statusIsError = true,
                    )
                }
            }
        }
    }

    private suspend fun refreshProfileState(
        isTraining: Boolean = _uiState.value.isTraining,
        isAutoAnalyzing: Boolean = _uiState.value.isAutoAnalyzing,
        trainSuccess: Boolean = _uiState.value.trainSuccess,
        statusMessageRes: Int? = _uiState.value.statusMessageRes,
        statusIsError: Boolean = _uiState.value.statusIsError,
    ) {
        val historyList = styleProfileRepository.getHistory()
        val profile = styleProfileRepository.getProfileOnce()
        _uiState.update {
            it.copy(
                isTraining = isTraining,
                isAutoAnalyzing = isAutoAnalyzing,
                trainSuccess = trainSuccess,
                profile = profile,
                history = historyList,
                statusMessageRes = statusMessageRes,
                statusIsError = statusIsError,
            )
        }
    }
}
