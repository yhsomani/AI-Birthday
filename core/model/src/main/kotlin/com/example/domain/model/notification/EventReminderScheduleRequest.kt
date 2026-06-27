package com.example.domain.model.notification

import com.example.domain.model.common.ContactId
import com.example.domain.model.common.OccasionId

data class EventReminderScheduleRequest(
    val eventId: OccasionId,
    val contactId: ContactId,
    val nextOccurrenceMs: Long,
    val notifyDaysBefore: Int,
    val isActive: Boolean,
)
