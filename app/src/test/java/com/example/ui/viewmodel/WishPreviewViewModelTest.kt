package com.example.ui.viewmodel

import com.example.R
import com.example.core.db.entities.PendingMessageEntity
import com.example.domain.repository.ActivityLogRepository
import com.example.domain.repository.ContactRepository
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
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        coEvery { contactRepository.getById(any()) } returns null
        coEvery { memoryNoteRepository.getByContact(any()) } returns emptyList()
        coEvery { giftHistoryRepository.getByContact(any()) } returns emptyList()
        coEvery { messageRepository.getSentByContact(any(), any()) } returns emptyList()
        coEvery { messageFeedbackRepository.getLatestForPendingMessage(any()) } returns null
        coEvery { testSendUseCase(any()) } returns TestSendUseCase.Outcome.Sent
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun samplePending(): PendingMessageEntity = PendingMessageEntity(
        id = "pm_1",
        contactId = "c_1",
        eventId = "e_1",
        shortVariant = "Happy B'day!",
        standardVariant = "Wishing you a happy birthday!",
        longVariant = "On this special day, I wish you all the best.",
        formalVariant = "Wishing you a very happy birthday, dear friend.",
        funnyVariant = "Another year older, still awesome!",
        emotionalVariant = "You mean the world to me. Happy birthday!",
        selectedVariant = "standard",
        selectedVariantText = "Wishing you a happy birthday!",
        channel = "SMS",
        scheduledForMs = 1_700_000_000_000L,
        approvalMode = "VIP_APPROVE",
        status = "PENDING"
    )

    private fun createViewModel() = WishPreviewViewModel(
        messageRepository = messageRepository,
        activityLogRepository = activityLogRepository,
        messageFeedbackRepository = messageFeedbackRepository,
        contactRepository = contactRepository,
        memoryNoteRepository = memoryNoteRepository,
        giftHistoryRepository = giftHistoryRepository,
        approvePendingMessageUseCase = approvePendingMessageUseCase,
        rejectPendingMessageUseCase = rejectPendingMessageUseCase,
        regeneratePendingMessageUseCase = regeneratePendingMessageUseCase,
        testSendUseCase = testSendUseCase,
    )

    @Test
    fun `loadPending populates state with selected variant text`() = runTest(testDispatcher) {
        coEvery { messageRepository.getPendingById("pm_1") } returns samplePending()

        val viewModel = createViewModel()
        viewModel.loadPending("pm_1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("pm_1", state.pendingMessage?.id)
        assertEquals("standard", state.selectedVariant)
        assertEquals("Wishing you a happy birthday!", state.editedText)
        assertEquals(false, state.isLoading)
        assertTrue(state.errorMessageRes == null)
    }

    @Test
    fun `loadPending surfaces error when message is missing`() = runTest(testDispatcher) {
        coEvery { messageRepository.getPendingById("missing") } returns null
        coEvery { messageRepository.getPendingByEventId("missing") } returns null

        val viewModel = createViewModel()
        viewModel.loadPending("missing")
        advanceUntilIdle()

        assertEquals(R.string.wish_preview_error_message_not_found, viewModel.uiState.value.errorMessageRes)
        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    @Test
    fun `loadPending falls back to event id for legacy preview routes`() = runTest(testDispatcher) {
        coEvery { messageRepository.getPendingById("e_1") } returns null
        coEvery { messageRepository.getPendingByEventId("e_1") } returns samplePending()

        val viewModel = createViewModel()
        viewModel.loadPending("e_1")
        advanceUntilIdle()

        assertEquals("pm_1", viewModel.uiState.value.pendingMessage?.id)
        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    @Test
    fun `selectVariant swaps edited text to that variant`() = runTest(testDispatcher) {
        coEvery { messageRepository.getPendingById("pm_1") } returns samplePending()

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
        coEvery { messageRepository.getPendingById("pm_1") } returns samplePending()

        val viewModel = createViewModel()
        viewModel.loadPending("pm_1")
        advanceUntilIdle()

        viewModel.updateEditedText("Custom draft text")
        assertEquals("Custom draft text", viewModel.uiState.value.editedText)
    }

    @Test
    fun `sendTestToMyself surfaces localized success feedback`() = runTest(testDispatcher) {
        coEvery { messageRepository.getPendingById("pm_1") } returns samplePending()
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
        coEvery { messageRepository.getPendingById("pm_1") } returns samplePending()
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
        val regenerated = samplePending().copy(
            standardVariant = "Fresh AI draft",
            selectedVariantText = "Fresh AI draft",
        )
        coEvery { messageRepository.getPendingById("pm_1") } returns samplePending() andThen regenerated
        coEvery {
            regeneratePendingMessageUseCase("pm_1", "Wishing you a happy birthday!", null)
        } returns RegeneratePendingMessageUseCase.Outcome.Regenerated("pm_1", usedFallback = false)

        val viewModel = createViewModel()
        viewModel.loadPending("pm_1")
        advanceUntilIdle()

        viewModel.regenerate()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Fresh AI draft", state.editedText)
        assertEquals(R.string.wish_preview_quality_regenerated, state.qualityMessageRes)
        assertEquals(null, state.qualityMessageArgRes)
        assertEquals(false, state.isRegenerating)
    }

    @Test
    fun `submitFeedback records activity and passes instruction into regenerate`() = runTest(testDispatcher) {
        val regenerated = samplePending().copy(
            standardVariant = "Personal fresh draft",
            selectedVariantText = "Personal fresh draft",
        )
        coEvery { messageRepository.getPendingById("pm_1") } returns samplePending() andThen regenerated
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

        assertEquals("too_generic", viewModel.uiState.value.selectedFeedbackKey)
        assertEquals(R.string.wish_preview_quality_regenerated_with_feedback, viewModel.uiState.value.qualityMessageRes)
        assertEquals(R.string.wish_feedback_too_generic, viewModel.uiState.value.qualityMessageArgRes)
        coVerify { activityLogRepository.record(match { it.type == "AI" && it.messageId == "pm_1" }) }
    }

    @Test
    fun `approve invokes use case and flips approved flag on success`() = runTest(testDispatcher) {
        coEvery { messageRepository.getPendingById("pm_1") } returns samplePending()
        coEvery { approvePendingMessageUseCase("pm_1", any()) } returns ApprovePendingMessageUseCase.ApprovalOutcome.Approved("pm_1", "VIP_APPROVE")

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
        coEvery { messageRepository.getPendingById("pm_1") } returns samplePending()
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
        coEvery { messageRepository.getPendingById("pm_1") } returns samplePending()
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
