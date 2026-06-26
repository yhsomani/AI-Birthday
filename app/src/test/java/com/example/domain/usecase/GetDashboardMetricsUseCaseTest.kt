package com.example.domain.usecase

import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.EventEntity
import com.example.core.db.entities.PendingMessageEntity
import com.example.domain.model.MessageChannel
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.repository.MessageRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
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
        coEvery { contactRepository.getAllSync() } returns emptyList()
        every { messageRepository.getAllPending() } returns flowOf(emptyList())
        coEvery { eventRepository.getUpcoming(30) } returns emptyList()
        every { messageRepository.countAllSent() } returns flowOf(0)

        val metrics = useCase()

        assertEquals(0, metrics.healthScore)
        assertEquals(0, metrics.pendingCount)
        assertEquals(0, metrics.upcomingEventsCount)
        assertEquals(0, metrics.contactCount)
        assertEquals(0, metrics.sentCount)
    }

    @Test
    fun `invoke computes correct metrics and average health score`() = runTest {
        val contacts = listOf(
            ContactEntity(id = "c1", name = "Alice", healthScore = 80),
            ContactEntity(id = "c2", name = "Bob", healthScore = 90)
        )
        val pending = listOf(
            PendingMessageEntity(
                id = "m1", contactId = "c1", eventId = "e1",
                shortVariant = "", standardVariant = "", longVariant = "",
                formalVariant = "", funnyVariant = "", emotionalVariant = "",
                selectedVariant = "standard", selectedVariantText = "",
                channel = MessageChannel.SMS.raw, scheduledForMs = 0, approvalMode = "MANUAL"
            )
        )
        val events = listOf(
            EventEntity(id = "e1", contactId = "c1", type = "BIRTHDAY", label = "Test", dayOfMonth = 1, month = 1, nextOccurrenceMs = 1000L)
        )

        coEvery { contactRepository.getAllSync() } returns contacts
        every { messageRepository.getAllPending() } returns flowOf(pending)
        coEvery { eventRepository.getUpcoming(30) } returns events
        every { messageRepository.countAllSent() } returns flowOf(10)

        val metrics = useCase()

        assertEquals(85, metrics.healthScore) // (80 + 90) / 2 = 85
        assertEquals(1, metrics.pendingCount)
        assertEquals(1, metrics.upcomingEventsCount)
        assertEquals(2, metrics.contactCount)
        assertEquals(10, metrics.sentCount)
    }
}
