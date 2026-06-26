package com.example.ui.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.EventEntity
import com.example.core.db.entities.PendingMessageEntity
import com.example.core.prefs.SecurePrefs
import com.example.domain.model.MessageChannel
import com.example.domain.repository.ActivityLogRepository
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.repository.MessageRepository
import com.example.domain.model.MessageStatus
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
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MessagesViewModelTest {
    private val messageRepository: MessageRepository = mockk(relaxed = true)
    private val contactRepository: ContactRepository = mockk(relaxed = true)
    private val eventRepository: EventRepository = mockk(relaxed = true)
    private val approvePendingMessageUseCase: ApprovePendingMessageUseCase = mockk(relaxed = true)
    private val rejectPendingMessageUseCase: RejectPendingMessageUseCase = mockk(relaxed = true)
    private val revokeApprovalUseCase: RevokeApprovalUseCase = mockk(relaxed = true)
    private val schedulerService: SchedulerService = mockk(relaxed = true)
    private val activityLogRepository: ActivityLogRepository = mockk(relaxed = true)
    private val securePrefs: SecurePrefs = mockk(relaxed = true)
    private val dispatcher = StandardTestDispatcher()
    private lateinit var context: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        every { messageRepository.getAllPending() } returns MutableStateFlow(emptyList())
        every { messageRepository.getAllSent() } returns MutableStateFlow(emptyList())
        every { contactRepository.getAll() } returns MutableStateFlow(emptyList())
        every { eventRepository.getAll() } returns MutableStateFlow(emptyList())
        every { securePrefs.getChannelBlackout() } returns "[]"
        every { securePrefs.getSenderEmail() } returns "sender@example.com"
        every { securePrefs.getSenderEmailPassword() } returns "app-password"
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
        channel = MessageChannel.SMS.raw,
        scheduledForMs = 1_700_000_000_000L,
        approvalMode = "MANUAL",
        status = MessageStatus.FAILED.raw,
    )

    private fun pending(
        id: String,
        contactId: String,
        eventId: String,
        channel: String,
        scheduledForMs: Long,
        status: String = MessageStatus.PENDING.raw,
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
        status = status,
    )

    @Test
    fun `bulkRetrySelected approves failed messages and schedules by pending id`() = runTest(dispatcher) {
        val failed = failedPending("pm_1")
        coEvery { messageRepository.getPendingById("pm_1") } returns failed

        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.toggleSelection("pm_1")
        viewModel.bulkRetrySelected()
        advanceUntilIdle()

        coVerify {
            messageRepository.insertPending(match {
                it.id == "pm_1" && it.status == MessageStatus.APPROVED.raw
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
                pending("pm_1", "c_1", "e_1", MessageChannel.SMS.raw, now + 2 * 86_400_000L),
                pending("pm_2", "c_2", "e_2", MessageChannel.EMAIL.raw, now + 3 * 86_400_000L),
            )
        )
        every { contactRepository.getAll() } returns MutableStateFlow(
            listOf(
                ContactEntity(id = "c_1", name = "Alice", primaryPhone = "+919999900000"),
                ContactEntity(id = "c_2", name = "Bob", primaryEmail = "bob@example.com"),
            )
        )
        every { eventRepository.getAll() } returns MutableStateFlow(
            listOf(
                EventEntity(id = "e_1", contactId = "c_1", type = "BIRTHDAY", dayOfMonth = 1, month = 1, nextOccurrenceMs = now),
                EventEntity(id = "e_2", contactId = "c_2", type = "ANNIVERSARY", dayOfMonth = 2, month = 1, nextOccurrenceMs = now),
            )
        )

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(listOf("pm_1", "pm_2"), viewModel.uiState.value.needsReviewMessages.map { it.entity.id })

        viewModel.selectChannelFilter(MessageChannelFilter.EMAIL)
        assertEquals(listOf("pm_2"), viewModel.uiState.value.needsReviewMessages.map { it.entity.id })

        viewModel.selectChannelFilter(MessageChannelFilter.ALL)
        viewModel.updateSearchQuery("alice")
        assertEquals(listOf("pm_1"), viewModel.uiState.value.needsReviewMessages.map { it.entity.id })

        viewModel.updateSearchQuery("")
        viewModel.selectSort(MessageSort.SCHEDULED_DESC)
        assertEquals(listOf("pm_2", "pm_1"), viewModel.uiState.value.needsReviewMessages.map { it.entity.id })
    }

    @Test
    fun `pending messages expose readiness labels from channel prerequisites`() = runTest(dispatcher) {
        val now = System.currentTimeMillis()
        every { securePrefs.getChannelBlackout() } returns "[\"WHATSAPP\"]"
        every { securePrefs.getSenderEmail() } returns ""
        every { securePrefs.getSenderEmailPassword() } returns ""
        every { messageRepository.getAllPending() } returns MutableStateFlow(
            listOf(
                pending("pm_sms", "c_sms", "e_sms", MessageChannel.SMS.raw, now + 2 * 86_400_000L),
                pending("pm_email", "c_email", "e_email", MessageChannel.EMAIL.raw, now + 3 * 86_400_000L),
                pending("pm_whatsapp", "c_whatsapp", "e_whatsapp", MessageChannel.WHATSAPP.raw, now + 4 * 86_400_000L),
            )
        )
        every { contactRepository.getAll() } returns MutableStateFlow(
            listOf(
                ContactEntity(id = "c_sms", name = "No Phone"),
                ContactEntity(id = "c_email", name = "No Gmail", primaryEmail = "no-gmail@example.com"),
                ContactEntity(id = "c_whatsapp", name = "Blocked WA", primaryPhone = "+919999900000"),
            )
        )
        every { eventRepository.getAll() } returns MutableStateFlow(
            listOf(
                EventEntity(id = "e_sms", contactId = "c_sms", type = "BIRTHDAY", dayOfMonth = 1, month = 1, nextOccurrenceMs = now),
                EventEntity(id = "e_email", contactId = "c_email", type = "BIRTHDAY", dayOfMonth = 1, month = 1, nextOccurrenceMs = now),
                EventEntity(id = "e_whatsapp", contactId = "c_whatsapp", type = "BIRTHDAY", dayOfMonth = 1, month = 1, nextOccurrenceMs = now),
            )
        )

        val viewModel = newViewModel()
        advanceUntilIdle()

        val readinessById = viewModel.uiState.value.blockedMessages.associate {
            it.entity.id to it.readiness
        }
        assertEquals(MessageReadiness.MISSING_PHONE, readinessById["pm_sms"])
        assertEquals(MessageReadiness.EMAIL_SETUP_MISSING, readinessById["pm_email"])
        assertEquals(MessageReadiness.CHANNEL_DISABLED, readinessById["pm_whatsapp"])
    }

    @Test
    fun `messages are split into task-state buckets`() = runTest(dispatcher) {
        val now = System.currentTimeMillis()
        every { messageRepository.getAllPending() } returns MutableStateFlow(
            listOf(
                pending("needs_review", "c_ready", "e_ready", MessageChannel.SMS.raw, now + 2 * 86_400_000L),
                pending(
                    "scheduled",
                    "c_ready",
                    "e_scheduled",
                    MessageChannel.SMS.raw,
                    now + 3 * 86_400_000L,
                    status = MessageStatus.APPROVED.raw,
                ),
                pending("blocked", "c_blocked", "e_blocked", MessageChannel.EMAIL.raw, now + 4 * 86_400_000L),
                failedPending("failed"),
            )
        )
        every { contactRepository.getAll() } returns MutableStateFlow(
            listOf(
                ContactEntity(id = "c_ready", name = "Ready", primaryPhone = "+919999900000"),
                ContactEntity(id = "c_blocked", name = "Blocked"),
            )
        )
        every { eventRepository.getAll() } returns MutableStateFlow(
            listOf(
                EventEntity(id = "e_ready", contactId = "c_ready", type = "BIRTHDAY", dayOfMonth = 1, month = 1, nextOccurrenceMs = now),
                EventEntity(id = "e_scheduled", contactId = "c_ready", type = "BIRTHDAY", dayOfMonth = 1, month = 1, nextOccurrenceMs = now),
                EventEntity(id = "e_blocked", contactId = "c_blocked", type = "BIRTHDAY", dayOfMonth = 1, month = 1, nextOccurrenceMs = now),
                EventEntity(id = "e_1", contactId = "c_1", type = "BIRTHDAY", dayOfMonth = 1, month = 1, nextOccurrenceMs = now),
            )
        )

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(listOf("needs_review"), viewModel.uiState.value.needsReviewMessages.map { it.entity.id })
        assertEquals(listOf("scheduled"), viewModel.uiState.value.scheduledMessages.map { it.entity.id })
        assertEquals(listOf("blocked"), viewModel.uiState.value.blockedMessages.map { it.entity.id })
        assertEquals(listOf("failed"), viewModel.uiState.value.failedMessages.map { it.entity.id })
    }

    private fun newViewModel(): MessagesViewModel {
        return MessagesViewModel(
            appContext = context,
            messageRepository = messageRepository,
            contactRepository = contactRepository,
            eventRepository = eventRepository,
            approvePendingMessageUseCase = approvePendingMessageUseCase,
            rejectPendingMessageUseCase = rejectPendingMessageUseCase,
            revokeApprovalUseCase = revokeApprovalUseCase,
            schedulerService = schedulerService,
            activityLogRepository = activityLogRepository,
            securePrefs = securePrefs,
        )
    }
}
