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
import java.util.UUID

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


fun addQuickBirthday(name: String, month: Int, day: Int) {
        viewModelScope.launch {
            val dummyId = "quick_add_" + UUID.randomUUID().toString().substring(0, 8)
            val newEvent = EventEntity(
                id = UUID.randomUUID().toString(),
                contactId = dummyId, // Mock contact for standalone event
                type = "BIRTHDAY",
                month = month,
                dayOfMonth = day,
                year = null,
                nextOccurrenceMs = System.currentTimeMillis() + 86400000L, // Mock next occurrence
                source = "MANUAL",
                confidenceScore = 100
            )
            // Save to repo in real implementation
            // eventRepository.insertEvent(newEvent)

            // For now, let's refresh to simulate success
            refresh()
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
