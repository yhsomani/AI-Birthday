package com.example.core.analytics

import com.example.core.db.dao.RelationshipTypeCount
import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.EventEntity
import com.example.core.db.entities.SentMessageEntity
import com.example.domain.repository.ActivityLogRepository
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.repository.MessageRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AnalyticsReportServiceImplTest {
    private val contactRepository: ContactRepository = mockk(relaxed = true)
    private val eventRepository: EventRepository = mockk(relaxed = true)
    private val messageRepository: MessageRepository = mockk(relaxed = true)
    private val activityLogRepository: ActivityLogRepository = mockk(relaxed = true)

    private lateinit var service: AnalyticsReportServiceImpl

    @Before
    fun setUp() {
        service = AnalyticsReportServiceImpl(
            contactRepository = contactRepository,
            eventRepository = eventRepository,
            messageRepository = messageRepository,
            activityLogRepository = activityLogRepository,
        )
    }

    @Test
    fun `buildRelationshipReport emits zero state aggregate csv`() = runTest {
        stubReportInputs(
            contacts = emptyList(),
            relationshipCounts = emptyList(),
            upcomingEvents = emptyList(),
            sentThisYear = emptyList(),
            totalSent = 0,
            pending = 0,
        )

        val report = service.buildRelationshipReport()

        assertEquals("text/csv", report.mimeType)
        assertTrue(report.fileName.startsWith("relateai-relationship-report-"))
        assertTrue(report.content.contains("summary,total_contacts,0"))
        assertTrue(report.content.contains("health,healthy_70_plus,0"))
        assertTrue(report.content.contains("messages,sent_this_year,0"))
        coVerify { activityLogRepository.record(match { it.type == "ANALYTICS" }) }
    }

    @Test
    fun `buildRelationshipReport emits populated aggregate csv without contact names`() = runTest {
        stubReportInputs(
            contacts = listOf(
                ContactEntity(id = "c1", name = "Alice", relationshipType = "FAMILY", healthScore = 90),
                ContactEntity(id = "c2", name = "Bob", relationshipType = "FRIEND", healthScore = 50),
                ContactEntity(id = "c3", name = "Cara", relationshipType = "WORK", healthScore = 20),
            ),
            relationshipCounts = listOf(
                RelationshipTypeCount("FAMILY", 1),
                RelationshipTypeCount("FRIEND", 1),
                RelationshipTypeCount("WORK", 1),
            ),
            upcomingEvents = listOf(
                EventEntity(
                    id = "e1",
                    contactId = "c1",
                    type = "BIRTHDAY",
                    dayOfMonth = 1,
                    month = 1,
                    nextOccurrenceMs = 200L,
                )
            ),
            sentThisYear = listOf(
                SentMessageEntity(
                    id = "s1",
                    contactId = "c1",
                    eventType = "BIRTHDAY",
                    eventYear = 2026,
                    messageText = "Happy birthday!",
                    channel = "SMS",
                    sentAtMs = 100L,
                    deliveryStatus = "SENT",
                )
            ),
            totalSent = 4,
            pending = 2,
        )

        val report = service.buildRelationshipReport()

        assertTrue(report.content.contains("summary,total_contacts,3"))
        assertTrue(report.content.contains("summary,total_wishes_sent,4"))
        assertTrue(report.content.contains("summary,pending_approvals,2"))
        assertTrue(report.content.contains("health,healthy_70_plus,1"))
        assertTrue(report.content.contains("health,needs_attention_30_to_69,1"))
        assertTrue(report.content.contains("health,at_risk_under_30,1"))
        assertTrue(report.content.contains("relationship_type,FAMILY,1"))
        assertTrue(report.content.contains("messages,sent_this_year,1"))
        assertTrue(!report.content.contains("Alice"))
        assertTrue(!report.content.contains("Happy birthday!"))
    }

    private fun stubReportInputs(
        contacts: List<ContactEntity>,
        relationshipCounts: List<RelationshipTypeCount>,
        upcomingEvents: List<EventEntity>,
        sentThisYear: List<SentMessageEntity>,
        totalSent: Int,
        pending: Int,
    ) {
        coEvery { contactRepository.getAllSync() } returns contacts
        every { contactRepository.countByRelationshipType() } returns MutableStateFlow(relationshipCounts)
        every { messageRepository.countAllSent() } returns MutableStateFlow(totalSent)
        every { messageRepository.countPending() } returns MutableStateFlow(pending)
        coEvery { eventRepository.getUpcoming(30) } returns upcomingEvents
        coEvery { messageRepository.getSentSinceYearStart(any()) } returns sentThisYear
    }
}
