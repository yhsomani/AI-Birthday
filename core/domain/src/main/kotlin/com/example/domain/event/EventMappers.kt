package com.example.domain.event

import com.example.core.db.entities.EventEntity
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.occasion.EventListItem
import com.example.domain.model.occasion.Occasion
import com.example.domain.model.occasion.OccasionDate
import com.example.domain.model.occasion.OccasionType
import com.example.domain.model.occasion.UpcomingEventPreview

fun EventEntity.toOccasion(): Occasion {
    return Occasion(
        id = OccasionId(id),
        contactId = ContactId(contactId),
        type = OccasionType.fromRaw(type),
        label = label,
        date = OccasionDate(
            dayOfMonth = dayOfMonth,
            month = month,
            year = year,
        ),
        nextOccurrenceMs = nextOccurrenceMs,
        isActive = isActive,
        notifyDaysBefore = notifyDaysBefore,
        source = source,
        confidenceScore = confidenceScore,
        isVerified = isVerified,
    )
}

fun Occasion.toEventEntity(): EventEntity {
    return EventEntity(
        id = id.value,
        contactId = contactId.value,
        type = type.raw,
        label = label,
        dayOfMonth = date.dayOfMonth,
        month = date.month,
        year = date.year,
        nextOccurrenceMs = nextOccurrenceMs,
        isActive = isActive,
        notifyDaysBefore = notifyDaysBefore,
        source = source,
        confidenceScore = confidenceScore,
        isVerified = isVerified,
    )
}

fun Iterable<EventEntity>.toOccasions(): List<Occasion> = map { it.toOccasion() }

fun EventListItem.toOccasion(): Occasion {
    return Occasion(
        id = id,
        contactId = contactId,
        type = type,
        label = label,
        date = OccasionDate(
            dayOfMonth = dayOfMonth,
            month = month,
            year = year,
        ),
        nextOccurrenceMs = nextOccurrenceMs,
        isActive = isActive,
        notifyDaysBefore = notifyDaysBefore,
        source = source,
        confidenceScore = confidenceScore,
        isVerified = isVerified,
    )
}

fun Occasion.toEventListItem(): EventListItem {
    return EventListItem(
        id = id,
        contactId = contactId,
        type = type,
        label = label,
        dayOfMonth = date.dayOfMonth,
        month = date.month,
        year = date.year,
        nextOccurrenceMs = nextOccurrenceMs,
        isActive = isActive,
        notifyDaysBefore = notifyDaysBefore,
        source = source,
        confidenceScore = confidenceScore,
        isVerified = isVerified,
    )
}

fun EventEntity.toEventListItem(): EventListItem {
    return EventListItem(
        id = OccasionId(id),
        contactId = ContactId(contactId),
        type = OccasionType.fromRaw(type),
        label = label,
        dayOfMonth = dayOfMonth,
        month = month,
        year = year,
        nextOccurrenceMs = nextOccurrenceMs,
        isActive = isActive,
        notifyDaysBefore = notifyDaysBefore,
        source = source,
        confidenceScore = confidenceScore,
        isVerified = isVerified,
    )
}

fun Iterable<EventEntity>.toEventListItems(): List<EventListItem> = map { it.toEventListItem() }

fun EventEntity.toUpcomingEventPreview(): UpcomingEventPreview {
    return UpcomingEventPreview(
        id = OccasionId(id),
        contactId = ContactId(contactId),
        type = OccasionType.fromRaw(type),
        label = label,
        nextOccurrenceMs = nextOccurrenceMs,
    )
}

fun Iterable<EventEntity>.toUpcomingEventPreviews(): List<UpcomingEventPreview> {
    return map { it.toUpcomingEventPreview() }
}
