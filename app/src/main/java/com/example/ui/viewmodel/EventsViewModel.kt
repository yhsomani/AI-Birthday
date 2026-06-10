package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.db.entities.ActivityLogEntity
import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.EventEntity
import com.example.core.resilience.StructuredLogger
import com.example.domain.repository.ActivityLogRepository
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
import java.util.UUID
import javax.inject.Inject

enum class EventTypeFilter {
    ALL,
    BIRTHDAY,
    ANNIVERSARY,
    WORK,
    CUSTOM,
}

enum class EventHorizonFilter {
    ALL,
    NEXT_7_DAYS,
    NEXT_30_DAYS,
    NEXT_90_DAYS,
}

data class EventsUiState(
    val allEvents: List<EventEntity> = emptyList(),
    val events: List<EventEntity> = emptyList(),
    val contacts: List<ContactEntity> = emptyList(),
    val searchQuery: String = "",
    val selectedTypeFilter: EventTypeFilter = EventTypeFilter.ALL,
    val selectedHorizonFilter: EventHorizonFilter = EventHorizonFilter.ALL,
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
    private val activityLogRepository: ActivityLogRepository,
) : ViewModel() {
    private companion object {
        const val TAG = "EventsViewModel"
        const val LOAD_FAILED_MESSAGE = "Unable to load events. Please try again."
        const val REFRESH_FAILED_MESSAGE = "Unable to refresh events. Please try again."
    }

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
                    _uiState.value.withEvents(
                        allEvents = events,
                        contacts = contacts,
                        isLoading = false,
                        isRefreshing = false,
                    )
                }.collect { state ->
                    _uiState.value = state
                }
            } catch (e: Exception) {
                StructuredLogger.e(TAG, "Event collection failed", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = LOAD_FAILED_MESSAGE,
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
                is SaveManualEventUseCase.Outcome.Saved -> {
                    recordActivity(
                        ActivityLogEntity(
                            id = UUID.randomUUID().toString(),
                            type = "EVENT",
                            title = "Event saved",
                            detail = "${outcome.event.type} reminder saved.",
                            contactId = outcome.contact.id,
                            eventId = outcome.event.id,
                        )
                    )
                    _uiState.value.copy(
                        isSavingManualEvent = false,
                        saveMessage = "${outcome.event.type.replace("_", " ")} saved for ${outcome.contact.name}.",
                        error = null,
                    )
                }
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
                    _uiState.value = _uiState.value.withEvents(
                        allEvents = events,
                        contacts = _uiState.value.contacts,
                        isLoading = false,
                        isRefreshing = false,
                    ) 
                }
            } catch (e: Exception) {
                StructuredLogger.e(TAG, "Event refresh failed", e)
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    error = REFRESH_FAILED_MESSAGE,
                )
            }
        }
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(saveMessage = null, error = null)
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query).withFilteredEvents()
    }

    fun selectTypeFilter(filter: EventTypeFilter) {
        _uiState.value = _uiState.value.copy(selectedTypeFilter = filter).withFilteredEvents()
    }

    fun selectHorizonFilter(filter: EventHorizonFilter) {
        _uiState.value = _uiState.value.copy(selectedHorizonFilter = filter).withFilteredEvents()
    }

    private fun EventsUiState.withEvents(
        allEvents: List<EventEntity>,
        contacts: List<ContactEntity>,
        isLoading: Boolean = this.isLoading,
        isRefreshing: Boolean = this.isRefreshing,
    ): EventsUiState {
        return copy(
            allEvents = allEvents,
            contacts = contacts.sortedBy { it.name.lowercase() },
            isLoading = isLoading,
            isRefreshing = isRefreshing,
        ).withFilteredEvents()
    }

    private fun EventsUiState.withFilteredEvents(): EventsUiState {
        val contactMap = contacts.associateBy { it.id }
        val normalizedQuery = searchQuery.trim()
        val nowMs = System.currentTimeMillis()
        val horizonEndMs = selectedHorizonFilter.endMs(nowMs)
        val filtered = allEvents
            .asSequence()
            .filter { event ->
                normalizedQuery.isBlank() ||
                    event.type.contains(normalizedQuery, ignoreCase = true) ||
                    event.label?.contains(normalizedQuery, ignoreCase = true) == true ||
                    contactMap[event.contactId]?.name?.contains(normalizedQuery, ignoreCase = true) == true
            }
            .filter { event -> event.matchesTypeFilter(selectedTypeFilter) }
            .filter { event -> horizonEndMs == null || event.nextOccurrenceMs <= horizonEndMs }
            .sortedBy { it.nextOccurrenceMs }
            .toList()
        return copy(events = filtered)
    }

    private fun EventEntity.matchesTypeFilter(filter: EventTypeFilter): Boolean {
        return when (filter) {
            EventTypeFilter.ALL -> true
            EventTypeFilter.BIRTHDAY -> type == "BIRTHDAY"
            EventTypeFilter.ANNIVERSARY -> type == "ANNIVERSARY"
            EventTypeFilter.WORK -> type == "WORK_ANNIVERSARY"
            EventTypeFilter.CUSTOM -> type == "CUSTOM"
        }
    }

    private fun EventHorizonFilter.endMs(nowMs: Long): Long? {
        val days = when (this) {
            EventHorizonFilter.ALL -> return null
            EventHorizonFilter.NEXT_7_DAYS -> 7
            EventHorizonFilter.NEXT_30_DAYS -> 30
            EventHorizonFilter.NEXT_90_DAYS -> 90
        }
        return nowMs + days * 86_400_000L
    }

    private suspend fun recordActivity(entry: ActivityLogEntity) {
        try {
            activityLogRepository.record(entry)
        } catch (e: Exception) {
            StructuredLogger.w(TAG, "Activity log write failed", e, extras = mapOf("type" to entry.type))
        }
    }
}
