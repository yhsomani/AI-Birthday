package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.db.entities.EventEntity
import com.example.domain.repository.EventRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EventsUiState(
    val events: List<EventEntity> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
)

@HiltViewModel
class EventsViewModel @Inject constructor(
    private val eventRepository: EventRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EventsUiState())
    val uiState: StateFlow<EventsUiState> = _uiState.asStateFlow()

    private var refreshJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            try {
                eventRepository.getAll().collect { events ->
                    _uiState.value = EventsUiState(
                        events = events,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("EventsViewModel", "Error collecting events", e)
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        _uiState.value = _uiState.value.copy(isRefreshing = true)
        refreshJob = viewModelScope.launch {
            try {
                eventRepository.getAll().first().let { events ->
                    _uiState.value = EventsUiState(
                        events = events,
                        isLoading = false,
                        isRefreshing = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isRefreshing = false)
            }
        }
    }
}
