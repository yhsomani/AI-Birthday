package com.example.domain.event

import com.example.domain.model.occasion.EventListItem
import com.example.domain.model.occasion.Occasion
import com.example.domain.model.occasion.OccasionDate

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
