package com.example.domain.service

import com.example.domain.model.notification.EventReminderScheduleRequest

interface EventReminderSchedulerService {
    fun scheduleReminder(request: EventReminderScheduleRequest)
    fun cancelReminder(eventId: String)
    fun rescheduleAll()
}
