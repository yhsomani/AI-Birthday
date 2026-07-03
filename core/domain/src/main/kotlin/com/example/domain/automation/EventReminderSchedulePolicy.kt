package com.example.domain.automation

import com.example.domain.model.notification.EventReminderScheduleRequest

sealed interface EventReminderScheduleDecision {
    data object Cancel : EventReminderScheduleDecision

    data class Schedule(
        val triggerAtMs: Long,
        val exact: Boolean,
    ) : EventReminderScheduleDecision
}

object EventReminderSchedulePolicy {
    fun decide(
        request: EventReminderScheduleRequest,
        remindersEnabled: Boolean,
        canScheduleExactAlarm: Boolean,
        nowMs: Long,
    ): EventReminderScheduleDecision {
        if (!request.isActive || !remindersEnabled || request.nextOccurrenceMs < nowMs) {
            return EventReminderScheduleDecision.Cancel
        }

        return EventReminderScheduleDecision.Schedule(
            triggerAtMs = AutomationSchedulePolicy.reminderTimeMs(
                eventOccurrenceMs = request.nextOccurrenceMs,
                notifyDaysBefore = request.notifyDaysBefore,
                nowMs = nowMs,
            ),
            exact = canScheduleExactAlarm,
        )
    }
}
