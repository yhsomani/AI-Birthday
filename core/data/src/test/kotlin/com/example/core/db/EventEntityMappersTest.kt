package com.example.core.db

import com.example.core.db.entities.EventEntity
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.occasion.Occasion
import com.example.domain.model.occasion.OccasionDate
import com.example.domain.model.occasion.OccasionType
import org.junit.Assert.assertEquals
import org.junit.Test

class EventEntityMappersTest {
    @Test
    fun eventEntityMapsToOccasion() {
        val entity = EventEntity(
            id = "event_1",
            contactId = "contact_1",
            type = "anniversary",
            label = "Wedding",
            dayOfMonth = 12,
            month = 8,
            year = 2020,
            nextOccurrenceMs = 1_800_000_000_000,
            isActive = true,
            notifyDaysBefore = 3,
            source = "DEVICE",
            confidenceScore = 82,
            isVerified = false,
        )

        val occasion = entity.toOccasion()

        assertEquals(OccasionId("event_1"), occasion.id)
        assertEquals(ContactId("contact_1"), occasion.contactId)
        assertEquals(OccasionType.ANNIVERSARY, occasion.type)
        assertEquals("Wedding", occasion.label)
        assertEquals(OccasionDate(dayOfMonth = 12, month = 8, year = 2020), occasion.date)
        assertEquals(1_800_000_000_000, occasion.nextOccurrenceMs)
        assertEquals(true, occasion.isActive)
        assertEquals(3, occasion.notifyDaysBefore)
        assertEquals("DEVICE", occasion.source)
        assertEquals(82, occasion.confidenceScore)
        assertEquals(false, occasion.isVerified)
    }

    @Test
    fun occasionMapsToEventEntity() {
        val occasion = Occasion(
            id = OccasionId("event_1"),
            contactId = ContactId("contact_1"),
            type = OccasionType.WORK_ANNIVERSARY,
            label = "Work start",
            date = OccasionDate(dayOfMonth = 4, month = 5, year = null),
            nextOccurrenceMs = 1_900_000_000_000,
            isActive = true,
            notifyDaysBefore = 7,
            source = "MANUAL",
            confidenceScore = 100,
            isVerified = true,
        )

        val entity = occasion.toEventEntity()

        assertEquals("event_1", entity.id)
        assertEquals("contact_1", entity.contactId)
        assertEquals(OccasionType.WORK_ANNIVERSARY.raw, entity.type)
        assertEquals("Work start", entity.label)
        assertEquals(4, entity.dayOfMonth)
        assertEquals(5, entity.month)
        assertEquals(null, entity.year)
        assertEquals(1_900_000_000_000, entity.nextOccurrenceMs)
        assertEquals(true, entity.isActive)
        assertEquals(7, entity.notifyDaysBefore)
        assertEquals("MANUAL", entity.source)
        assertEquals(100, entity.confidenceScore)
        assertEquals(true, entity.isVerified)
    }
}
