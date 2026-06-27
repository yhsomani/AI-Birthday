package com.example.domain.usecase

import com.example.domain.model.common.ContactId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.contact.ContactEventDiscoveryProfile
import com.example.domain.model.occasion.Occasion
import com.example.domain.model.occasion.OccasionDate
import com.example.domain.model.occasion.OccasionType
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
        coEvery { contactRepository.getEventDiscoveryProfiles() } returns emptyList()
        every { eventRepository.getOccasions() } returns MutableStateFlow(emptyList())

        val outcome = useCase()

        assertEquals(0, outcome.contacts)
        assertEquals(0, outcome.events)
        coVerify(exactly = 0) { eventRepository.upsertOccasion(any()) }
        coVerify(exactly = 0) { eventReminderSchedulerService.scheduleReminder(any()) }
    }

    @Test
    fun `invoke with contacts having birthday, anniversary, and work anniversary generates events`() = runTest {
        val today = Calendar.getInstance()
        val birthdayDay = today.get(Calendar.DAY_OF_MONTH)
        val birthdayMonth = today.get(Calendar.MONTH) + 1

        val contact = contactProfile(
            id = "c1",
            displayName = "Alice",
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

        coEvery { contactRepository.getEventDiscoveryProfiles() } returns listOf(contact)
        every { eventRepository.getOccasions() } returns MutableStateFlow(emptyList())

        val outcome = useCase()

        assertEquals(1, outcome.contacts)
        assertEquals(3, outcome.events) // Birthday, Anniversary, Work Anniversary

        coVerify(exactly = 3) { eventRepository.upsertOccasion(any()) }
        coVerify(exactly = 3) { eventReminderSchedulerService.scheduleReminder(any()) }
    }

    @Test
    fun `invoke skips contact-derived event when matching manual event already exists`() = runTest {
        val contact = contactProfile(
            id = "c1",
            displayName = "Alice",
            birthdayDay = 12,
            birthdayMonth = 6,
            birthdayYear = 1994,
        )
        val manualEvent = occasion(
            id = "manual_1",
            contactId = "c1",
            type = OccasionType.BIRTHDAY,
            day = 12,
            month = 6,
            year = 1994,
            nextOccurrenceMs = 100L,
            source = "MANUAL",
            isVerified = true,
        )
        coEvery { contactRepository.getEventDiscoveryProfiles() } returns listOf(contact)
        every { eventRepository.getOccasions() } returns MutableStateFlow(listOf(manualEvent))

        val outcome = useCase()

        assertEquals(1, outcome.contacts)
        assertEquals(0, outcome.events)
        coVerify(exactly = 0) { eventRepository.upsertOccasion(any()) }
        coVerify(exactly = 0) { eventReminderSchedulerService.scheduleReminder(any()) }
    }

    @Test
    fun `invoke refreshes matching contact-derived event`() = runTest {
        val contact = contactProfile(
            id = "c1",
            displayName = "Alice",
            birthdayDay = 12,
            birthdayMonth = 6,
            birthdayYear = 1994,
        )
        val contactEvent = occasion(
            id = "c1_birthday",
            contactId = "c1",
            type = OccasionType.BIRTHDAY,
            day = 12,
            month = 6,
            year = 1994,
            nextOccurrenceMs = 100L,
            source = "CONTACTS",
            isVerified = true,
        )
        coEvery { contactRepository.getEventDiscoveryProfiles() } returns listOf(contact)
        every { eventRepository.getOccasions() } returns MutableStateFlow(listOf(contactEvent))

        val outcome = useCase()

        assertEquals(1, outcome.contacts)
        assertEquals(1, outcome.events)
        coVerify(exactly = 1) {
            eventRepository.upsertOccasion(match { it.id.value == "c1_birthday" && it.source == "CONTACTS" })
        }
        coVerify(exactly = 1) { eventReminderSchedulerService.scheduleReminder(any()) }
    }

    @Test
    fun `invoke skips invalid imported dates instead of rolling them over`() = runTest {
        val contact = contactProfile(
            id = "c1",
            displayName = "Alice",
            birthdayDay = 31,
            birthdayMonth = 2,
        )
        coEvery { contactRepository.getEventDiscoveryProfiles() } returns listOf(contact)
        every { eventRepository.getOccasions() } returns MutableStateFlow(emptyList())

        val outcome = useCase()

        assertEquals(1, outcome.contacts)
        assertEquals(0, outcome.events)
        coVerify(exactly = 0) { eventRepository.upsertOccasion(any()) }
        coVerify(exactly = 0) { eventReminderSchedulerService.scheduleReminder(any()) }
    }

    @Test
    fun `invoke deactivates contact-derived events when contact date is removed`() = runTest {
        val contact = contactProfile(
            id = "c1",
            displayName = "Alice",
            birthdayDay = null,
            birthdayMonth = null,
        )
        coEvery { contactRepository.getEventDiscoveryProfiles() } returns listOf(contact)
        every { eventRepository.getOccasions() } returns MutableStateFlow(emptyList())

        val outcome = useCase()

        assertEquals(1, outcome.contacts)
        assertEquals(0, outcome.events)
        coVerify(exactly = 1) { eventRepository.deactivateContactDerivedOccasion(ContactId("c1"), OccasionType.BIRTHDAY) }
        coVerify(exactly = 1) { eventRepository.deactivateContactDerivedOccasion(ContactId("c1"), OccasionType.ANNIVERSARY) }
        coVerify(exactly = 1) { eventRepository.deactivateContactDerivedOccasion(ContactId("c1"), OccasionType.WORK_ANNIVERSARY) }
        coVerify(exactly = 0) { eventRepository.upsertOccasion(any()) }
        coVerify(exactly = 0) { eventReminderSchedulerService.scheduleReminder(any()) }
    }

    private fun contactProfile(
        id: String,
        displayName: String,
        birthdayDay: Int? = null,
        birthdayMonth: Int? = null,
        birthdayYear: Int? = null,
        anniversaryDay: Int? = null,
        anniversaryMonth: Int? = null,
        anniversaryYear: Int? = null,
        workStartDay: Int? = null,
        workStartMonth: Int? = null,
        workStartYear: Int? = null,
    ) = ContactEventDiscoveryProfile(
        id = ContactId(id),
        displayName = displayName,
        birthdayDay = birthdayDay,
        birthdayMonth = birthdayMonth,
        birthdayYear = birthdayYear,
        anniversaryDay = anniversaryDay,
        anniversaryMonth = anniversaryMonth,
        anniversaryYear = anniversaryYear,
        workStartDay = workStartDay,
        workStartMonth = workStartMonth,
        workStartYear = workStartYear,
    )

    private fun occasion(
        id: String,
        contactId: String,
        type: OccasionType,
        day: Int,
        month: Int,
        year: Int? = null,
        nextOccurrenceMs: Long = 100L,
        source: String = "CONTACTS",
        isVerified: Boolean = true,
    ) = Occasion(
        id = OccasionId(id),
        contactId = ContactId(contactId),
        type = type,
        label = "Alice",
        date = OccasionDate(
            dayOfMonth = day,
            month = month,
            year = year,
        ),
        nextOccurrenceMs = nextOccurrenceMs,
        isActive = true,
        notifyDaysBefore = 1,
        source = source,
        confidenceScore = 100,
        isVerified = isVerified,
    )
}
