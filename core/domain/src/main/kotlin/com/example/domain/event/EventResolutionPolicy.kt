package com.example.domain.event

import com.example.core.db.entities.EventEntity
import com.example.domain.model.EventType
import java.util.Locale

enum class EventConflictKind {
    NONE,
    DUPLICATE,
    DATE_CONFLICT,
}

object EventResolutionPolicy {
    private const val KEEP_SEPARATE_SOURCE_SUFFIX = "_SEPARATE"

    fun conflictStates(events: List<EventEntity>): Map<String, EventConflictKind> {
        val states = mutableMapOf<String, EventConflictKind>()
        events
            .filter { it.isActive && !isMarkedKeepSeparate(it) }
            .groupBy { it.conflictGroupKey() }
            .values
            .forEach { groupedEvents ->
                val dates = groupedEvents.map { EventConflictDate(it.month, it.dayOfMonth) }.toSet()
                val conflict = when {
                    dates.size > 1 -> EventConflictKind.DATE_CONFLICT
                    groupedEvents.size > 1 -> EventConflictKind.DUPLICATE
                    else -> EventConflictKind.NONE
                }
                if (conflict != EventConflictKind.NONE) {
                    groupedEvents.forEach { event ->
                        states[event.id] = conflict
                    }
                }
            }
        return states
    }

    fun conflictGroupFor(events: List<EventEntity>, selectedEvent: EventEntity): List<EventEntity> {
        val selectedKey = selectedEvent.conflictGroupKey()
        return events.filter { event ->
            event.isActive && event.conflictGroupKey() == selectedKey
        }
    }

    fun baseSource(source: String): String {
        val normalized = source.trim().uppercase(Locale.US)
        return if (normalized.endsWith(KEEP_SEPARATE_SOURCE_SUFFIX)) {
            normalized.removeSuffix(KEEP_SEPARATE_SOURCE_SUFFIX)
        } else {
            source.trim()
        }
    }

    fun keepSeparateSource(source: String): String {
        val base = baseSource(source).ifBlank { "UNKNOWN" }.uppercase(Locale.US)
        return "$base$KEEP_SEPARATE_SOURCE_SUFFIX"
    }

    fun isMarkedKeepSeparate(event: EventEntity): Boolean {
        return event.source.trim().uppercase(Locale.US).endsWith(KEEP_SEPARATE_SOURCE_SUFFIX)
    }

    fun isSourceConflict(event: EventEntity): Boolean {
        return !isMarkedKeepSeparate(event) && baseSource(event.source).equals("CONFLICT", ignoreCase = true)
    }

    private data class EventConflictDate(
        val month: Int,
        val dayOfMonth: Int,
    )

    private fun EventEntity.conflictGroupKey(): String {
        val normalizedType = type.trim().uppercase(Locale.US)
        val normalizedLabel = if (EventType.fromRaw(normalizedType) == EventType.CUSTOM) {
            label.orEmpty().trim().lowercase(Locale.US)
        } else {
            ""
        }
        return listOf(contactId, normalizedType, normalizedLabel).joinToString(separator = "|")
    }
}
