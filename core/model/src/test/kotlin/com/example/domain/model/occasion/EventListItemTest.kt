package com.example.domain.model.occasion

import com.example.domain.model.common.ContactId
import com.example.domain.model.common.OccasionId
import org.junit.Assert.assertEquals
import org.junit.Test

class EventListItemTest {

    @Test
    fun daysUntilUsesProvidedNowAndClampsPastEvents() {
        val nowMs = 1_000_000L
        val event = event(nextOccurrenceMs = nowMs + 4L * DayMs + OneHourMs)
        val pastEvent = event(nextOccurrenceMs = nowMs - DayMs)

        assertEquals(4, event.daysUntil(nowMs))
        assertEquals(0, pastEvent.daysUntil(nowMs))
    }

    private fun event(nextOccurrenceMs: Long): EventListItem {
        return EventListItem(
            id = OccasionId("event"),
            contactId = ContactId("contact"),
            type = OccasionType.BIRTHDAY,
            label = "Birthday",
            dayOfMonth = 1,
            month = 1,
            year = null,
            nextOccurrenceMs = nextOccurrenceMs,
            isActive = true,
            notifyDaysBefore = 1,
            source = "MANUAL",
            confidenceScore = 100,
            isVerified = true,
        )
    }

    private companion object {
        const val DayMs = 86_400_000L
        const val OneHourMs = 3_600_000L
    }
}
