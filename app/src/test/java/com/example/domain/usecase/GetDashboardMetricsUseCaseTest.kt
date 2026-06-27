package com.example.domain.usecase

import com.example.domain.model.common.ContactId
import com.example.domain.model.contact.ContactHealthProfile
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.repository.MessageRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetDashboardMetricsUseCaseTest {

    private val contactRepository: ContactRepository = mockk(relaxed = true)
    private val eventRepository: EventRepository = mockk(relaxed = true)
    private val messageRepository: MessageRepository = mockk(relaxed = true)
    private val useCase = GetDashboardMetricsUseCase(contactRepository, eventRepository, messageRepository)

    @Test
    fun `invoke with empty repositories returns zero metrics`() = runTest {
        coEvery { contactRepository.getHealthProfiles() } returns emptyList()
        every { messageRepository.countPending() } returns flowOf(0)
        coEvery { eventRepository.countUpcoming(30) } returns 0
        every { messageRepository.countAllSent() } returns flowOf(0)

        val metrics = useCase()

        assertEquals(0, metrics.healthScore)
        assertEquals(0, metrics.pendingCount)
        assertEquals(0, metrics.upcomingEventsCount)
        assertEquals(0, metrics.contactCount)
        assertEquals(0, metrics.sentCount)
    }

    @Test
    fun `invoke computes correct metrics using status-filtered pending count`() = runTest {
        val contacts = listOf(
            healthProfile(id = "c1", healthScore = 80),
            healthProfile(id = "c2", healthScore = 90),
        )

        coEvery { contactRepository.getHealthProfiles() } returns contacts
        every { messageRepository.countPending() } returns flowOf(1)
        coEvery { eventRepository.countUpcoming(30) } returns 1
        every { messageRepository.countAllSent() } returns flowOf(10)

        val metrics = useCase()

        assertEquals(85, metrics.healthScore) // (80 + 90) / 2 = 85
        assertEquals(1, metrics.pendingCount)
        assertEquals(1, metrics.upcomingEventsCount)
        assertEquals(2, metrics.contactCount)
        assertEquals(10, metrics.sentCount)
        verify(exactly = 0) { messageRepository.getAllPending() }
        verify(exactly = 1) { messageRepository.countPending() }
        coVerify(exactly = 0) { contactRepository.getAllSync() }
        coVerify(exactly = 1) { contactRepository.getHealthProfiles() }
        coVerify(exactly = 1) { eventRepository.countUpcoming(30) }
    }

    private fun healthProfile(id: String, healthScore: Int): ContactHealthProfile {
        return ContactHealthProfile(
            id = ContactId(id),
            currentHealthScore = healthScore,
            interactionFrequencyPerMonth = 0f,
            lastInteractionAtMs = null,
            lastWishedAtMs = null,
            consecutiveYearsWished = 0,
        )
    }
}
