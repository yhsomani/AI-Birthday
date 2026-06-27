package com.example.core.analytics

import com.example.domain.model.ActivityLogType
import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.contact.ContactAnalyticsProfile
import com.example.domain.model.contact.RelationshipAnalyticsCount
import com.example.domain.model.message.MessageAnalyticsRecord
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
            contactProfiles = emptyList(),
            relationshipCounts = emptyList(),
            upcomingEventsCount = 0,
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
        coVerify { activityLogRepository.record(match { it.type == ActivityLogType.ANALYTICS.raw }) }
    }

    @Test
    fun `buildRelationshipReport emits populated aggregate csv without contact names`() = runTest {
        stubReportInputs(
            contactProfiles = listOf(
                analyticsProfile(id = "c1", healthScore = 90),
                analyticsProfile(id = "c2", healthScore = 50),
                analyticsProfile(id = "c3", healthScore = 20),
            ),
            relationshipCounts = listOf(
                RelationshipAnalyticsCount("FAMILY", 1),
                RelationshipAnalyticsCount("FRIEND", 1),
                RelationshipAnalyticsCount("WORK", 1),
            ),
            upcomingEventsCount = 1,
            sentThisYear = listOf(
                MessageAnalyticsRecord(
                    sentAtMs = 100L,
                    deliveryStatus = MessageDeliveryStatus.SENT,
                    replyReceived = false,
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

    private fun analyticsProfile(id: String, healthScore: Int): ContactAnalyticsProfile {
        return ContactAnalyticsProfile(
            id = ContactId(id),
            healthScore = healthScore,
            nickname = null,
            notesText = "",
            interestsJson = "[]",
            sharedHistoryJson = "[]",
        )
    }

    private fun stubReportInputs(
        contactProfiles: List<ContactAnalyticsProfile>,
        relationshipCounts: List<RelationshipAnalyticsCount>,
        upcomingEventsCount: Int,
        sentThisYear: List<MessageAnalyticsRecord>,
        totalSent: Int,
        pending: Int,
    ) {
        coEvery { contactRepository.getAnalyticsProfiles() } returns contactProfiles
        every { contactRepository.getRelationshipAnalyticsCounts() } returns MutableStateFlow(relationshipCounts)
        every { messageRepository.countAllSent() } returns MutableStateFlow(totalSent)
        every { messageRepository.countPending() } returns MutableStateFlow(pending)
        coEvery { eventRepository.countUpcoming(30) } returns upcomingEventsCount
        coEvery { messageRepository.getSentAnalyticsRecordsSince(any()) } returns sentThisYear
    }
}
