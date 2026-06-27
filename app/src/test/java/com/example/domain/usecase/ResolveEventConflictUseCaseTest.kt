package com.example.domain.usecase

import com.example.domain.event.EventResolutionPolicy
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.occasion.Occasion
import com.example.domain.model.occasion.OccasionDate
import com.example.domain.model.occasion.OccasionType
import com.example.domain.repository.EventRepository
import com.example.domain.service.EventReminderSchedulerService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ResolveEventConflictUseCaseTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    private val eventRepository: EventRepository = mockk(relaxed = true)
    private val eventReminderSchedulerService: EventReminderSchedulerService = mockk(relaxed = true)
    private val useCase = ResolveEventConflictUseCase(eventRepository, eventReminderSchedulerService)

    @Test
    fun `merge keeps selected event active and deactivates conflicting siblings`() = runTest {
        val imported = event(
            id = "imported",
            source = "CONTACTS",
            month = 6,
            dayOfMonth = 12,
        )
        val manual = event(
            id = "manual",
            source = "MANUAL",
            month = 7,
            dayOfMonth = 1,
        )
        val upserts = mutableListOf<Occasion>()
        every { eventRepository.getOccasions() } returns MutableStateFlow(listOf(imported, manual))
        coEvery { eventRepository.upsertOccasion(capture(upserts)) } returns Unit

        val outcome = useCase(
            ResolveEventConflictUseCase.Request(
                eventId = manual.id.value,
                action = ResolveEventConflictUseCase.Action.MERGE_KEEP_SELECTED,
            )
        )

        assertTrue(outcome is ResolveEventConflictUseCase.Outcome.Resolved)
        assertEquals(manual.id.value, (outcome as ResolveEventConflictUseCase.Outcome.Resolved).keptEvent.id.value)
        assertEquals(listOf(imported.id.value), outcome.affectedEventIds)
        assertTrue(upserts.any { it.id == manual.id && it.isActive && it.source == "MANUAL" && it.isVerified })
        assertTrue(upserts.any { it.id == imported.id && !it.isActive })
        verify { eventReminderSchedulerService.scheduleReminder(match { it.eventId == manual.id }) }
        verify { eventReminderSchedulerService.cancelReminder(imported.id.value) }
    }

    @Test
    fun `keep separate marks active conflict group as reviewed without canceling reminders`() = runTest {
        val imported = event(
            id = "imported",
            source = "CONTACTS",
            month = 6,
            dayOfMonth = 12,
            isVerified = false,
        )
        val manual = event(
            id = "manual",
            source = "MANUAL",
            month = 7,
            dayOfMonth = 1,
        )
        val upserts = mutableListOf<Occasion>()
        every { eventRepository.getOccasions() } returns MutableStateFlow(listOf(imported, manual))
        coEvery { eventRepository.upsertOccasion(capture(upserts)) } returns Unit

        val outcome = useCase(
            ResolveEventConflictUseCase.Request(
                eventId = manual.id.value,
                action = ResolveEventConflictUseCase.Action.KEEP_SEPARATE,
            )
        )

        assertTrue(outcome is ResolveEventConflictUseCase.Outcome.Resolved)
        assertEquals(setOf(imported.id, manual.id), upserts.map { it.id }.toSet())
        assertTrue(upserts.all { it.isVerified })
        assertTrue(upserts.any { it.id == imported.id && it.source == EventResolutionPolicy.keepSeparateSource("CONTACTS") })
        assertTrue(upserts.any { it.id == manual.id && it.source == EventResolutionPolicy.keepSeparateSource("MANUAL") })
        assertTrue(EventResolutionPolicy.conflictStates(upserts).isEmpty())
        verify(exactly = 0) { eventReminderSchedulerService.cancelReminder(any()) }
        verify(exactly = 0) { eventReminderSchedulerService.scheduleReminder(any()) }
    }

    private fun event(
        id: String,
        source: String,
        month: Int,
        dayOfMonth: Int,
        isVerified: Boolean = true,
    ): Occasion {
        return Occasion(
            id = OccasionId(id),
            contactId = ContactId("contact_1"),
            type = OccasionType.BIRTHDAY,
            label = "Birthday",
            date = OccasionDate(
                dayOfMonth = dayOfMonth,
                month = month,
            ),
            nextOccurrenceMs = 1_800_000_000_000,
            isActive = true,
            notifyDaysBefore = 1,
            source = source,
            confidenceScore = 100,
            isVerified = isVerified,
        )
    }
}
