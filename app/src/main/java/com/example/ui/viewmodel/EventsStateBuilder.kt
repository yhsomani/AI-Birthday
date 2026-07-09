package com.example.ui.viewmodel

import com.example.domain.event.EventConflictKind
import com.example.domain.event.EventResolutionPolicy
import com.example.domain.event.toOccasion
import com.example.domain.model.contact.ContactPickerItem
import com.example.domain.model.occasion.EventListItem
import com.example.domain.model.occasion.OccasionType

internal fun EventsUiState.withEvents(
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

internal fun EventsUiState.withFilteredEvents(): EventsUiState {
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

private fun EventConflictKind.toTrustConflictState(): EventTrustConflictState {
    return when (this) {
        EventConflictKind.NONE -> EventTrustConflictState.NONE
        EventConflictKind.DUPLICATE -> EventTrustConflictState.DUPLICATE
        EventConflictKind.DATE_CONFLICT -> EventTrustConflictState.DATE_CONFLICT
    }
}
