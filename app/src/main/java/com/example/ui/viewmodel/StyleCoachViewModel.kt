package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.db.entities.StyleProfileEntity
import com.example.core.db.entities.StyleProfileHistoryEntity
import com.example.domain.repository.StyleProfileRepository
import com.example.domain.usecase.StyleAnalysisUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StyleCoachUiState(
    val profile: StyleProfileEntity? = null,
    val history: List<StyleProfileHistoryEntity> = emptyList(),
    val isTraining: Boolean = false,
    val trainSuccess: Boolean = false,
    val errorMessage: String? = null
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
            _uiState.value = _uiState.value.copy(isTraining = true, trainSuccess = false, errorMessage = null)
            try {
                styleAnalysisUseCase.analyzeAndSave(samples, "MANUAL_TRAINING")
                val historyList = styleProfileRepository.getHistory()
                val profile = styleProfileRepository.getProfileOnce()
                _uiState.value = _uiState.value.copy(
                    isTraining = false,
                    trainSuccess = true,
                    profile = profile,
                    history = historyList
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isTraining = false,
                    trainSuccess = false,
                    errorMessage = e.localizedMessage ?: "Failed to analyze style samples"
                )
            }
        }
    }
}
