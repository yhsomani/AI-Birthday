package com.example.domain.usecase

import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.EventEntity
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SaveManualEventUseCaseTest {
    private val contactRepository: ContactRepository = mockk(relaxed = true)
    private val eventRepository: EventRepository = mockk(relaxed = true)
    private val useCase = SaveManualEventUseCase(contactRepository, eventRepository)

    @Test
    fun `existing contact saves birthday event and updates contact birthday fields`() = runTest {
        val contact = ContactEntity(id = "c1", name = "Alice")
        val eventSlot = slot<EventEntity>()
        coEvery { contactRepository.getById("c1") } returns contact

        val outcome = useCase(
            SaveManualEventUseCase.Request(
                existingContactId = "c1",
                eventType = "BIRTHDAY",
                month = 6,
                dayOfMonth = 12,
                year = 1994,
            )
        )

        assertTrue(outcome is SaveManualEventUseCase.Outcome.Saved)
        coVerify {
            contactRepository.upsert(
                match {
                    it.id == "c1" &&
                        it.birthdayMonth == 6 &&
                        it.birthdayDay == 12 &&
                        it.birthdayYear == 1994
                }
            )
        }
        coVerify { eventRepository.upsert(capture(eventSlot)) }
        assertEquals("c1", eventSlot.captured.contactId)
        assertEquals("BIRTHDAY", eventSlot.captured.type)
        assertEquals("MANUAL", eventSlot.captured.source)
    }

    @Test
    fun `new contact creates local manual contact before event`() = runTest {
        val contactSlot = slot<ContactEntity>()
        val eventSlot = slot<EventEntity>()

        val outcome = useCase(
            SaveManualEventUseCase.Request(
                newContactName = "Priya",
                eventType = "ANNIVERSARY",
                label = "Wedding anniversary",
                month = 11,
                dayOfMonth = 7,
            )
        )

        assertTrue(outcome is SaveManualEventUseCase.Outcome.Saved)
        coVerify { contactRepository.upsert(capture(contactSlot)) }
        coVerify { eventRepository.upsert(capture(eventSlot)) }
        assertTrue(contactSlot.captured.id.startsWith("manual_"))
        assertEquals("Priya", contactSlot.captured.name)
        assertEquals(contactSlot.captured.id, eventSlot.captured.contactId)
        assertEquals("Wedding anniversary", eventSlot.captured.label)
    }

    @Test
    fun `invalid date does not persist anything`() = runTest {
        val outcome = useCase(
            SaveManualEventUseCase.Request(
                newContactName = "Invalid",
                month = 2,
                dayOfMonth = 30,
            )
        )

        assertTrue(outcome is SaveManualEventUseCase.Outcome.InvalidInput)
        coVerify(exactly = 0) { contactRepository.upsert(any()) }
        coVerify(exactly = 0) { eventRepository.upsert(any()) }
    }

    @Test
    fun `past date rolls to the next year`() {
        val now = millisFor(2026, Calendar.DECEMBER, 31)
        val next = SaveManualEventUseCase.nextOccurrenceMs(day = 1, month = 1, nowMs = now)

        val cal = Calendar.getInstance().apply { timeInMillis = next }
        assertEquals(2027, cal.get(Calendar.YEAR))
        assertEquals(Calendar.JANUARY, cal.get(Calendar.MONTH))
        assertEquals(1, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `february twenty ninth finds the next leap year`() {
        val now = millisFor(2025, Calendar.JANUARY, 1)
        val next = SaveManualEventUseCase.nextOccurrenceMs(day = 29, month = 2, nowMs = now)

        val cal = Calendar.getInstance().apply { timeInMillis = next }
        assertEquals(2028, cal.get(Calendar.YEAR))
        assertEquals(Calendar.FEBRUARY, cal.get(Calendar.MONTH))
        assertEquals(29, cal.get(Calendar.DAY_OF_MONTH))
    }

    private fun millisFor(year: Int, month: Int, day: Int): Long {
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
