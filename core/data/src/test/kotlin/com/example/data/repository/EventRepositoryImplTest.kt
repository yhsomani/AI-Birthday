package com.example.data.repository

import com.example.core.db.dao.EventDao
import com.example.core.db.entities.EventEntity
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.occasion.Occasion
import com.example.domain.model.occasion.OccasionDate
import com.example.domain.model.occasion.OccasionType
import io.mockk.every
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class EventRepositoryImplTest {
    private val eventDao: EventDao = mockk(relaxed = true)
    private val repository = EventRepositoryImpl(eventDao)

    @Test
    fun getEventListItems_mapsRoomEventsToPureListItems() = runTest {
        every { eventDao.getAll() } returns flowOf(
            listOf(
                EventEntity(
                    id = "event_1",
                    contactId = "contact_1",
                    type = "work_anniversary",
                    label = "Work milestone",
                    dayOfMonth = 1,
                    month = 6,
                    year = 2020,
                    nextOccurrenceMs = 1_800_000_000_000L,
                    isActive = true,
                    notifyDaysBefore = 3,
                    source = "MANUAL",
                    confidenceScore = 82,
                    isVerified = false,
                ),
            ),
        )

        val items = repository.getEventListItems().first()

        assertEquals(1, items.size)
        assertEquals(OccasionId("event_1"), items.single().id)
        assertEquals(ContactId("contact_1"), items.single().contactId)
        assertEquals(OccasionType.WORK_ANNIVERSARY, items.single().type)
        assertEquals("Work milestone", items.single().label)
        assertEquals(1, items.single().dayOfMonth)
        assertEquals(6, items.single().month)
        assertEquals(2020, items.single().year)
        assertEquals(1_800_000_000_000L, items.single().nextOccurrenceMs)
        assertEquals(true, items.single().isActive)
        assertEquals(3, items.single().notifyDaysBefore)
        assertEquals("MANUAL", items.single().source)
        assertEquals(82, items.single().confidenceScore)
        assertEquals(false, items.single().isVerified)
    }

    @Test
    fun getOccasions_mapsRoomEventsToPureOccasions() = runTest {
        every { eventDao.getAll() } returns flowOf(
            listOf(
                EventEntity(
                    id = "event_1",
                    contactId = "contact_1",
                    type = "anniversary",
                    label = "Anniversary",
                    dayOfMonth = 2,
                    month = 7,
                    year = 2016,
                    nextOccurrenceMs = 1_800_000_000_000L,
                    isActive = true,
                    notifyDaysBefore = 7,
                    source = "MANUAL",
                    confidenceScore = 91,
                    isVerified = false,
                ),
            ),
        )

        val occasions = repository.getOccasions().first()

        assertEquals(1, occasions.size)
        assertEquals(OccasionId("event_1"), occasions.single().id)
        assertEquals(ContactId("contact_1"), occasions.single().contactId)
        assertEquals(OccasionType.ANNIVERSARY, occasions.single().type)
        assertEquals("Anniversary", occasions.single().label)
        assertEquals(2, occasions.single().date.dayOfMonth)
        assertEquals(7, occasions.single().date.month)
        assertEquals(2016, occasions.single().date.year)
        assertEquals(1_800_000_000_000L, occasions.single().nextOccurrenceMs)
        assertEquals(true, occasions.single().isActive)
        assertEquals(7, occasions.single().notifyDaysBefore)
        assertEquals("MANUAL", occasions.single().source)
        assertEquals(91, occasions.single().confidenceScore)
        assertEquals(false, occasions.single().isVerified)
    }

    @Test
    fun getOccasionById_mapsRoomEventToPureOccasion() = runTest {
        coEvery { eventDao.getById("event_1") } returns EventEntity(
            id = "event_1",
            contactId = "contact_1",
            type = "birthday",
            label = "Birthday",
            dayOfMonth = 12,
            month = 8,
            year = 1995,
            nextOccurrenceMs = 1_800_000_000_000L,
            isActive = true,
            notifyDaysBefore = 2,
            source = "CONTACTS",
            confidenceScore = 95,
            isVerified = true,
        )

        val occasion = repository.getOccasionById("event_1")

        assertEquals(OccasionId("event_1"), occasion?.id)
        assertEquals(ContactId("contact_1"), occasion?.contactId)
        assertEquals(OccasionType.BIRTHDAY, occasion?.type)
        assertEquals("Birthday", occasion?.label)
        assertEquals(12, occasion?.date?.dayOfMonth)
        assertEquals(8, occasion?.date?.month)
        assertEquals(1995, occasion?.date?.year)
        assertEquals(1_800_000_000_000L, occasion?.nextOccurrenceMs)
        assertEquals(2, occasion?.notifyDaysBefore)
        assertEquals("CONTACTS", occasion?.source)
        assertEquals(95, occasion?.confidenceScore)
        assertEquals(true, occasion?.isVerified)
        coVerify { eventDao.getById("event_1") }
    }

    @Test
    fun countUpcoming_delegatesToDaoWithCurrentTimeWindow() = runTest {
        coEvery { eventDao.countUpcoming(30, any()) } returns 3

        val count = repository.countUpcoming(30)

        assertEquals(3, count)
        coVerify { eventDao.countUpcoming(30, any()) }
    }

    @Test
    fun getOccasionsBefore_mapsDaoEventsBeforeCutoffToPureOccasions() = runTest {
        coEvery { eventDao.getEventsBefore(1_800_000_000_000L) } returns listOf(
            EventEntity(
                id = "event_1",
                contactId = "contact_1",
                type = "birthday",
                label = "Birthday",
                dayOfMonth = 12,
                month = 8,
                nextOccurrenceMs = 1_799_999_999_000L,
            ),
        )

        val occasions = repository.getOccasionsBefore(1_800_000_000_000L)

        assertEquals(1, occasions.size)
        assertEquals(OccasionId("event_1"), occasions.single().id)
        assertEquals(ContactId("contact_1"), occasions.single().contactId)
        assertEquals(OccasionType.BIRTHDAY, occasions.single().type)
        assertEquals("Birthday", occasions.single().label)
        assertEquals(1_799_999_999_000L, occasions.single().nextOccurrenceMs)
        coVerify { eventDao.getEventsBefore(1_800_000_000_000L) }
    }

    @Test
    fun getOccasionTypeById_mapsDaoRawTypeToPureOccasionType() = runTest {
        coEvery { eventDao.getTypeById("event_1") } returns "work_anniversary"

        val type = repository.getOccasionTypeById("event_1")

        assertEquals(OccasionType.WORK_ANNIVERSARY, type)
        coVerify { eventDao.getTypeById("event_1") }
    }

    @Test
    fun getUpcomingPreviews_mapsRoomEventsToPurePreviewModel() = runTest {
        coEvery { eventDao.getUpcoming(30, any()) } returns listOf(
            EventEntity(
                id = "event_1",
                contactId = "contact_1",
                type = "birthday",
                label = "Asha's Birthday",
                dayOfMonth = 1,
                month = 6,
                nextOccurrenceMs = 1_800_000_000_000L,
            ),
        )

        val previews = repository.getUpcomingPreviews(30)

        assertEquals(1, previews.size)
        assertEquals(OccasionId("event_1"), previews.single().id)
        assertEquals(ContactId("contact_1"), previews.single().contactId)
        assertEquals(OccasionType.BIRTHDAY, previews.single().type)
        assertEquals("Asha's Birthday", previews.single().label)
        assertEquals(1_800_000_000_000L, previews.single().nextOccurrenceMs)
    }

    @Test
    fun getNextUpcomingPreviewForContact_mapsDaoResultToPurePreviewModel() = runTest {
        coEvery { eventDao.getNextUpcomingForContact("contact_1", 365, any()) } returns EventEntity(
            id = "event_1",
            contactId = "contact_1",
            type = "anniversary",
            label = "Anniversary",
            dayOfMonth = 1,
            month = 6,
            nextOccurrenceMs = 1_800_000_000_000L,
        )

        val preview = repository.getNextUpcomingPreviewForContact("contact_1", 365)

        assertEquals(OccasionId("event_1"), preview?.id)
        assertEquals(ContactId("contact_1"), preview?.contactId)
        assertEquals(OccasionType.ANNIVERSARY, preview?.type)
        assertEquals("Anniversary", preview?.label)
        assertEquals(1_800_000_000_000L, preview?.nextOccurrenceMs)
        coVerify { eventDao.getNextUpcomingForContact("contact_1", 365, any()) }
    }

    @Test
    fun upsertOccasion_mapsPureOccasionToRoomEvent() = runTest {
        val eventSlot = slot<EventEntity>()
        val occasion = Occasion(
            id = OccasionId("event_1"),
            contactId = ContactId("contact_1"),
            type = OccasionType.WORK_ANNIVERSARY,
            label = "Work milestone",
            date = OccasionDate(
                dayOfMonth = 1,
                month = 6,
                year = 2020,
            ),
            nextOccurrenceMs = 1_800_000_000_000L,
            isActive = true,
            notifyDaysBefore = 3,
            source = "CONTACTS",
            confidenceScore = 88,
            isVerified = false,
        )

        repository.upsertOccasion(occasion)

        coVerify { eventDao.upsert(capture(eventSlot)) }
        assertEquals("event_1", eventSlot.captured.id)
        assertEquals("contact_1", eventSlot.captured.contactId)
        assertEquals("WORK_ANNIVERSARY", eventSlot.captured.type)
        assertEquals("Work milestone", eventSlot.captured.label)
        assertEquals(1, eventSlot.captured.dayOfMonth)
        assertEquals(6, eventSlot.captured.month)
        assertEquals(2020, eventSlot.captured.year)
        assertEquals(1_800_000_000_000L, eventSlot.captured.nextOccurrenceMs)
        assertEquals(true, eventSlot.captured.isActive)
        assertEquals(3, eventSlot.captured.notifyDaysBefore)
        assertEquals("CONTACTS", eventSlot.captured.source)
        assertEquals(88, eventSlot.captured.confidenceScore)
        assertEquals(false, eventSlot.captured.isVerified)
    }

    @Test
    fun deactivateContactDerivedOccasion_delegatesTypedIdsToDao() = runTest {
        repository.deactivateContactDerivedOccasion(ContactId("contact_1"), OccasionType.BIRTHDAY)

        coVerify { eventDao.deactivateContactDerivedEvent("contact_1", "BIRTHDAY") }
    }
}
