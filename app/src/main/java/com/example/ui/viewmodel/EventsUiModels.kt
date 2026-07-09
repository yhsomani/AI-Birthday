package com.example.ui.viewmodel

import com.example.domain.model.contact.ContactPickerItem
import com.example.domain.model.occasion.EventListItem

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
