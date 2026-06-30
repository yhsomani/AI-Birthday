package com.example.ui.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.core.prefs.SecurePrefs
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.contact.ContactMessageContext
import com.example.domain.model.message.PendingMessageListItem
import com.example.domain.model.occasion.EventListItem
import com.example.domain.model.occasion.OccasionType
import com.example.domain.repository.ActivityLogRepository
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.repository.MessageRepository
import com.example.domain.usecase.ApprovePendingMessageUseCase
import com.example.domain.usecase.RejectPendingMessageUseCase
import com.example.domain.usecase.RevokeApprovalUseCase
import com.example.domain.usecase.RetryFailedMessageUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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
    private val retryFailedMessageUseCase: RetryFailedMessageUseCase = mockk(relaxed = true)
    private val activityLogRepository: ActivityLogRepository = mockk(relaxed = true)
    private val securePrefs: SecurePrefs = mockk(relaxed = true)
    private val dispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var preferenceChanges: MutableSharedFlow<Unit>

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        preferenceChanges = MutableSharedFlow(extraBufferCapacity = 1)
        every { messageRepository.getPendingListItems() } returns MutableStateFlow(emptyList())
        every { messageRepository.getSentListItems() } returns MutableStateFlow(emptyList())
        every { contactRepository.getMessageContexts() } returns MutableStateFlow(emptyList())
        every { eventRepository.getEventListItems() } returns MutableStateFlow(emptyList())
        every { securePrefs.observeChanges() } returns preferenceChanges
        every { securePrefs.getChannelBlackout() } returns "[]"
        every { securePrefs.getSenderEmail() } returns "sender@example.com"
        every { securePrefs.getSenderEmailPassword() } returns "app-password"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun failedPending(id: String) = PendingMessageListItem(
        id = MessageDraftId(id),
        contactId = ContactId("c_1"),
        occasionId = OccasionId("e_1"),
        selectedVariantText = "standard",
        standardVariant = "standard",
        channel = MessageChannel.SMS,
        scheduledForMs = 1_700_000_000_000L,
        approvalMode = ApprovalMode.UNKNOWN,
        status = MessageStatus.FAILED,
        editedByUser = false,
        userEditedText = null,
    )

    private fun pending(
        id: String,
        contactId: String,
        eventId: String,
        channel: String,
        scheduledForMs: Long,
        status: String = MessageStatus.PENDING.raw,
    ) = PendingMessageListItem(
        id = MessageDraftId(id),
        contactId = ContactId(contactId),
        occasionId = OccasionId(eventId),
        selectedVariantText = "standard $contactId",
        standardVariant = "standard $contactId",
        channel = MessageChannel.fromRaw(channel),
        scheduledForMs = scheduledForMs,
        approvalMode = ApprovalMode.UNKNOWN,
        status = MessageStatus.fromRaw(status),
        editedByUser = false,
        userEditedText = null,
    )

    private fun contact(
        id: String,
        name: String,
        primaryPhone: String? = null,
        primaryEmail: String? = null,
    ) = ContactMessageContext(
        id = ContactId(id),
        displayName = name,
        avatarUrl = null,
        primaryPhone = primaryPhone,
        primaryEmail = primaryEmail,
    )

    private fun event(
        id: String,
        contactId: String,
        type: OccasionType,
        nextOccurrenceMs: Long,
    ) = EventListItem(
        id = OccasionId(id),
        contactId = ContactId(contactId),
        type = type,
        label = null,
        dayOfMonth = 1,
        month = 1,
        year = null,
        nextOccurrenceMs = nextOccurrenceMs,
        isActive = true,
        notifyDaysBefore = 1,
        source = "CONTACTS",
        confidenceScore = 100,
        isVerified = true,
    )

    @Test
    fun `bulkRetrySelected queues selected failed messages through retry use case`() = runTest(dispatcher) {
        coEvery { retryFailedMessageUseCase("pm_1") } returns RetryFailedMessageUseCase.RetryOutcome.RetryQueued(
            pendingMessageId = "pm_1",
            retryCount = 1,
            previousAttempt = null,
        )

        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.toggleSelection("pm_1")
        viewModel.bulkRetrySelected()
        advanceUntilIdle()

        coVerify { retryFailedMessageUseCase("pm_1") }
        assertEquals(emptySet<String>(), viewModel.uiState.value.selectedMessageIds)
    }

    @Test
    fun `retryMessage surfaces error when retry use case rejects row`() = runTest(dispatcher) {
        coEvery { retryFailedMessageUseCase("pm_1") } returns RetryFailedMessageUseCase.RetryOutcome.NotFailed(
            pendingMessageId = "pm_1",
            status = MessageStatus.APPROVED,
        )

        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.retryMessage("pm_1")
        advanceUntilIdle()

        coVerify { retryFailedMessageUseCase("pm_1") }
        assertEquals(context.getString(com.example.R.string.messages_error_retry), viewModel.uiState.value.error)
        assertEquals(null, viewModel.uiState.value.retryingMessageId)
    }

    @Test
    fun `search channel filter and sort are applied in viewmodel`() = runTest(dispatcher) {
        val now = System.currentTimeMillis()
        every { messageRepository.getPendingListItems() } returns MutableStateFlow(
            listOf(
                pending("pm_1", "c_1", "e_1", MessageChannel.SMS.raw, now + 2 * 86_400_000L),
                pending("pm_2", "c_2", "e_2", MessageChannel.EMAIL.raw, now + 3 * 86_400_000L),
            )
        )
        every { contactRepository.getMessageContexts() } returns MutableStateFlow(
            listOf(
                contact("c_1", "Alice", primaryPhone = "+919999900000"),
                contact("c_2", "Bob", primaryEmail = "bob@example.com"),
            )
        )
        every { eventRepository.getEventListItems() } returns MutableStateFlow(
            listOf(
                event("e_1", "c_1", OccasionType.BIRTHDAY, now),
                event("e_2", "c_2", OccasionType.ANNIVERSARY, now),
            )
        )

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(listOf("pm_1", "pm_2"), viewModel.uiState.value.needsReviewMessages.map { it.id })

        viewModel.selectChannelFilter(MessageChannelFilter.EMAIL)
        assertEquals(listOf("pm_2"), viewModel.uiState.value.needsReviewMessages.map { it.id })

        viewModel.selectChannelFilter(MessageChannelFilter.ALL)
        viewModel.updateSearchQuery("alice")
        assertEquals(listOf("pm_1"), viewModel.uiState.value.needsReviewMessages.map { it.id })

        viewModel.updateSearchQuery("")
        viewModel.selectSort(MessageSort.SCHEDULED_DESC)
        assertEquals(listOf("pm_2", "pm_1"), viewModel.uiState.value.needsReviewMessages.map { it.id })
    }

    @Test
    fun `pending messages expose readiness labels from channel prerequisites`() = runTest(dispatcher) {
        val now = System.currentTimeMillis()
        every { securePrefs.getChannelBlackout() } returns "[\"WHATSAPP\"]"
        every { securePrefs.getSenderEmail() } returns ""
        every { securePrefs.getSenderEmailPassword() } returns ""
        every { messageRepository.getPendingListItems() } returns MutableStateFlow(
            listOf(
                pending("pm_sms", "c_sms", "e_sms", MessageChannel.SMS.raw, now + 2 * 86_400_000L),
                pending("pm_email", "c_email", "e_email", MessageChannel.EMAIL.raw, now + 3 * 86_400_000L),
                pending("pm_whatsapp", "c_whatsapp", "e_whatsapp", MessageChannel.WHATSAPP.raw, now + 4 * 86_400_000L),
            )
        )
        every { contactRepository.getMessageContexts() } returns MutableStateFlow(
            listOf(
                contact("c_sms", "No Phone"),
                contact("c_email", "No Gmail", primaryEmail = "no-gmail@example.com"),
                contact("c_whatsapp", "Blocked WA", primaryPhone = "+919999900000"),
            )
        )
        every { eventRepository.getEventListItems() } returns MutableStateFlow(
            listOf(
                event("e_sms", "c_sms", OccasionType.BIRTHDAY, now),
                event("e_email", "c_email", OccasionType.BIRTHDAY, now),
                event("e_whatsapp", "c_whatsapp", OccasionType.BIRTHDAY, now),
            )
        )

        val viewModel = newViewModel()
        advanceUntilIdle()

        val readinessById = viewModel.uiState.value.blockedMessages.associate {
            it.id to it.readiness
        }
        assertEquals(MessageReadiness.MISSING_PHONE, readinessById["pm_sms"])
        assertEquals(MessageReadiness.EMAIL_SETUP_MISSING, readinessById["pm_email"])
        assertEquals(MessageReadiness.CHANNEL_DISABLED, readinessById["pm_whatsapp"])
    }

    @Test
    fun `preference changes immediately recalculate message readiness`() = runTest(dispatcher) {
        val now = System.currentTimeMillis()
        every { securePrefs.getSenderEmail() } returns ""
        every { securePrefs.getSenderEmailPassword() } returns ""
        every { messageRepository.getPendingListItems() } returns MutableStateFlow(
            listOf(
                pending("pm_email", "c_email", "e_email", MessageChannel.EMAIL.raw, now + 3 * 86_400_000L),
            )
        )
        every { contactRepository.getMessageContexts() } returns MutableStateFlow(
            listOf(
                contact("c_email", "Email Ready", primaryEmail = "ready@example.com"),
            )
        )
        every { eventRepository.getEventListItems() } returns MutableStateFlow(
            listOf(
                event("e_email", "c_email", OccasionType.BIRTHDAY, now),
            )
        )

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(listOf("pm_email"), viewModel.uiState.value.blockedMessages.map { it.id })
        assertEquals(emptyList<String>(), viewModel.uiState.value.needsReviewMessages.map { it.id })

        every { securePrefs.getSenderEmail() } returns "sender@example.com"
        every { securePrefs.getSenderEmailPassword() } returns "app-password"
        preferenceChanges.tryEmit(Unit)
        advanceUntilIdle()

        assertEquals(emptyList<String>(), viewModel.uiState.value.blockedMessages.map { it.id })
        assertEquals(listOf("pm_email"), viewModel.uiState.value.needsReviewMessages.map { it.id })
    }

    @Test
    fun `messages are split into task-state buckets`() = runTest(dispatcher) {
        val now = System.currentTimeMillis()
        every { messageRepository.getPendingListItems() } returns MutableStateFlow(
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
        every { contactRepository.getMessageContexts() } returns MutableStateFlow(
            listOf(
                contact("c_ready", "Ready", primaryPhone = "+919999900000"),
                contact("c_blocked", "Blocked"),
                contact("c_1", "Failed", primaryPhone = "+919999900000"),
            )
        )
        every { eventRepository.getEventListItems() } returns MutableStateFlow(
            listOf(
                event("e_ready", "c_ready", OccasionType.BIRTHDAY, now),
                event("e_scheduled", "c_ready", OccasionType.BIRTHDAY, now),
                event("e_blocked", "c_blocked", OccasionType.BIRTHDAY, now),
                event("e_1", "c_1", OccasionType.BIRTHDAY, now),
            )
        )

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(listOf("needs_review"), viewModel.uiState.value.needsReviewMessages.map { it.id })
        assertEquals(listOf("scheduled"), viewModel.uiState.value.scheduledMessages.map { it.id })
        assertEquals(listOf("blocked"), viewModel.uiState.value.blockedMessages.map { it.id })
        assertEquals(listOf("failed"), viewModel.uiState.value.failedMessages.map { it.id })
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
            retryFailedMessageUseCase = retryFailedMessageUseCase,
            activityLogRepository = activityLogRepository,
            securePrefs = securePrefs,
        )
    }
}
