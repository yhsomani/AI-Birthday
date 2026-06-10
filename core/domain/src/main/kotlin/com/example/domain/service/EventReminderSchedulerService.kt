package com.example.domain.service

import com.example.core.db.entities.EventEntity

interface EventReminderSchedulerService {
    fun scheduleReminder(event: EventEntity)
    fun cancelReminder(eventId: String)
    fun rescheduleAll()
}
