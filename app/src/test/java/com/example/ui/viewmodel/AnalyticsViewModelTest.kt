package com.example.ui.viewmodel

import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.contact.ContactAnalyticsProfile
import com.example.domain.model.contact.ContactAnalyticsSummary
import com.example.domain.model.message.MessageAnalyticsRecord
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.repository.MessageRepository
import com.example.domain.service.AnalyticsReport
import com.example.domain.service.AnalyticsReportService
import com.example.domain.usecase.GetAnalyticsUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModelTest {
    private val contactRepository: ContactRepository = mockk(relaxed = true)
    private val eventRepository: EventRepository = mockk(relaxed = true)
    private val messageRepository: MessageRepository = mockk(relaxed = true)
    private val analyticsReportService: AnalyticsReportService = mockk(relaxed = true)
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `zero sent messages keeps monthly chart empty`() = runTest(dispatcher) {
        stubEmptyAnalytics()

        val viewModel = newViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertEquals(emptyList<Pair<String, Float>>(), state.monthlyCounts)
    }

    @Test
    fun `exportRelationshipReport exposes generated report`() = runTest(dispatcher) {
        stubEmptyAnalytics()
        coEvery { analyticsReportService.buildRelationshipReport() } returns AnalyticsReport(
            fileName = "report.csv",
            mimeType = "text/csv",
            content = "section,metric,value\n",
        )

        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.exportRelationshipReport()
        advanceUntilIdle()

        assertEquals("report.csv", viewModel.uiState.value.exportReport?.fileName)
        assertEquals(false, viewModel.uiState.value.isExporting)
    }

    @Test
    fun `delivery reliability excludes failed analytics records`() = runTest(dispatcher) {
        every { messageRepository.countAllSent() } returns MutableStateFlow(2)
        every { messageRepository.countPending() } returns MutableStateFlow(0)
        every { contactRepository.countAll() } returns MutableStateFlow(0)
        every { contactRepository.getRelationshipAnalyticsCounts() } returns MutableStateFlow(emptyList())
        every { contactRepository.getTopHealthSummariesFlow(5) } returns MutableStateFlow(emptyList())
        every { contactRepository.getBottomHealthSummariesFlow(5) } returns MutableStateFlow(
            listOf(
                ContactAnalyticsSummary(
                    id = ContactId("contact_low"),
                    displayName = "Neha",
                    healthScore = 18,
                    relationshipType = "FRIEND",
                ),
            ),
        )
        every { contactRepository.getAnalyticsProfilesFlow() } returns MutableStateFlow(emptyList())
        every { eventRepository.countUpcomingFlow(30) } returns MutableStateFlow(0)
        every { messageRepository.getSentAnalyticsRecordsSinceFlow(any()) } returns MutableStateFlow(
            listOf(
                sentMessageAnalyticsRecord(deliveryStatus = MessageDeliveryStatus.SENT),
                sentMessageAnalyticsRecord(deliveryStatus = MessageDeliveryStatus.FAILED),
            ),
        )

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(50, viewModel.uiState.value.deliveryReliabilityPercent)
        assertEquals(listOf("Neha (18)"), viewModel.uiState.value.topNeglectedContacts)
    }

    @Test
    fun `analytics profile flow immediately updates health buckets and coverage`() = runTest(dispatcher) {
        val analyticsProfiles = MutableStateFlow(emptyList<ContactAnalyticsProfile>())
        every { messageRepository.countAllSent() } returns MutableStateFlow(0)
        every { messageRepository.countPending() } returns MutableStateFlow(0)
        every { contactRepository.countAll() } returns MutableStateFlow(3)
        every { contactRepository.getRelationshipAnalyticsCounts() } returns MutableStateFlow(emptyList())
        every { contactRepository.getTopHealthSummariesFlow(5) } returns MutableStateFlow(emptyList())
        every { contactRepository.getBottomHealthSummariesFlow(5) } returns MutableStateFlow(emptyList())
        every { contactRepository.getAnalyticsProfilesFlow() } returns analyticsProfiles
        every { eventRepository.countUpcomingFlow(30) } returns MutableStateFlow(0)
        every { messageRepository.getSentAnalyticsRecordsSinceFlow(any()) } returns MutableStateFlow(emptyList())

        val viewModel = newViewModel()
        advanceUntilIdle()

        analyticsProfiles.value = listOf(
            analyticsProfile(id = "healthy", healthScore = 80, nickname = "Ash"),
            analyticsProfile(id = "attention", healthScore = 45, interestsJson = "[\"music\"]"),
            analyticsProfile(id = "risk", healthScore = 15),
        )
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.healthCounts["Healthy (70%+)"])
        assertEquals(1, viewModel.uiState.value.healthCounts["Needs Attention"])
        assertEquals(1, viewModel.uiState.value.healthCounts["At Risk"])
        assertEquals(66, viewModel.uiState.value.personalizationCoveragePercent)
    }

    @Test
    fun `analytics profiles drive health buckets and personalization coverage`() = runTest(dispatcher) {
        every { messageRepository.countAllSent() } returns MutableStateFlow(0)
        every { messageRepository.countPending() } returns MutableStateFlow(0)
        every { contactRepository.countAll() } returns MutableStateFlow(3)
        every { contactRepository.getRelationshipAnalyticsCounts() } returns MutableStateFlow(emptyList())
        every { contactRepository.getTopHealthSummariesFlow(5) } returns MutableStateFlow(emptyList())
        every { contactRepository.getBottomHealthSummariesFlow(5) } returns MutableStateFlow(emptyList())
        every { contactRepository.getAnalyticsProfilesFlow() } returns MutableStateFlow(
            listOf(
                analyticsProfile(id = "healthy", healthScore = 80, nickname = "Ash"),
                analyticsProfile(id = "attention", healthScore = 45, interestsJson = "[\"music\"]"),
                analyticsProfile(id = "risk", healthScore = 15),
            ),
        )
        every { eventRepository.countUpcomingFlow(30) } returns MutableStateFlow(0)
        every { messageRepository.getSentAnalyticsRecordsSinceFlow(any()) } returns MutableStateFlow(emptyList())

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.healthCounts["Healthy (70%+)"])
        assertEquals(1, viewModel.uiState.value.healthCounts["Needs Attention"])
        assertEquals(1, viewModel.uiState.value.healthCounts["At Risk"])
        assertEquals(66, viewModel.uiState.value.personalizationCoveragePercent)
    }

    private fun stubEmptyAnalytics() {
        every { messageRepository.countAllSent() } returns MutableStateFlow(0)
        every { messageRepository.countPending() } returns MutableStateFlow(0)
        every { contactRepository.countAll() } returns MutableStateFlow(0)
        every { contactRepository.getRelationshipAnalyticsCounts() } returns MutableStateFlow(emptyList())
        every { contactRepository.getTopHealthSummariesFlow(5) } returns MutableStateFlow(emptyList())
        every { contactRepository.getBottomHealthSummariesFlow(5) } returns MutableStateFlow(emptyList())
        every { contactRepository.getAnalyticsProfilesFlow() } returns MutableStateFlow(emptyList())
        every { eventRepository.countUpcomingFlow(30) } returns MutableStateFlow(0)
        every { messageRepository.getSentAnalyticsRecordsSinceFlow(any()) } returns MutableStateFlow(emptyList())
    }

    private fun newViewModel(): AnalyticsViewModel {
        return AnalyticsViewModel(
            getAnalyticsUseCase = GetAnalyticsUseCase(contactRepository, messageRepository),
            contactRepository = contactRepository,
            eventRepository = eventRepository,
            messageRepository = messageRepository,
            analyticsReportService = analyticsReportService,
        )
    }

    private fun analyticsProfile(
        id: String,
        healthScore: Int,
        nickname: String? = null,
        notesText: String = "",
        interestsJson: String = "[]",
        sharedHistoryJson: String = "[]",
    ): ContactAnalyticsProfile {
        return ContactAnalyticsProfile(
            id = ContactId(id),
            healthScore = healthScore,
            nickname = nickname,
            notesText = notesText,
            interestsJson = interestsJson,
            sharedHistoryJson = sharedHistoryJson,
        )
    }

    private fun sentMessageAnalyticsRecord(
        deliveryStatus: MessageDeliveryStatus,
        replyReceived: Boolean = false,
        sentAtMs: Long = System.currentTimeMillis(),
    ): MessageAnalyticsRecord {
        return MessageAnalyticsRecord(
            sentAtMs = sentAtMs,
            deliveryStatus = deliveryStatus,
            replyReceived = replyReceived,
        )
    }
}
