package com.example.ui.viewmodel

import com.example.core.db.entities.PendingMessageEntity
import com.example.domain.repository.MessageRepository
import com.example.domain.usecase.ApprovePendingMessageUseCase
import com.example.domain.usecase.RejectPendingMessageUseCase
import io.mockk.coEvery
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private lateinit var approvePendingMessageUseCase: ApprovePendingMessageUseCase

    @RelaxedMockK
    private lateinit var rejectPendingMessageUseCase: RejectPendingMessageUseCase

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
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

    @Test
    fun `loadPending populates state with selected variant text`() = runTest(testDispatcher) {
        coEvery { messageRepository.getAllPending() } returns flowOf(listOf(samplePending()))

        val viewModel = WishPreviewViewModel(messageRepository, approvePendingMessageUseCase, rejectPendingMessageUseCase)
        viewModel.loadPending("pm_1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("pm_1", state.pendingMessage?.id)
        assertEquals("standard", state.selectedVariant)
        assertEquals("Wishing you a happy birthday!", state.editedText)
        assertEquals(false, state.isLoading)
        assertTrue(state.error == null)
    }

    @Test
    fun `loadPending surfaces error when message is missing`() = runTest(testDispatcher) {
        coEvery { messageRepository.getAllPending() } returns flowOf(emptyList())

        val viewModel = WishPreviewViewModel(messageRepository, approvePendingMessageUseCase, rejectPendingMessageUseCase)
        viewModel.loadPending("missing")
        advanceUntilIdle()

        assertEquals("Message not found.", viewModel.uiState.value.error)
        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    @Test
    fun `selectVariant swaps edited text to that variant`() = runTest(testDispatcher) {
        coEvery { messageRepository.getAllPending() } returns flowOf(listOf(samplePending()))

        val viewModel = WishPreviewViewModel(messageRepository, approvePendingMessageUseCase, rejectPendingMessageUseCase)
        viewModel.loadPending("pm_1")
        advanceUntilIdle()

        viewModel.selectVariant("funny")
        advanceUntilIdle()

        assertEquals("funny", viewModel.uiState.value.selectedVariant)
        assertEquals("Another year older, still awesome!", viewModel.uiState.value.editedText)
    }

    @Test
    fun `updateEditedText sets the local draft text`() = runTest(testDispatcher) {
        coEvery { messageRepository.getAllPending() } returns flowOf(listOf(samplePending()))

        val viewModel = WishPreviewViewModel(messageRepository, approvePendingMessageUseCase, rejectPendingMessageUseCase)
        viewModel.loadPending("pm_1")
        advanceUntilIdle()

        viewModel.updateEditedText("Custom draft text")
        assertEquals("Custom draft text", viewModel.uiState.value.editedText)
    }

    @Test
    fun `approve invokes use case and flips approved flag on success`() = runTest(testDispatcher) {
        coEvery { messageRepository.getAllPending() } returns flowOf(listOf(samplePending()))
        coEvery { approvePendingMessageUseCase("pm_1", any()) } returns ApprovePendingMessageUseCase.ApprovalOutcome.Approved("pm_1", "VIP_APPROVE")

        val viewModel = WishPreviewViewModel(messageRepository, approvePendingMessageUseCase, rejectPendingMessageUseCase)
        viewModel.loadPending("pm_1")
        advanceUntilIdle()

        viewModel.approve()
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.approved)
        assertEquals(false, viewModel.uiState.value.isApproving)
    }

    @Test
    fun `approve surfaces error when pending missing`() = runTest(testDispatcher) {
        coEvery { messageRepository.getAllPending() } returns flowOf(listOf(samplePending()))
        coEvery { approvePendingMessageUseCase("pm_1", any()) } returns ApprovePendingMessageUseCase.ApprovalOutcome.PendingNotFound

        val viewModel = WishPreviewViewModel(messageRepository, approvePendingMessageUseCase, rejectPendingMessageUseCase)
        viewModel.loadPending("pm_1")
        advanceUntilIdle()

        viewModel.approve()
        advanceUntilIdle()

        assertEquals("Message not found.", viewModel.uiState.value.error)
        assertEquals(false, viewModel.uiState.value.isApproving)
        assertEquals(false, viewModel.uiState.value.approved)
    }

    @Test
    fun `reject invokes use case and flips rejected flag on success`() = runTest(testDispatcher) {
        coEvery { messageRepository.getAllPending() } returns flowOf(listOf(samplePending()))
        coEvery { rejectPendingMessageUseCase("pm_1") } returns RejectPendingMessageUseCase.RejectionOutcome.Rejected("pm_1")

        val viewModel = WishPreviewViewModel(messageRepository, approvePendingMessageUseCase, rejectPendingMessageUseCase)
        viewModel.loadPending("pm_1")
        advanceUntilIdle()

        viewModel.reject()
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.rejected)
        assertEquals(false, viewModel.uiState.value.isRejecting)
    }
}
