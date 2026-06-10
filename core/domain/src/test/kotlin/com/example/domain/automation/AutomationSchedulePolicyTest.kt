package com.example.domain.automation

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationSchedulePolicyTest {
    @Test
    fun `messageSendTimeMs uses custom contact time on event date`() {
        val event = fixedMs(year = 2026, month = Calendar.JUNE, day = 20, hour = 0)

        val result = AutomationSchedulePolicy.messageSendTimeMs(
            eventOccurrenceMs = event,
            customHour = 14,
            customMinute = 30,
            quietHoursStart = 22,
            quietHoursEnd = 8,
            blackoutDatesJson = "[]",
            nowMs = fixedMs(year = 2026, month = Calendar.JUNE, day = 10, hour = 12),
        )

        assertEquals(fixedMs(year = 2026, month = Calendar.JUNE, day = 20, hour = 14, minute = 30), result)
    }

    @Test
    fun `messageSendTimeMs moves overnight quiet-hour sends to quiet end`() {
        val event = fixedMs(year = 2026, month = Calendar.JUNE, day = 20, hour = 0)

        val result = AutomationSchedulePolicy.messageSendTimeMs(
            eventOccurrenceMs = event,
            customHour = 23,
            customMinute = 15,
            quietHoursStart = 22,
            quietHoursEnd = 8,
            blackoutDatesJson = "[]",
            nowMs = fixedMs(year = 2026, month = Calendar.JUNE, day = 10, hour = 12),
        )

        assertEquals(fixedMs(year = 2026, month = Calendar.JUNE, day = 21, hour = 8), result)
    }

    @Test
    fun `messageSendTimeMs moves blackout date to next allowed day`() {
        val event = fixedMs(year = 2026, month = Calendar.JUNE, day = 20, hour = 0)

        val result = AutomationSchedulePolicy.messageSendTimeMs(
            eventOccurrenceMs = event,
            customHour = 9,
            customMinute = 0,
            quietHoursStart = 22,
            quietHoursEnd = 8,
            blackoutDatesJson = "[\"2026-06-20\"]",
            nowMs = fixedMs(year = 2026, month = Calendar.JUNE, day = 10, hour = 12),
        )

        assertEquals(fixedMs(year = 2026, month = Calendar.JUNE, day = 21, hour = 8), result)
    }

    @Test
    fun `nextAllowedSendMs defers current quiet-hour dispatch`() {
        val now = fixedMs(year = 2026, month = Calendar.JUNE, day = 20, hour = 23)

        val result = AutomationSchedulePolicy.nextAllowedSendMs(
            candidateMs = now,
            quietHoursStart = 22,
            quietHoursEnd = 8,
            blackoutDatesJson = "[]",
            nowMs = now,
        )

        assertEquals(fixedMs(year = 2026, month = Calendar.JUNE, day = 21, hour = 8), result)
    }

    @Test
    fun `reminderTimeMs uses notify days before at default reminder time`() {
        val event = fixedMs(year = 2026, month = Calendar.JUNE, day = 20, hour = 0)

        val result = AutomationSchedulePolicy.reminderTimeMs(
            eventOccurrenceMs = event,
            notifyDaysBefore = 2,
            nowMs = fixedMs(year = 2026, month = Calendar.JUNE, day = 10, hour = 12),
        )

        assertEquals(fixedMs(year = 2026, month = Calendar.JUNE, day = 18, hour = 9), result)
    }

    @Test
    fun `isChannelBlocked reads json token list`() {
        assertTrue(AutomationSchedulePolicy.isChannelBlocked("sms", "[\"SMS\",\"EMAIL\"]"))
    }

    private fun fixedMs(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int = 0,
    ): Long {
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
