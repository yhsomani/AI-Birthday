package com.example.widget

import com.example.domain.model.common.ContactId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.contact.ContactHeader
import com.example.domain.model.occasion.Occasion
import com.example.domain.model.occasion.OccasionDate
import com.example.domain.model.occasion.OccasionType
import org.junit.Assert.assertEquals
import org.junit.Test

class BirthdayWidgetProviderTest {

    @Test
    fun `summary uses pure occasions for birthdays next events and pending approvals`() {
        val summary = buildBirthdayWidgetSummary(
            occasions = listOf(
                occasion(
                    id = "anniversary",
                    contactId = "contact_2",
                    type = OccasionType.ANNIVERSARY,
                    label = "Anniversary",
                    day = 27,
                    month = 6,
                    nextOccurrenceMs = 1_000L,
                ),
                occasion(
                    id = "tomorrow_birthday",
                    contactId = "contact_2",
                    type = OccasionType.BIRTHDAY,
                    label = "Birthday",
                    day = 28,
                    month = 6,
                    nextOccurrenceMs = 2_000L,
                ),
                occasion(
                    id = "today_birthday",
                    contactId = "contact_1",
                    type = OccasionType.BIRTHDAY,
                    label = "Birthday",
                    day = 27,
                    month = 6,
                    nextOccurrenceMs = 3_000L,
                ),
                occasion(
                    id = "inactive_event",
                    contactId = "contact_1",
                    type = OccasionType.CUSTOM,
                    label = "Dormant",
                    day = 27,
                    month = 6,
                    nextOccurrenceMs = 500L,
                    isActive = false,
                ),
            ),
            contacts = listOf(
                ContactHeader(id = ContactId("contact_1"), displayName = "Asha"),
                ContactHeader(id = ContactId("contact_2"), displayName = "Ben"),
            ),
            pendingApprovals = 2,
            todayDay = 27,
            todayMonth = 6,
            unknownContactLabel = "Unknown Contact",
        )

        assertEquals(1, summary.todayBirthdayCount)
        assertEquals(listOf("Asha"), summary.todayBirthdayNames)
        assertEquals(
            listOf("Ben: Anniversary", "Ben: Birthday", "Asha: Birthday"),
            summary.nextEvents,
        )
        assertEquals(2, summary.pendingApprovals)
    }

    @Test
    fun `summary falls back to unknown contact and occasion type label for next events`() {
        val summary = buildBirthdayWidgetSummary(
            occasions = listOf(
                occasion(
                    id = "work_anniversary",
                    contactId = "missing_contact",
                    type = OccasionType.WORK_ANNIVERSARY,
                    label = null,
                    day = 27,
                    month = 6,
                    nextOccurrenceMs = 1_000L,
                ),
            ),
            contacts = emptyList(),
            pendingApprovals = 0,
            todayDay = 27,
            todayMonth = 6,
            unknownContactLabel = "Unknown Contact",
        )

        assertEquals(0, summary.todayBirthdayCount)
        assertEquals(emptyList<String>(), summary.todayBirthdayNames)
        assertEquals(listOf("Unknown Contact: WORK ANNIVERSARY"), summary.nextEvents)
        assertEquals(0, summary.pendingApprovals)
    }

    private fun occasion(
        id: String,
        contactId: String,
        type: OccasionType,
        label: String?,
        day: Int,
        month: Int,
        nextOccurrenceMs: Long,
        isActive: Boolean = true,
    ): Occasion {
        return Occasion(
            id = OccasionId(id),
            contactId = ContactId(contactId),
            type = type,
            label = label,
            date = OccasionDate(dayOfMonth = day, month = month),
            nextOccurrenceMs = nextOccurrenceMs,
            isActive = isActive,
            notifyDaysBefore = 1,
            source = "TEST",
            confidenceScore = 100,
            isVerified = true,
        )
    }
}
