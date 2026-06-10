package com.example.ui.viewmodel

import com.example.core.db.entities.PendingMessageEntity
import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.EventEntity
import com.example.domain.repository.ActivityLogRepository
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.repository.MessageRepository
import com.example.domain.service.SchedulerService
import com.example.domain.usecase.ApprovePendingMessageUseCase
import com.example.domain.usecase.RejectPendingMessageUseCase
import com.example.domain.usecase.RevokeApprovalUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
class MessagesViewModelTest {
    private val messageRepository: MessageRepository = mockk(relaxed = true)
    private val contactRepository: ContactRepository = mockk(relaxed = true)
    private val eventRepository: EventRepository = mockk(relaxed = true)
    private val approvePendingMessageUseCase: ApprovePendingMessageUseCase = mockk(relaxed = true)
    private val rejectPendingMessageUseCase: RejectPendingMessageUseCase = mockk(relaxed = true)
    private val revokeApprovalUseCase: RevokeApprovalUseCase = mockk(relaxed = true)
    private val schedulerService: SchedulerService = mockk(relaxed = true)
    private val activityLogRepository: ActivityLogRepository = mockk(relaxed = true)
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { messageRepository.getAllPending() } returns MutableStateFlow(emptyList())
        every { messageRepository.getAllSent() } returns MutableStateFlow(emptyList())
        every { contactRepository.getAll() } returns MutableStateFlow(emptyList())
        every { eventRepository.getAll() } returns MutableStateFlow(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun failedPending(id: String) = PendingMessageEntity(
        id = id,
        contactId = "c_1",
        eventId = "e_1",
        shortVariant = "short",
        standardVariant = "standard",
        longVariant = "long",
        formalVariant = "formal",
        funnyVariant = "funny",
        emotionalVariant = "emotional",
        selectedVariant = "standard",
        selectedVariantText = "standard",
        channel = "SMS",
        scheduledForMs = 1_700_000_000_000L,
        approvalMode = "MANUAL",
        status = "FAILED",
    )

    private fun pending(
        id: String,
        contactId: String,
        eventId: String,
        channel: String,
        scheduledForMs: Long,
    ) = PendingMessageEntity(
        id = id,
        contactId = contactId,
        eventId = eventId,
        shortVariant = "short",
        standardVariant = "standard $contactId",
        longVariant = "long",
        formalVariant = "formal",
        funnyVariant = "funny",
        emotionalVariant = "emotional",
        selectedVariant = "standard",
        selectedVariantText = "standard $contactId",
        channel = channel,
        scheduledForMs = scheduledForMs,
        approvalMode = "MANUAL",
    )

    @Test
    fun `bulkRetrySelected approves failed messages and schedules by pending id`() = runTest(dispatcher) {
        val failed = failedPending("pm_1")
        coEvery { messageRepository.getPendingById("pm_1") } returns failed

        val viewModel = MessagesViewModel(
            messageRepository = messageRepository,
            contactRepository = contactRepository,
            eventRepository = eventRepository,
            approvePendingMessageUseCase = approvePendingMessageUseCase,
            rejectPendingMessageUseCase = rejectPendingMessageUseCase,
            revokeApprovalUseCase = revokeApprovalUseCase,
            schedulerService = schedulerService,
            activityLogRepository = activityLogRepository,
        )
        advanceUntilIdle()

        viewModel.toggleSelection("pm_1")
        viewModel.bulkRetrySelected()
        advanceUntilIdle()

        coVerify {
            messageRepository.insertPending(match {
                it.id == "pm_1" && it.status == "APPROVED"
            })
        }
        verify { schedulerService.scheduleExactSend("pm_1") }
        assertEquals(emptySet<String>(), viewModel.uiState.value.selectedMessageIds)
    }

    @Test
    fun `search channel filter and sort are applied in viewmodel`() = runTest(dispatcher) {
        val now = System.currentTimeMillis()
        every { messageRepository.getAllPending() } returns MutableStateFlow(
            listOf(
                pending("pm_1", "c_1", "e_1", "SMS", now + 2 * 86_400_000L),
                pending("pm_2", "c_2", "e_2", "EMAIL", now + 3 * 86_400_000L),
            )
        )
        every { contactRepository.getAll() } returns MutableStateFlow(
            listOf(
                ContactEntity(id = "c_1", name = "Alice"),
                ContactEntity(id = "c_2", name = "Bob"),
            )
        )
        every { eventRepository.getAll() } returns MutableStateFlow(
            listOf(
                EventEntity(id = "e_1", contactId = "c_1", type = "BIRTHDAY", dayOfMonth = 1, month = 1, nextOccurrenceMs = now),
                EventEntity(id = "e_2", contactId = "c_2", type = "ANNIVERSARY", dayOfMonth = 2, month = 1, nextOccurrenceMs = now),
            )
        )

        val viewModel = MessagesViewModel(
            messageRepository = messageRepository,
            contactRepository = contactRepository,
            eventRepository = eventRepository,
            approvePendingMessageUseCase = approvePendingMessageUseCase,
            rejectPendingMessageUseCase = rejectPendingMessageUseCase,
            revokeApprovalUseCase = revokeApprovalUseCase,
            schedulerService = schedulerService,
            activityLogRepository = activityLogRepository,
        )
        advanceUntilIdle()

        assertEquals(listOf("pm_1", "pm_2"), viewModel.uiState.value.pendingMessages.map { it.entity.id })

        viewModel.selectChannelFilter(MessageChannelFilter.EMAIL)
        assertEquals(listOf("pm_2"), viewModel.uiState.value.pendingMessages.map { it.entity.id })

        viewModel.selectChannelFilter(MessageChannelFilter.ALL)
        viewModel.updateSearchQuery("alice")
        assertEquals(listOf("pm_1"), viewModel.uiState.value.pendingMessages.map { it.entity.id })

        viewModel.updateSearchQuery("")
        viewModel.selectSort(MessageSort.SCHEDULED_DESC)
        assertEquals(listOf("pm_2", "pm_1"), viewModel.uiState.value.pendingMessages.map { it.entity.id })
    }
}
