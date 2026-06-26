package com.example.domain.event

import com.example.core.db.entities.EventEntity
import com.example.domain.model.EventType
import java.util.Locale

object EventIdentityPolicy {
    fun canonicalId(contactId: String, eventType: String): String? {
        return when (EventType.fromRaw(eventType)) {
            EventType.BIRTHDAY -> "${contactId}_birthday"
            EventType.ANNIVERSARY -> "${contactId}_anniversary"
            EventType.WORK_ANNIVERSARY -> "${contactId}_work_anniversary"
            else -> null
        }
    }

    fun hasMatchingActiveEvent(
        events: List<EventEntity>,
        contactId: String,
        eventType: String,
        month: Int,
        dayOfMonth: Int,
        label: String? = null,
    ): Boolean = findMatchingActiveEvent(
        events = events,
        contactId = contactId,
        eventType = eventType,
        month = month,
        dayOfMonth = dayOfMonth,
        label = label,
    ) != null

    fun findMatchingActiveEvent(
        events: List<EventEntity>,
        contactId: String,
        eventType: String,
        month: Int,
        dayOfMonth: Int,
        label: String? = null,
    ): EventEntity? {
        val normalizedType = eventType.normalizedEventType()
        return events.firstOrNull { event ->
            event.isActive &&
                event.contactId == contactId &&
                event.type.normalizedEventType() == normalizedType &&
                event.month == month &&
                event.dayOfMonth == dayOfMonth &&
                labelsAreCompatibleForDuplicate(normalizedType, event.label, label)
        }
    }

    fun findConflictingActiveEvent(
        events: List<EventEntity>,
        contactId: String,
        eventType: String,
        month: Int,
        dayOfMonth: Int,
        label: String? = null,
    ): EventEntity? {
        val normalizedType = eventType.normalizedEventType()
        return events.firstOrNull { event ->
            event.isActive &&
                event.contactId == contactId &&
                event.type.normalizedEventType() == normalizedType &&
                (event.month != month || event.dayOfMonth != dayOfMonth) &&
                labelsAreCompatibleForDuplicate(normalizedType, event.label, label)
        }
    }

    private fun labelsAreCompatibleForDuplicate(
        eventType: String,
        existingLabel: String?,
        newLabel: String?,
    ): Boolean {
        if (EventType.fromRaw(eventType) != EventType.CUSTOM) return true
        val normalizedExisting = existingLabel.orEmpty().trim().lowercase(Locale.US)
        val normalizedNew = newLabel.orEmpty().trim().lowercase(Locale.US)
        return normalizedExisting.isBlank() ||
            normalizedNew.isBlank() ||
            normalizedExisting == normalizedNew
    }

    private fun String.normalizedEventType(): String = trim().uppercase(Locale.US)
}
