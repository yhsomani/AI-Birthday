package com.example.domain.usecase

import com.example.core.db.dao.RelationshipTypeCount
import com.example.core.db.entities.ContactEntity
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.MessageRepository
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
            RelationshipTypeCount("FRIEND", 10),
            RelationshipTypeCount("FAMILY", 5)
        )

        every { messageRepository.countAllSent() } returns flowOf(25)
        every { messageRepository.countPending() } returns flowOf(3)
        every { contactRepository.countAll() } returns flowOf(15)
        every { contactRepository.countByRelationshipType() } returns flowOf(relCounts)

        val topContacts = listOf(ContactEntity(id = "c1", name = "Alice", healthScore = 90))
        val neglectedContacts = listOf(ContactEntity(id = "c2", name = "Bob", healthScore = 20))

        val snapshot = useCase(
            topHealthContactsProvider = { topContacts },
            neglectedContactsProvider = { neglectedContacts }
        ).first()

        assertEquals(25, snapshot.totalWishesSent)
        assertEquals(3, snapshot.pendingApprovals)
        assertEquals(15, snapshot.totalContacts)
        assertEquals(relCounts, snapshot.relationshipCounts)
        assertEquals(topContacts, snapshot.topHealthContacts)
        assertEquals(neglectedContacts, snapshot.neglectedContacts)
    }
}
