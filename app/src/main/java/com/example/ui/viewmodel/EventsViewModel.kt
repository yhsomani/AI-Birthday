package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.EventEntity
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.usecase.SaveManualEventUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EventsUiState(
    val events: List<EventEntity> = emptyList(),
    val contacts: List<ContactEntity> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isSavingManualEvent: Boolean = false,
    val saveMessage: String? = null,
    val error: String? = null,
)

@HiltViewModel
class EventsViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val contactRepository: ContactRepository,
    private val saveManualEventUseCase: SaveManualEventUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EventsUiState())
    val uiState: StateFlow<EventsUiState> = _uiState.asStateFlow()

    private var refreshJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            try {
                combine(
                    eventRepository.getAll(),
                    contactRepository.getAll(),
                ) { events, contacts ->
                    _uiState.value.copy(
                        events = events.sortedBy { it.nextOccurrenceMs },
                        contacts = contacts.sortedBy { it.name.lowercase() },
                        isLoading = false,
                        isRefreshing = false,
                    )
                }.collect { state ->
                    _uiState.value = state
                }
            } catch (e: Exception) {
                android.util.Log.e("EventsViewModel", "Error collecting events", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load events.",
                )
            }
        }
    }

    fun saveManualEvent(
        existingContactId: String?,
        newContactName: String?,
        eventType: String,
        label: String?,
        month: Int,
        day: Int,
        year: Int?,
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSavingManualEvent = true,
                saveMessage = null,
                error = null,
            )
            val outcome = saveManualEventUseCase(
                SaveManualEventUseCase.Request(
                    existingContactId = existingContactId,
                    newContactName = newContactName,
                    eventType = eventType,
                    label = label,
                    month = month,
                    dayOfMonth = day,
                    year = year,
                )
            )
            _uiState.value = when (outcome) {
                is SaveManualEventUseCase.Outcome.Saved -> _uiState.value.copy(
                    isSavingManualEvent = false,
                    saveMessage = "${outcome.event.type.replace("_", " ")} saved for ${outcome.contact.name}.",
                    error = null,
                )
                SaveManualEventUseCase.Outcome.ContactNotFound -> _uiState.value.copy(
                    isSavingManualEvent = false,
                    error = "Selected contact was not found.",
                )
                is SaveManualEventUseCase.Outcome.InvalidInput -> _uiState.value.copy(
                    isSavingManualEvent = false,
                    error = outcome.message,
                )
            }
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        _uiState.value = _uiState.value.copy(isRefreshing = true)
        refreshJob = viewModelScope.launch {
            try {
                eventRepository.getAll().first().let { events ->
                    _uiState.value = _uiState.value.copy(
                        events = events.sortedBy { it.nextOccurrenceMs },
                        isLoading = false,
                        isRefreshing = false,
                    ) 
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    error = e.message ?: "Failed to refresh events.",
                )
            }
        }
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(saveMessage = null, error = null)
    }
}
