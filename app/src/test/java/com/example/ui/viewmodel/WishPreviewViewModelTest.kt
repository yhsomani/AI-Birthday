package com.example.ui.viewmodel

import com.example.R
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.MessageFeedbackId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.contact.ContactWishContext
import com.example.domain.model.message.MessageFeedbackRecord
import com.example.domain.model.message.WishPreviewDraft
import com.example.domain.model.message.WishPreviewReviewItem
import com.example.domain.model.message.WishPreviewVariants
import com.example.domain.model.occasion.OccasionType
import com.example.domain.repository.ActivityLogRepository
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.repository.GiftHistoryRepository
import com.example.domain.repository.MemoryNoteRepository
import com.example.domain.repository.MessageFeedbackRepository
import com.example.domain.repository.MessageRepository
import com.example.domain.usecase.ApprovePendingMessageUseCase
import com.example.domain.usecase.RegeneratePendingMessageUseCase
import com.example.domain.usecase.RejectPendingMessageUseCase
import com.example.domain.usecase.TestSendUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WishPreviewViewModelTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @RelaxedMockK
    private lateinit var messageRepository: MessageRepository

    @RelaxedMockK
    private lateinit var activityLogRepository: ActivityLogRepository

    @RelaxedMockK
    private lateinit var messageFeedbackRepository: MessageFeedbackRepository

    @RelaxedMockK
    private lateinit var contactRepository: ContactRepository

    @RelaxedMockK
    private lateinit var eventRepository: EventRepository

    @RelaxedMockK
    private lateinit var memoryNoteRepository: MemoryNoteRepository

    @RelaxedMockK
    private lateinit var giftHistoryRepository: GiftHistoryRepository

    @RelaxedMockK
    private lateinit var approvePendingMessageUseCase: ApprovePendingMessageUseCase

    @RelaxedMockK
    private lateinit var rejectPendingMessageUseCase: RejectPendingMessageUseCase

    @RelaxedMockK
    private lateinit var regeneratePendingMessageUseCase: RegeneratePendingMessageUseCase

    @RelaxedMockK
    private lateinit var testSendUseCase: TestSendUseCase

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { contactRepository.getWishContext(any()) } returns null
        coEvery { memoryNoteRepository.countByContact(any()) } returns 0
        coEvery { giftHistoryRepository.countByContact(any()) } returns 0
        coEvery { messageRepository.getSentByContact(any(), any()) } returns emptyList()
        every { contactRepository.getWishContextFlow(any()) } returns flowOf(null)
        every { memoryNoteRepository.countByContactFlow(any()) } returns flowOf(0)
        every { giftHistoryRepository.countByContactFlow(any()) } returns flowOf(0)
        every { messageRepository.countSentByContact(any()) } returns flowOf(0)
        every { eventRepository.getOccasionTypeByIdFlow(any()) } returns flowOf(null)
        every { messageRepository.getWishPreviewDraftByRef(any()) } returns flowOf(null)
        every { messageRepository.getWishPreviewReviewQueue() } returns flowOf(emptyList())
        coEvery { eventRepository.getOccasionTypeById(any()) } returns null
        coEvery { messageFeedbackRepository.getLatestForPendingMessage(any()) } returns null
        coEvery { testSendUseCase(any()) } returns TestSendUseCase.Outcome.Sent
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun sampleDraft(): WishPreviewDraft = WishPreviewDraft(
        id = MessageDraftId("pm_1"),
        contactId = ContactId("c_1"),
        occasionId = OccasionId("e_1"),
        variants = WishPreviewVariants(
            short = "Happy B'day!",
            standard = "Wishing you a happy birthday!",
            long = "On this special day, I wish you all the best.",
            formal = "Wishing you a very happy birthday, dear friend.",
            funny = "Another year older, still awesome!",
            emotional = "You mean the world to me. Happy birthday!",
        ),
        selectedVariant = "standard",
        selectedVariantText = "Wishing you a happy birthday!",
        channel = MessageChannel.SMS,
        scheduledForMs = 1_700_000_000_000L,
        approvalMode = ApprovalMode.VIP_APPROVE,
        status = MessageStatus.PENDING,
        isUsingFallback = false,
    )

    private fun sampleReviewItem(
        id: String = "pm_1",
        contactId: String = "c_1",
        scheduledForMs: Long = 1_700_000_000_000L,
        status: MessageStatus = MessageStatus.PENDING,
    ) = WishPreviewReviewItem(
        id = MessageDraftId(id),
        contactId = ContactId(contactId),
        scheduledForMs = scheduledForMs,
        status = status,
    )

    private fun createViewModel() = WishPreviewViewModel(
        messageRepository = messageRepository,
        activityLogRepository = activityLogRepository,
        messageFeedbackRepository = messageFeedbackRepository,
        contactRepository = contactRepository,
        eventRepository = eventRepository,
        memoryNoteRepository = memoryNoteRepository,
        giftHistoryRepository = giftHistoryRepository,
        approvePendingMessageUseCase = approvePendingMessageUseCase,
        rejectPendingMessageUseCase = rejectPendingMessageUseCase,
        regeneratePendingMessageUseCase = regeneratePendingMessageUseCase,
        testSendUseCase = testSendUseCase,
    )

    private fun stubLiveDraft(
        messageRef: String = "pm_1",
        draft: WishPreviewDraft? = sampleDraft(),
        reviewQueue: List<WishPreviewReviewItem> = emptyList(),
        contact: ContactWishContext? = null,
        memoryCount: Int = 0,
        giftCount: Int = 0,
        previousWishes: Int = 0,
        eventType: OccasionType? = null,
    ) {
        every { messageRepository.getWishPreviewDraftByRef(messageRef) } returns flowOf(draft)
        every { messageRepository.getWishPreviewReviewQueue() } returns flowOf(reviewQueue)
        if (draft != null) {
            every { contactRepository.getWishContextFlow(draft.contactId.value) } returns flowOf(contact)
            every { memoryNoteRepository.countByContactFlow(draft.contactId.value) } returns flowOf(memoryCount)
            every { giftHistoryRepository.countByContactFlow(draft.contactId.value) } returns flowOf(giftCount)
            every { messageRepository.countSentByContact(draft.contactId.value) } returns flowOf(previousWishes)
            every { eventRepository.getOccasionTypeByIdFlow(draft.occasionId.value) } returns flowOf(eventType)
        }
    }

    @Test
    fun `loadPending populates state with selected variant text`() = runTest(testDispatcher) {
        stubLiveDraft()

        val viewModel = createViewModel()
        viewModel.loadPending("pm_1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(MessageDraftId("pm_1"), state.previewDraft?.id)
        assertEquals("standard", state.selectedVariant)
        assertEquals("Wishing you a happy birthday!", state.editedText)
        assertEquals(false, state.isLoading)
        assertTrue(state.errorMessageRes == null)
    }

    @Test
    fun `draft and review queue flow updates immediately update preview state`() = runTest(testDispatcher) {
        val draftFlow = MutableStateFlow(sampleDraft())
        val reviewQueueFlow = MutableStateFlow(
            listOf(
                sampleReviewItem(id = "pm_1", contactId = "c_1"),
            ),
        )
        every { messageRepository.getWishPreviewDraftByRef("pm_1") } returns draftFlow
        every { messageRepository.getWishPreviewReviewQueue() } returns reviewQueueFlow

        val viewModel = createViewModel()
        viewModel.loadPending("pm_1")
        advanceUntilIdle()

        assertEquals("Wishing you a happy birthday!", viewModel.uiState.value.editedText)
        assertEquals(0, viewModel.uiState.value.remainingReviewCount)

        draftFlow.value = sampleDraft().copy(
            selectedVariantText = "Updated from database",
            variants = sampleDraft().variants.copy(standard = "Updated from database"),
        )
        reviewQueueFlow.value = listOf(
            sampleReviewItem(id = "pm_1", contactId = "c_1"),
            sampleReviewItem(id = "pm_2", contactId = "c_2", scheduledForMs = 1_700_000_100_000L),
        )
        advanceUntilIdle()

        assertEquals("Updated from database", viewModel.uiState.value.editedText)
        assertEquals(ReviewNextTarget(contactId = "c_2", messageRef = "pm_2"), viewModel.uiState.value.nextReviewTarget)
        assertEquals(1, viewModel.uiState.value.remainingReviewCount)
    }

    @Test
    fun `loadPending exposes approval plan summary`() = runTest(testDispatcher) {
        stubLiveDraft(
            draft = sampleDraft().copy(
                channel = MessageChannel.EMAIL,
                approvalMode = ApprovalMode.SMART_APPROVE,
                isUsingFallback = true,
            ),
            eventType = OccasionType.ANNIVERSARY,
        )

        val viewModel = createViewModel()
        viewModel.loadPending("pm_1")
        advanceUntilIdle()

        val summary = viewModel.uiState.value.sendSummary
        assertEquals("ANNIVERSARY", summary?.eventType)
        assertEquals(MessageChannel.EMAIL.raw, summary?.channel)
        assertEquals("SMART_APPROVE", summary?.approvalMode)
        assertEquals(true, summary?.usesFallback)
    }

    @Test
    fun `loadPending exposes next pending review target`() = runTest(testDispatcher) {
        val current = sampleReviewItem(
            id = "pm_1",
            contactId = "c_1",
            scheduledForMs = 1_700_000_000_000L,
        )
        val next = sampleReviewItem(
            id = "pm_2",
            contactId = "c_2",
            scheduledForMs = 1_700_000_100_000L,
        )
        val ignoredApproved = sampleReviewItem(
            id = "pm_3",
            contactId = "c_3",
            scheduledForMs = 1_700_000_050_000L,
            status = MessageStatus.APPROVED,
        )
        stubLiveDraft(reviewQueue = listOf(current, ignoredApproved, next))

        val viewModel = createViewModel()
        viewModel.loadPending("pm_1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ReviewNextTarget(contactId = "c_2", messageRef = "pm_2"), state.nextReviewTarget)
        assertEquals(1, state.remainingReviewCount)
    }

    @Test
    fun `loadPending uses count-only context signals`() = runTest(testDispatcher) {
        stubLiveDraft(memoryCount = 3, giftCount = 2)

        val viewModel = createViewModel()
        viewModel.loadPending("pm_1")
        advanceUntilIdle()

        val signals = viewModel.uiState.value.whySignals.associate { it.labelRes to it.value }
        assertEquals("3", signals[R.string.wish_why_memories])
        assertEquals("2", signals[R.string.wish_why_gifts])
    }

    @Test
    fun `loadPending uses pure contact wish context for why signals`() = runTest(testDispatcher) {
        stubLiveDraft(
            contact = ContactWishContext(
                id = ContactId("c_1"),
                relationshipType = "FAMILY",
                preferredLanguage = "hi",
            ),
        )

        val viewModel = createViewModel()
        viewModel.loadPending("pm_1")
        advanceUntilIdle()

        val signals = viewModel.uiState.value.whySignals.associate { it.labelRes to it.value }
        assertEquals("FAMILY", signals[R.string.wish_why_relationship])
        assertEquals("hi", signals[R.string.wish_why_language])
    }

    @Test
    fun `loadPending surfaces error when message is missing`() = runTest(testDispatcher) {
        stubLiveDraft(messageRef = "missing", draft = null)

        val viewModel = createViewModel()
        viewModel.loadPending("missing")
        advanceUntilIdle()

        assertEquals(R.string.wish_preview_error_message_not_found, viewModel.uiState.value.errorMessageRes)
        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    @Test
    fun `loadPending falls back to event id for legacy preview routes`() = runTest(testDispatcher) {
        stubLiveDraft(messageRef = "e_1", draft = sampleDraft())

        val viewModel = createViewModel()
        viewModel.loadPending("e_1")
        advanceUntilIdle()

        assertEquals(MessageDraftId("pm_1"), viewModel.uiState.value.previewDraft?.id)
        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    @Test
    fun `selectVariant swaps edited text to that variant`() = runTest(testDispatcher) {
        stubLiveDraft()

        val viewModel = createViewModel()
        viewModel.loadPending("pm_1")
        advanceUntilIdle()

        viewModel.selectVariant("funny")
        advanceUntilIdle()

        assertEquals("funny", viewModel.uiState.value.selectedVariant)
        assertEquals("Another year older, still awesome!", viewModel.uiState.value.editedText)
    }

    @Test
    fun `updateEditedText sets the local draft text`() = runTest(testDispatcher) {
        stubLiveDraft()

        val viewModel = createViewModel()
        viewModel.loadPending("pm_1")
        advanceUntilIdle()

        viewModel.updateEditedText("Custom draft text")
        assertEquals("Custom draft text", viewModel.uiState.value.editedText)
        assertEquals(WishDraftReadiness.EDITED_READY, viewModel.uiState.value.draftReadiness)
    }

    @Test
    fun `blank edit recalculates readiness and blocks approval`() = runTest(testDispatcher) {
        stubLiveDraft()

        val viewModel = createViewModel()
        viewModel.loadPending("pm_1")
        advanceUntilIdle()

        viewModel.updateEditedText("   ")
        viewModel.approve()
        advanceUntilIdle()

        assertEquals(WishDraftReadiness.BLANK, viewModel.uiState.value.draftReadiness)
        assertEquals(R.string.wish_preview_readiness_blank, viewModel.uiState.value.errorMessageRes)
        assertEquals(false, viewModel.uiState.value.approved)
        coVerify(exactly = 0) { approvePendingMessageUseCase(any(), any()) }
    }

    @Test
    fun `short edit recalculates readiness and blocks approval`() = runTest(testDispatcher) {
        stubLiveDraft()

        val viewModel = createViewModel()
        viewModel.loadPending("pm_1")
        advanceUntilIdle()

        viewModel.updateEditedText("Too short")
        viewModel.approve()
        advanceUntilIdle()

        assertEquals(WishDraftReadiness.TOO_SHORT, viewModel.uiState.value.draftReadiness)
        assertEquals(R.string.wish_preview_readiness_short, viewModel.uiState.value.errorMessageRes)
        assertEquals(false, viewModel.uiState.value.approved)
        coVerify(exactly = 0) { approvePendingMessageUseCase(any(), any()) }
    }

    @Test
    fun `sendTestToMyself surfaces localized success feedback`() = runTest(testDispatcher) {
        stubLiveDraft()
        coEvery { testSendUseCase("Wishing you a happy birthday!") } returns TestSendUseCase.Outcome.Sent

        val viewModel = createViewModel()
        viewModel.loadPending("pm_1")
        advanceUntilIdle()

        viewModel.sendTestToMyself()
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isTestingSend)
        assertEquals(R.string.wish_preview_test_sent, viewModel.uiState.value.feedbackEvent?.message?.let {
            (it as com.example.ui.feedback.UiText.Resource).resId
        })
    }

    @Test
    fun `sendTestToMyself surfaces missing email setup feedback`() = runTest(testDispatcher) {
        stubLiveDraft()
        coEvery { testSendUseCase(any()) } returns TestSendUseCase.Outcome.MissingEmailSetup

        val viewModel = createViewModel()
        viewModel.loadPending("pm_1")
        advanceUntilIdle()

        viewModel.sendTestToMyself()
        advanceUntilIdle()

        assertEquals(R.string.wish_preview_test_missing_email, viewModel.uiState.value.feedbackEvent?.message?.let {
            (it as com.example.ui.feedback.UiText.Resource).resId
        })
    }

    @Test
    fun `regenerate refreshes pending draft and quality message`() = runTest(testDispatcher) {
        val regenerated = sampleDraft().copy(
            variants = sampleDraft().variants.copy(standard = "Fresh AI draft"),
            selectedVariantText = "Fresh AI draft",
        )
        val draftFlow = MutableStateFlow(sampleDraft())
        every { messageRepository.getWishPreviewDraftByRef("pm_1") } returns draftFlow
        coEvery {
            regeneratePendingMessageUseCase("pm_1", "Wishing you a happy birthday!", null)
        } returns RegeneratePendingMessageUseCase.Outcome.Regenerated("pm_1", usedFallback = false)

        val viewModel = createViewModel()
        viewModel.loadPending("pm_1")
        advanceUntilIdle()

        viewModel.regenerate()
        advanceUntilIdle()
        draftFlow.value = regenerated
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Fresh AI draft", state.editedText)
        assertEquals(R.string.wish_preview_quality_regenerated, state.qualityMessageRes)
        assertEquals(null, state.qualityMessageArgRes)
        assertEquals(false, state.isRegenerating)
    }

    @Test
    fun `submitFeedback records activity and passes instruction into regenerate`() = runTest(testDispatcher) {
        val regenerated = sampleDraft().copy(
            variants = sampleDraft().variants.copy(standard = "Personal fresh draft"),
            selectedVariantText = "Personal fresh draft",
        )
        val draftFlow = MutableStateFlow(sampleDraft())
        every { messageRepository.getWishPreviewDraftByRef("pm_1") } returns draftFlow
        coEvery {
            regeneratePendingMessageUseCase("pm_1", "Wishing you a happy birthday!", match { it?.contains("more personal") == true })
        } returns RegeneratePendingMessageUseCase.Outcome.Regenerated("pm_1", usedFallback = false)

        val viewModel = createViewModel()
        viewModel.loadPending("pm_1")
        advanceUntilIdle()

        viewModel.submitFeedback("too_generic")
        advanceUntilIdle()
        viewModel.regenerate()
        advanceUntilIdle()
        draftFlow.value = regenerated
        advanceUntilIdle()

        assertEquals("too_generic", viewModel.uiState.value.selectedFeedbackKey)
        assertEquals(R.string.wish_preview_quality_regenerated_with_feedback, viewModel.uiState.value.qualityMessageRes)
        assertEquals(R.string.wish_feedback_too_generic, viewModel.uiState.value.qualityMessageArgRes)
        coVerify {
            messageFeedbackRepository.record(
                match {
                    it.pendingMessageId == MessageDraftId("pm_1") &&
                        it.contactId == ContactId("c_1") &&
                        it.occasionId == OccasionId("e_1") &&
                        it.reasonKey == "too_generic" &&
                        it.instruction.contains("more personal")
                }
            )
        }
        coVerify { activityLogRepository.record(match { it.type == "AI" && it.messageId == "pm_1" }) }
    }

    @Test
    fun `regenerate marks matching feedback record as applied`() = runTest(testDispatcher) {
        stubLiveDraft()
        coEvery {
            regeneratePendingMessageUseCase("pm_1", "Wishing you a happy birthday!", match { it?.contains("more personal") == true })
        } returns RegeneratePendingMessageUseCase.Outcome.Regenerated("pm_1", usedFallback = false)
        coEvery { messageFeedbackRepository.getLatestForPendingMessage(MessageDraftId("pm_1")) } returns MessageFeedbackRecord(
            id = MessageFeedbackId("feedback_1"),
            pendingMessageId = MessageDraftId("pm_1"),
            contactId = ContactId("c_1"),
            occasionId = OccasionId("e_1"),
            reasonKey = "too_generic",
            instruction = "Make it more personal.",
            draftText = "Wishing you a happy birthday!",
            createdAtMs = 1_700_000_000_000L,
        )

        val viewModel = createViewModel()
        viewModel.loadPending("pm_1")
        advanceUntilIdle()

        viewModel.submitFeedback("too_generic")
        advanceUntilIdle()
        viewModel.regenerate()
        advanceUntilIdle()

        coVerify { messageFeedbackRepository.markApplied(MessageFeedbackId("feedback_1")) }
    }

    @Test
    fun `approve invokes use case and flips approved flag on success`() = runTest(testDispatcher) {
        stubLiveDraft()
        coEvery { approvePendingMessageUseCase("pm_1", any()) } returns
            ApprovePendingMessageUseCase.ApprovalOutcome.Approved("pm_1", ApprovalMode.VIP_APPROVE)

        val viewModel = createViewModel()
        viewModel.loadPending("pm_1")
        advanceUntilIdle()

        viewModel.approve()
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.approved)
        assertEquals(false, viewModel.uiState.value.isApproving)
    }

    @Test
    fun `approve surfaces error when pending missing`() = runTest(testDispatcher) {
        stubLiveDraft()
        coEvery { approvePendingMessageUseCase("pm_1", any()) } returns ApprovePendingMessageUseCase.ApprovalOutcome.PendingNotFound

        val viewModel = createViewModel()
        viewModel.loadPending("pm_1")
        advanceUntilIdle()

        viewModel.approve()
        advanceUntilIdle()

        assertEquals(R.string.wish_preview_error_message_not_found, viewModel.uiState.value.errorMessageRes)
        assertEquals(false, viewModel.uiState.value.isApproving)
        assertEquals(false, viewModel.uiState.value.approved)
    }

    @Test
    fun `reject invokes use case and flips rejected flag on success`() = runTest(testDispatcher) {
        stubLiveDraft()
        coEvery { rejectPendingMessageUseCase("pm_1") } returns RejectPendingMessageUseCase.RejectionOutcome.Rejected("pm_1")

        val viewModel = createViewModel()
        viewModel.loadPending("pm_1")
        advanceUntilIdle()

        viewModel.reject()
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.rejected)
        assertEquals(false, viewModel.uiState.value.isRejecting)
    }
}
