package com.example.domain.usecase

import com.example.domain.model.common.ContactId
import com.example.domain.model.contact.ContactAnalyticsSummary
import com.example.domain.model.contact.RelationshipAnalyticsCount
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.MessageRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetAnalyticsUseCaseTest {

    private val contactRepository: ContactRepository = mockk(relaxed = true)
    private val messageRepository: MessageRepository = mockk(relaxed = true)
    private val useCase = GetAnalyticsUseCase(contactRepository, messageRepository)

    @Test
    fun `invoke combines repository flows into AnalyticsSnapshot`() = runTest {
        val relCounts = listOf(
            RelationshipAnalyticsCount("FRIEND", 10),
            RelationshipAnalyticsCount("FAMILY", 5),
        )

        every { messageRepository.countAllSent() } returns flowOf(25)
        every { messageRepository.countPending() } returns flowOf(3)
        every { contactRepository.countAll() } returns flowOf(15)
        every { contactRepository.getRelationshipAnalyticsCounts() } returns flowOf(relCounts)

        val topContacts = listOf(
            ContactAnalyticsSummary(
                id = ContactId("c1"),
                displayName = "Alice",
                healthScore = 90,
                relationshipType = "FRIEND",
            ),
        )
        val neglectedContacts = listOf(
            ContactAnalyticsSummary(
                id = ContactId("c2"),
                displayName = "Bob",
                healthScore = 20,
                relationshipType = "FAMILY",
            ),
        )
        coEvery { contactRepository.getTopHealthSummaries(5) } returns topContacts
        coEvery { contactRepository.getBottomHealthSummaries(5) } returns neglectedContacts

        val snapshot = useCase().first()

        assertEquals(25, snapshot.totalWishesSent)
        assertEquals(3, snapshot.pendingApprovals)
        assertEquals(15, snapshot.totalContacts)
        assertEquals(relCounts, snapshot.relationshipCounts)
        assertEquals(topContacts, snapshot.topHealthContacts)
        assertEquals(neglectedContacts, snapshot.neglectedContacts)
    }
}
