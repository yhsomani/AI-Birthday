package com.example.domain.model.occasion

import com.example.domain.model.common.ContactId
import com.example.domain.model.common.OccasionId

data class UpcomingEventPreview(
    val id: OccasionId,
    val contactId: ContactId,
    val type: OccasionType,
    val label: String?,
    val nextOccurrenceMs: Long,
) {
    val daysUntil: Int
        get() = ((nextOccurrenceMs - System.currentTimeMillis()).coerceAtLeast(0L) / DAY_MS).toInt()

    private companion object {
        const val DAY_MS = 86_400_000L
    }
}
