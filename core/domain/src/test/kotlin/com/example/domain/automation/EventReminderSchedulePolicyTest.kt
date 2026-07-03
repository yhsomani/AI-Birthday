package com.example.domain.automation

import com.example.domain.model.common.ContactId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.notification.EventReminderScheduleRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventReminderSchedulePolicyTest {
    @Test
    fun `inactive request cancels reminder`() {
        val decision = EventReminderSchedulePolicy.decide(
            request = request(isActive = false),
            remindersEnabled = true,
            canScheduleExactAlarm = true,
            nowMs = NOW_MS,
        )

        assertEquals(EventReminderScheduleDecision.Cancel, decision)
    }

    @Test
    fun `disabled reminders cancel reminder`() {
        val decision = EventReminderSchedulePolicy.decide(
            request = request(),
            remindersEnabled = false,
            canScheduleExactAlarm = true,
            nowMs = NOW_MS,
        )

        assertEquals(EventReminderScheduleDecision.Cancel, decision)
    }

    @Test
    fun `past occurrence cancels reminder`() {
        val decision = EventReminderSchedulePolicy.decide(
            request = request(nextOccurrenceMs = NOW_MS - 1L),
            remindersEnabled = true,
            canScheduleExactAlarm = true,
            nowMs = NOW_MS,
        )

        assertEquals(EventReminderScheduleDecision.Cancel, decision)
    }

    @Test
    fun `future occurrence schedules exact reminder when exact alarms are available`() {
        val decision = EventReminderSchedulePolicy.decide(
            request = request(),
            remindersEnabled = true,
            canScheduleExactAlarm = true,
            nowMs = NOW_MS,
        )

        assertTrue(decision is EventReminderScheduleDecision.Schedule)
        decision as EventReminderScheduleDecision.Schedule
        assertEquals(
            AutomationSchedulePolicy.reminderTimeMs(
                eventOccurrenceMs = OCCURRENCE_MS,
                notifyDaysBefore = 2,
                nowMs = NOW_MS,
            ),
            decision.triggerAtMs,
        )
        assertEquals(true, decision.exact)
    }

    @Test
    fun `future occurrence schedules inexact reminder when exact alarms are unavailable`() {
        val decision = EventReminderSchedulePolicy.decide(
            request = request(),
            remindersEnabled = true,
            canScheduleExactAlarm = false,
            nowMs = NOW_MS,
        )

        assertTrue(decision is EventReminderScheduleDecision.Schedule)
        decision as EventReminderScheduleDecision.Schedule
        assertEquals(false, decision.exact)
    }

    private fun request(
        nextOccurrenceMs: Long = OCCURRENCE_MS,
        isActive: Boolean = true,
    ): EventReminderScheduleRequest {
        return EventReminderScheduleRequest(
            eventId = OccasionId("event_1"),
            contactId = ContactId("contact_1"),
            nextOccurrenceMs = nextOccurrenceMs,
            notifyDaysBefore = 2,
            isActive = isActive,
        )
    }

    private companion object {
        const val NOW_MS = 1_800_000_000_000L
        const val OCCURRENCE_MS = NOW_MS + 7L * 24 * 60 * 60 * 1000
    }
}
