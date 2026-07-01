package com.example.ui.viewmodel

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.core.resilience.StructuredLogger
import com.example.domain.event.EventConflictKind
import com.example.domain.event.EventResolutionPolicy
import com.example.domain.event.toOccasion
import com.example.domain.model.ActivityLogType
import com.example.domain.model.activity.ActivityLogRecord
import com.example.domain.model.contact.ContactPickerItem
import com.example.domain.model.occasion.EventListItem
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

enum class EventTypeFilter {
    ALL,
    BIRTHDAY,
    ANNIVERSARY,
    WORK,
    GRADUATION,
    HOLIDAY,
    REVIVAL,
    FOLLOW_UP,
    CUSTOM,
}

enum class EventHorizonFilter {
    ALL,
    NEXT_7_DAYS,
    NEXT_30_DAYS,
    NEXT_90_DAYS,
}

enum class ManualEventWarningKind {
    DUPLICATE,
    DATE_CONFLICT,
}

enum class EventVerificationState {
    VERIFIED,
    NEEDS_REVIEW,
    CONFLICT,
}

enum class EventTrustConflictState {
    NONE,
    DUPLICATE,
    DATE_CONFLICT,
}

enum class EventResolutionAction {
    MERGE_KEEP_SELECTED,
    KEEP_SEPARATE,
}

data class EventTrustState(
    val source: String,
    val verification: EventVerificationState,
    val confidenceScore: Int,
    val conflict: EventTrustConflictState = EventTrustConflictState.NONE,
)

data class ManualEventDuplicateWarning(
    val contactName: String,
    val eventType: String,
    val month: Int,
    val dayOfMonth: Int,
    val kind: ManualEventWarningKind = ManualEventWarningKind.DUPLICATE,
    val requestedMonth: Int? = null,
    val requestedDayOfMonth: Int? = null,
)

data class EventsUiState(
    val allEvents: List<EventListItem> = emptyList(),
    val events: List<EventListItem> = emptyList(),
    val contacts: List<ContactPickerItem> = emptyList(),
    val searchQuery: String = "",
    val selectedTypeFilter: EventTypeFilter = EventTypeFilter.ALL,
    val selectedHorizonFilter: EventHorizonFilter = EventHorizonFilter.ALL,
    val eventTrust: Map<String, EventTrustState> = emptyMap(),
    val resolvingEventId: String? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isSavingManualEvent: Boolean = false,
    val saveMessage: String? = null,
    val duplicateWarning: ManualEventDuplicateWarning? = null,
    val error: String? = null,
)

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

    private fun EventsUiState.withEvents(
        allEvents: List<EventListItem>,
        contacts: List<ContactPickerItem>,
        isLoading: Boolean = this.isLoading,
        isRefreshing: Boolean = this.isRefreshing,
    ): EventsUiState {
        return copy(
            allEvents = allEvents,
            contacts = contacts.sortedBy { it.displayName.lowercase() },
            eventTrust = buildEventTrustStates(allEvents),
            isLoading = isLoading,
            isRefreshing = isRefreshing,
        ).withFilteredEvents()
    }

    private fun EventsUiState.withFilteredEvents(): EventsUiState {
        val contactMap = contacts.associateBy { it.id.value }
        val normalizedQuery = searchQuery.trim()
        val nowMs = System.currentTimeMillis()
        val horizonEndMs = selectedHorizonFilter.endMs(nowMs)
        val filtered = allEvents
            .asSequence()
            .filter { event ->
                normalizedQuery.isBlank() ||
                    event.type.raw.contains(normalizedQuery, ignoreCase = true) ||
                    event.label?.contains(normalizedQuery, ignoreCase = true) == true ||
                    contactMap[event.contactId.value]?.displayName?.contains(normalizedQuery, ignoreCase = true) == true
            }
            .filter { event -> event.matchesTypeFilter(selectedTypeFilter) }
            .filter { event -> horizonEndMs == null || event.nextOccurrenceMs <= horizonEndMs }
            .sortedBy { it.nextOccurrenceMs }
            .toList()
        return copy(events = filtered)
    }

    private fun EventListItem.matchesTypeFilter(filter: EventTypeFilter): Boolean {
        return when (filter) {
            EventTypeFilter.ALL -> true
            EventTypeFilter.BIRTHDAY -> type == OccasionType.BIRTHDAY
            EventTypeFilter.ANNIVERSARY -> type == OccasionType.ANNIVERSARY
            EventTypeFilter.WORK -> type == OccasionType.WORK_ANNIVERSARY
            EventTypeFilter.GRADUATION -> type == OccasionType.GRADUATION
            EventTypeFilter.HOLIDAY -> type == OccasionType.HOLIDAY
            EventTypeFilter.REVIVAL -> type == OccasionType.REVIVAL
            EventTypeFilter.FOLLOW_UP -> type == OccasionType.FOLLOW_UP
            EventTypeFilter.CUSTOM -> type == OccasionType.CUSTOM
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

internal fun buildEventTrustStates(events: List<EventListItem>): Map<String, EventTrustState> {
    val conflicts = EventResolutionPolicy.conflictStates(events.map { it.toOccasion() })
    return events.associate { event ->
        val eventId = event.id.value
        val conflict = (conflicts[eventId] ?: if (EventResolutionPolicy.isSourceConflict(event.toOccasion())) {
            EventConflictKind.DATE_CONFLICT
        } else {
            EventConflictKind.NONE
        }).toTrustConflictState()
        val verification = when {
            conflict != EventTrustConflictState.NONE -> EventVerificationState.CONFLICT
            event.isVerified -> EventVerificationState.VERIFIED
            else -> EventVerificationState.NEEDS_REVIEW
        }

        eventId to EventTrustState(
            source = event.source,
            verification = verification,
            confidenceScore = event.confidenceScore.coerceIn(0, 100),
            conflict = conflict,
        )
    }
}

private fun EventConflictKind.toTrustConflictState(): EventTrustConflictState {
    return when (this) {
        EventConflictKind.NONE -> EventTrustConflictState.NONE
        EventConflictKind.DUPLICATE -> EventTrustConflictState.DUPLICATE
        EventConflictKind.DATE_CONFLICT -> EventTrustConflictState.DATE_CONFLICT
    }
}

private fun EventResolutionAction.toUseCaseAction(): ResolveEventConflictUseCase.Action {
    return when (this) {
        EventResolutionAction.MERGE_KEEP_SELECTED -> ResolveEventConflictUseCase.Action.MERGE_KEEP_SELECTED
        EventResolutionAction.KEEP_SEPARATE -> ResolveEventConflictUseCase.Action.KEEP_SEPARATE
    }
}
