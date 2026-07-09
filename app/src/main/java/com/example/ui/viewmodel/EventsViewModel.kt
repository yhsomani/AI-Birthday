package com.example.ui.viewmodel

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.core.resilience.StructuredLogger
import com.example.domain.model.ActivityLogType
import com.example.domain.model.activity.ActivityLogRecord
import com.example.domain.model.occasion.OccasionType
import com.example.domain.repository.ActivityLogRepository
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.usecase.ResolveEventConflictUseCase
import com.example.domain.usecase.SaveManualEventUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class EventsViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val eventRepository: EventRepository,
    private val contactRepository: ContactRepository,
    private val saveManualEventUseCase: SaveManualEventUseCase,
    private val resolveEventConflictUseCase: ResolveEventConflictUseCase,
    private val activityLogRepository: ActivityLogRepository,
) : ViewModel() {
    private companion object {
        const val TAG = "EventsViewModel"
    }

    private val _uiState = MutableStateFlow(EventsUiState())
    val uiState: StateFlow<EventsUiState> = _uiState.asStateFlow()

    private var refreshJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            try {
                combine(
                    eventRepository.getEventListItems(),
                    contactRepository.getContactPickerItems(),
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
                    error = string(R.string.events_error_load),
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
        allowDuplicate: Boolean = false,
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSavingManualEvent = true,
                saveMessage = null,
                duplicateWarning = null,
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
                    allowDuplicate = allowDuplicate,
                )
            )
            _uiState.value = when (outcome) {
                is SaveManualEventUseCase.Outcome.Saved -> {
                    val eventTypeLabel = eventTypeLabel(outcome.event.type.raw)
                    recordActivity(
                        ActivityLogRecord(
                            id = UUID.randomUUID().toString(),
                            type = ActivityLogType.EVENT.raw,
                            title = string(R.string.events_saved_activity_title),
                            detail = string(R.string.events_saved_activity_detail, eventTypeLabel),
                            contactId = outcome.contact.id.value,
                            eventId = outcome.event.id.value,
                        )
                    )
                    _uiState.value.copy(
                        isSavingManualEvent = false,
                        saveMessage = string(R.string.events_saved_message, eventTypeLabel, outcome.contact.displayName),
                        error = null,
                    )
                }
                is SaveManualEventUseCase.Outcome.DuplicateFound -> _uiState.value.copy(
                    isSavingManualEvent = false,
                    duplicateWarning = ManualEventDuplicateWarning(
                        contactName = outcome.contact.displayName,
                        eventType = outcome.existingEvent.type.raw,
                        month = outcome.existingEvent.month,
                        dayOfMonth = outcome.existingEvent.dayOfMonth,
                    ),
                    error = null,
                )
                is SaveManualEventUseCase.Outcome.ConflictFound -> _uiState.value.copy(
                    isSavingManualEvent = false,
                    duplicateWarning = ManualEventDuplicateWarning(
                        contactName = outcome.contact.displayName,
                        eventType = outcome.existingEvent.type.raw,
                        month = outcome.existingEvent.month,
                        dayOfMonth = outcome.existingEvent.dayOfMonth,
                        kind = ManualEventWarningKind.DATE_CONFLICT,
                        requestedMonth = outcome.requestedMonth,
                        requestedDayOfMonth = outcome.requestedDayOfMonth,
                    ),
                    error = null,
                )
                SaveManualEventUseCase.Outcome.ContactNotFound -> _uiState.value.copy(
                    isSavingManualEvent = false,
                    error = string(R.string.events_error_selected_contact_not_found),
                )
                is SaveManualEventUseCase.Outcome.InvalidInput -> _uiState.value.copy(
                    isSavingManualEvent = false,
                    error = outcome.reason.message(),
                )
            }
        }
    }

    fun clearManualEventDuplicateWarning() {
        _uiState.value = _uiState.value.copy(duplicateWarning = null)
    }

    fun refresh() {
        refreshJob?.cancel()
        _uiState.value = _uiState.value.copy(isRefreshing = true)
        refreshJob = viewModelScope.launch {
            try {
                eventRepository.getEventListItems().first().let { events ->
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
                    error = string(R.string.events_error_refresh),
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

    fun resolveEventConflict(eventId: String, action: EventResolutionAction) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                resolvingEventId = eventId,
                saveMessage = null,
                error = null,
            )
            try {
                val outcome = resolveEventConflictUseCase(
                    ResolveEventConflictUseCase.Request(
                        eventId = eventId,
                        action = action.toUseCaseAction(),
                    )
                )
                _uiState.value = when (outcome) {
                    is ResolveEventConflictUseCase.Outcome.Resolved -> {
                        recordEventResolution(outcome)
                        _uiState.value.copy(
                            resolvingEventId = null,
                            saveMessage = when (action) {
                                EventResolutionAction.MERGE_KEEP_SELECTED -> {
                                    string(R.string.event_resolution_merged_message)
                                }
                                EventResolutionAction.KEEP_SEPARATE -> {
                                    string(R.string.event_resolution_keep_separate_message)
                                }
                            },
                        )
                    }
                    ResolveEventConflictUseCase.Outcome.EventNotFound -> _uiState.value.copy(
                        resolvingEventId = null,
                        error = string(R.string.events_error_event_not_found),
                    )
                    is ResolveEventConflictUseCase.Outcome.NoConflict -> _uiState.value.copy(
                        resolvingEventId = null,
                        saveMessage = string(R.string.event_resolution_no_conflict_message),
                    )
                }
            } catch (e: Exception) {
                StructuredLogger.e(TAG, "Event conflict resolution failed", e)
                _uiState.value = _uiState.value.copy(
                    resolvingEventId = null,
                    error = string(R.string.events_error_resolution),
                )
            }
        }
    }

    private suspend fun recordActivity(entry: ActivityLogRecord) {
        try {
            activityLogRepository.record(entry)
        } catch (e: Exception) {
            StructuredLogger.w(TAG, "Activity log write failed", e, extras = mapOf("type" to entry.type))
        }
    }

    private suspend fun recordEventResolution(outcome: ResolveEventConflictUseCase.Outcome.Resolved) {
        recordActivity(
            ActivityLogRecord(
                id = UUID.randomUUID().toString(),
                type = ActivityLogType.EVENT.raw,
                title = when (outcome.action) {
                    ResolveEventConflictUseCase.Action.MERGE_KEEP_SELECTED -> {
                        string(R.string.event_resolution_merged_activity_title)
                    }
                    ResolveEventConflictUseCase.Action.KEEP_SEPARATE -> {
                        string(R.string.event_resolution_keep_separate_activity_title)
                    }
                },
                detail = string(R.string.event_resolution_activity_detail, outcome.affectedEventIds.size),
                contactId = outcome.keptEvent.contactId.value,
                eventId = outcome.keptEvent.id.value,
            )
        )
    }

    private fun SaveManualEventUseCase.InvalidInputReason.message(): String {
        return when (this) {
            SaveManualEventUseCase.InvalidInputReason.MISSING_CONTACT -> string(R.string.events_error_missing_contact)
            SaveManualEventUseCase.InvalidInputReason.INVALID_DATE -> string(R.string.events_error_invalid_date)
            SaveManualEventUseCase.InvalidInputReason.UNSUPPORTED_EVENT_TYPE -> string(
                R.string.events_error_unsupported_event_type,
            )
        }
    }

    private fun eventTypeLabel(rawType: String): String {
        return when (OccasionType.fromRaw(rawType)) {
            OccasionType.BIRTHDAY -> string(R.string.event_type_birthday)
            OccasionType.ANNIVERSARY -> string(R.string.event_type_anniversary)
            OccasionType.WORK_ANNIVERSARY -> string(R.string.event_type_work_anniversary)
            OccasionType.GRADUATION -> string(R.string.event_type_graduation)
            OccasionType.HOLIDAY -> string(R.string.event_type_holiday)
            OccasionType.REVIVAL -> string(R.string.event_type_revival)
            OccasionType.FOLLOW_UP -> string(R.string.event_type_follow_up)
            OccasionType.CUSTOM -> string(R.string.event_type_custom)
            else -> rawType.replace("_", " ").lowercase().replaceFirstChar { it.titlecase() }
        }
    }

    private fun string(@StringRes resId: Int, vararg args: Any): String {
        return appContext.getString(resId, *args)
    }
}

private fun EventResolutionAction.toUseCaseAction(): ResolveEventConflictUseCase.Action {
    return when (this) {
        EventResolutionAction.MERGE_KEEP_SELECTED -> ResolveEventConflictUseCase.Action.MERGE_KEEP_SELECTED
        EventResolutionAction.KEEP_SEPARATE -> ResolveEventConflictUseCase.Action.KEEP_SEPARATE
    }
}
