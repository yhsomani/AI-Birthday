package com.example.domain.notification

import com.example.domain.model.common.ContactId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.contact.ContactHeader
import com.example.domain.model.occasion.Occasion
import com.example.domain.model.occasion.OccasionDate
import com.example.domain.model.occasion.OccasionType
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationMappersTest {
    @Test
    fun buildApprovalNotificationRequest_mapsPureContactAndOccasion() {
        val request = buildApprovalNotificationRequest(
            contact = ContactHeader(
                id = ContactId("contact_1"),
                displayName = "Asha",
            ),
            event = occasion(),
            messageId = "message_1",
        )

        assertEquals(ContactId("contact_1"), request.contactId)
        assertEquals("Asha", request.contactDisplayName)
        assertEquals(OccasionId("event_1"), request.eventId)
        assertEquals(MessageDraftId("message_1"), request.messageId)
    }

    @Test
    fun buildEventReminderNotificationRequest_mapsPureContactAndOccasion() {
        val request = buildEventReminderNotificationRequest(
            contact = ContactHeader(
                id = ContactId("contact_1"),
                displayName = "Asha",
            ),
            event = occasion(),
        )

        assertEquals(ContactId("contact_1"), request.contactId)
        assertEquals("Asha", request.contactDisplayName)
        assertEquals(OccasionId("event_1"), request.eventId)
        assertEquals(OccasionType.BIRTHDAY.raw, request.eventType)
    }

    @Test
    fun buildEventReminderScheduleRequest_mapsPureOccasion() {
        val request = buildEventReminderScheduleRequest(occasion())

        assertEquals(OccasionId("event_1"), request.eventId)
        assertEquals(ContactId("contact_1"), request.contactId)
        assertEquals(1_800_000_000_000L, request.nextOccurrenceMs)
        assertEquals(2, request.notifyDaysBefore)
        assertEquals(true, request.isActive)
    }

    private fun occasion(): Occasion {
        return Occasion(
            id = OccasionId("event_1"),
            contactId = ContactId("contact_1"),
            type = OccasionType.BIRTHDAY,
            label = "Birthday",
            date = OccasionDate(
                dayOfMonth = 12,
                month = 8,
            ),
            nextOccurrenceMs = 1_800_000_000_000L,
            isActive = true,
            notifyDaysBefore = 2,
            source = "CONTACTS",
            confidenceScore = 100,
            isVerified = true,
        )
    }
}
