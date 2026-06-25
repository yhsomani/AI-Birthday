package com.example.domain.usecase

import com.example.core.db.entities.ContactEntity
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.service.EventReminderSchedulerService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class DiscoverEventsUseCaseTest {

    private val contactRepository: ContactRepository = mockk(relaxed = true)
    private val eventRepository: EventRepository = mockk(relaxed = true)
    private val eventReminderSchedulerService: EventReminderSchedulerService = mockk(relaxed = true)
    private val useCase = DiscoverEventsUseCase(
        contactRepository,
        eventRepository,
        eventReminderSchedulerService,
    )

    @Test
    fun `invoke with no contacts returns zero discovered events`() = runTest {
        coEvery { contactRepository.getAllSync() } returns emptyList()

        val outcome = useCase()

        assertEquals(0, outcome.contacts)
        assertEquals(0, outcome.events)
        coVerify(exactly = 0) { eventRepository.upsert(any()) }
        coVerify(exactly = 0) { eventReminderSchedulerService.scheduleReminder(any()) }
    }

    @Test
    fun `invoke with contacts having birthday, anniversary, and work anniversary generates events`() = runTest {
        val today = Calendar.getInstance()
        val birthdayDay = today.get(Calendar.DAY_OF_MONTH)
        val birthdayMonth = today.get(Calendar.MONTH) + 1

        val contact = ContactEntity(
            id = "c1",
            name = "Alice",
            birthdayDay = birthdayDay,
            birthdayMonth = birthdayMonth,
            birthdayYear = 1990,
            anniversaryDay = 10,
            anniversaryMonth = 5,
            anniversaryYear = 2015,
            workStartDay = 1,
            workStartMonth = 9,
            workStartYear = 2020
        )

        coEvery { contactRepository.getAllSync() } returns listOf(contact)

        val outcome = useCase()

        assertEquals(1, outcome.contacts)
        assertEquals(3, outcome.events) // Birthday, Anniversary, Work Anniversary

        coVerify(exactly = 3) { eventRepository.upsert(any()) }
        coVerify(exactly = 3) { eventReminderSchedulerService.scheduleReminder(any()) }
    }

    @Test
    fun `invoke skips contact-derived event when matching manual event already exists`() = runTest {
        val contact = ContactEntity(
            id = "c1",
            name = "Alice",
            birthdayDay = 12,
            birthdayMonth = 6,
            birthdayYear = 1994,
        )
        val manualEvent = com.example.core.db.entities.EventEntity(
            id = "manual_1",
            contactId = "c1",
            type = "BIRTHDAY",
            dayOfMonth = 12,
            month = 6,
            year = 1994,
            nextOccurrenceMs = 100L,
            source = "MANUAL",
            isVerified = true,
        )
        coEvery { contactRepository.getAllSync() } returns listOf(contact)
        every { eventRepository.getAll() } returns MutableStateFlow(listOf(manualEvent))

        val outcome = useCase()

        assertEquals(1, outcome.contacts)
        assertEquals(0, outcome.events)
        coVerify(exactly = 0) { eventRepository.upsert(any()) }
        coVerify(exactly = 0) { eventReminderSchedulerService.scheduleReminder(any()) }
    }

    @Test
    fun `invoke refreshes matching contact-derived event`() = runTest {
        val contact = ContactEntity(
            id = "c1",
            name = "Alice",
            birthdayDay = 12,
            birthdayMonth = 6,
            birthdayYear = 1994,
        )
        val contactEvent = com.example.core.db.entities.EventEntity(
            id = "c1_birthday",
            contactId = "c1",
            type = "BIRTHDAY",
            dayOfMonth = 12,
            month = 6,
            year = 1994,
            nextOccurrenceMs = 100L,
            source = "CONTACTS",
            isVerified = true,
        )
        coEvery { contactRepository.getAllSync() } returns listOf(contact)
        every { eventRepository.getAll() } returns MutableStateFlow(listOf(contactEvent))

        val outcome = useCase()

        assertEquals(1, outcome.contacts)
        assertEquals(1, outcome.events)
        coVerify(exactly = 1) { eventRepository.upsert(match { it.id == "c1_birthday" && it.source == "CONTACTS" }) }
        coVerify(exactly = 1) { eventReminderSchedulerService.scheduleReminder(any()) }
    }

    @Test
    fun `invoke skips invalid imported dates instead of rolling them over`() = runTest {
        val contact = ContactEntity(
            id = "c1",
            name = "Alice",
            birthdayDay = 31,
            birthdayMonth = 2,
        )
        coEvery { contactRepository.getAllSync() } returns listOf(contact)
        every { eventRepository.getAll() } returns MutableStateFlow(emptyList())

        val outcome = useCase()

        assertEquals(1, outcome.contacts)
        assertEquals(0, outcome.events)
        coVerify(exactly = 0) { eventRepository.upsert(any()) }
        coVerify(exactly = 0) { eventReminderSchedulerService.scheduleReminder(any()) }
    }
}
