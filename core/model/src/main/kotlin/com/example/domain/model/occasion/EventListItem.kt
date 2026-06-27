package com.example.domain.model.occasion

import com.example.domain.model.common.ContactId
import com.example.domain.model.common.OccasionId

data class EventListItem(
    val id: OccasionId,
    val contactId: ContactId,
    val type: OccasionType,
    val label: String?,
    val dayOfMonth: Int,
    val month: Int,
    val year: Int?,
    val nextOccurrenceMs: Long,
    val isActive: Boolean,
    val notifyDaysBefore: Int,
    val source: String,
    val confidenceScore: Int,
    val isVerified: Boolean,
) {
    val daysUntil: Int
        get() = ((nextOccurrenceMs - System.currentTimeMillis()).coerceAtLeast(0) / MILLIS_PER_DAY).toInt()

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
    }
}
