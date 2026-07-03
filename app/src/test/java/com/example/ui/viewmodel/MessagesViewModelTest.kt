package com.example.ui.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import com.example.domain.readiness.RelationshipReadinessAction
import com.example.domain.readiness.RelationshipReadinessReason
import com.example.domain.readiness.RelationshipReadinessState
import com.example.domain.repository.ActivityLogRepository
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.repository.MessageRepository
import com.example.domain.service.PreferencesRepository
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
import java.util.Calendar
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
    private val preferencesRepository: PreferencesRepository = mockk(relaxed = true)
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
        every { preferencesRepository.observeChanges() } returns preferenceChanges
        every { preferencesRepository.getChannelBlackout() } returns "[]"
        every { preferencesRepository.getSenderEmail() } returns "sender@example.com"
        every { preferencesRepository.getSenderEmailPassword() } returns "app-password"
        every { preferencesRepository.getQuietHoursStart() } returns 0
        every { preferencesRepository.getQuietHoursEnd() } returns 0
        every { preferencesRepository.getBlackoutDates() } returns "[]"
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
    fun `retryMessage queues failed message and records retry activity`() = runTest(dispatcher) {
        coEvery { retryFailedMessageUseCase("pm_1") } returns RetryFailedMessageUseCase.RetryOutcome.RetryQueued(
            pendingMessageId = "pm_1",
            retryCount = 1,
            previousAttempt = null,
        )

        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.retryMessage("pm_1")
        advanceUntilIdle()

        coVerify { retryFailedMessageUseCase("pm_1") }
        coVerify {
            activityLogRepository.record(
                match { entry ->
                    entry.title == context.getString(com.example.R.string.message_activity_retried_title) &&
                        entry.detail == context.getString(com.example.R.string.message_activity_retried_detail) &&
                        entry.messageId == "pm_1"
                }
            )
        }
        assertEquals(null, viewModel.uiState.value.retryingMessageId)
        assertEquals(null, viewModel.uiState.value.error)
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
        every { preferencesRepository.getChannelBlackout() } returns "[\"WHATSAPP\"]"
        every { preferencesRepository.getSenderEmail() } returns ""
        every { preferencesRepository.getSenderEmailPassword() } returns ""
        every { messageRepository.getPendingListItems() } returns MutableStateFlow(
            listOf(
                pending("pm_sms", "c_sms", "e_sms", MessageChannel.SMS.raw, now + 2 * 86_400_000L),
                pending("pm_email", "c_email", "e_email", MessageChannel.EMAIL.raw, now + 3 * 86_400_000L),
                pending("pm_whatsapp", "c_whatsapp", "e_whatsapp", MessageChannel.WHATSAPP.raw, now + 4 * 86_400_000L),
                pending("pm_bad_email", "c_bad_email", "e_bad_email", MessageChannel.EMAIL.raw, now + 5 * 86_400_000L),
            )
        )
        every { contactRepository.getMessageContexts() } returns MutableStateFlow(
            listOf(
                contact("c_sms", "No Phone"),
                contact("c_email", "No Gmail", primaryEmail = "no-gmail@example.com"),
                contact("c_whatsapp", "Blocked WA", primaryPhone = "+919999900000"),
                contact("c_bad_email", "Bad Email", primaryEmail = "bad email"),
            )
        )
        every { eventRepository.getEventListItems() } returns MutableStateFlow(
            listOf(
                event("e_sms", "c_sms", OccasionType.BIRTHDAY, now),
                event("e_email", "c_email", OccasionType.BIRTHDAY, now),
                event("e_whatsapp", "c_whatsapp", OccasionType.BIRTHDAY, now),
                event("e_bad_email", "c_bad_email", OccasionType.BIRTHDAY, now),
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
        assertEquals(MessageReadiness.MISSING_EMAIL, readinessById["pm_bad_email"])

        val canonicalById = viewModel.uiState.value.blockedMessages.associateBy { it.id }
        assertEquals(RelationshipReadinessState.ACTION_REQUIRED, canonicalById.getValue("pm_sms").actionReadiness.state)
        assertEquals(RelationshipReadinessReason.MISSING_PHONE, canonicalById.getValue("pm_sms").actionReadiness.primaryReason)
        assertEquals(RelationshipReadinessAction.CONFIGURE_CHANNEL, canonicalById.getValue("pm_sms").actionReadiness.primaryAction)
        assertEquals(RelationshipReadinessAction.CONFIGURE_EMAIL, canonicalById.getValue("pm_email").actionReadiness.primaryAction)
        assertEquals(RelationshipReadinessAction.CONFIGURE_CHANNEL, canonicalById.getValue("pm_whatsapp").actionReadiness.primaryAction)
        assertEquals(RelationshipReadinessAction.CONFIGURE_CHANNEL, canonicalById.getValue("pm_bad_email").actionReadiness.primaryAction)
        assertEquals(MessageActionRoute.CONTACT, canonicalById.getValue("pm_sms").primaryActionRoute)
        assertEquals(MessageActionRoute.AUTOMATION_SETUP, canonicalById.getValue("pm_email").primaryActionRoute)
        assertEquals(MessageActionRoute.AUTOMATION_SETUP, canonicalById.getValue("pm_whatsapp").primaryActionRoute)
        assertEquals(MessageActionRoute.CONTACT, canonicalById.getValue("pm_bad_email").primaryActionRoute)
    }

    @Test
    fun `preference changes immediately recalculate message readiness`() = runTest(dispatcher) {
        val now = System.currentTimeMillis()
        every { preferencesRepository.getSenderEmail() } returns ""
        every { preferencesRepository.getSenderEmailPassword() } returns ""
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

        every { preferencesRepository.getSenderEmail() } returns "not-an-email"
        every { preferencesRepository.getSenderEmailPassword() } returns "app-password"
        preferenceChanges.tryEmit(Unit)
        advanceUntilIdle()

        assertEquals(listOf("pm_email"), viewModel.uiState.value.blockedMessages.map { it.id })
        assertEquals(emptyList<String>(), viewModel.uiState.value.needsReviewMessages.map { it.id })

        every { preferencesRepository.getSenderEmail() } returns "sender@example.com"
        every { preferencesRepository.getSenderEmailPassword() } returns "app-password"
        preferenceChanges.tryEmit(Unit)
        advanceUntilIdle()

        assertEquals(emptyList<String>(), viewModel.uiState.value.blockedMessages.map { it.id })
        assertEquals(listOf("pm_email"), viewModel.uiState.value.needsReviewMessages.map { it.id })
    }

    @Test
    fun `approved future messages expose scheduled-time readiness`() = runTest(dispatcher) {
        val now = System.currentTimeMillis()
        every { messageRepository.getPendingListItems() } returns MutableStateFlow(
            listOf(
                pending(
                    "scheduled",
                    "c_ready",
                    "e_ready",
                    MessageChannel.SMS.raw,
                    now + 86_400_000L,
                    status = MessageStatus.APPROVED.raw,
                ),
            )
        )
        every { contactRepository.getMessageContexts() } returns MutableStateFlow(
            listOf(contact("c_ready", "Ready", primaryPhone = "+919999900000"))
        )

        val viewModel = newViewModel()
        advanceUntilIdle()

        val scheduled = viewModel.uiState.value.scheduledMessages.single()
        assertEquals("scheduled", scheduled.id)
        assertEquals(MessageReadiness.APPROVED_WAITING_FOR_SCHEDULE, scheduled.readiness)
        assertEquals(RelationshipReadinessState.WAITING, scheduled.actionReadiness.state)
        assertEquals(RelationshipReadinessReason.WAITING_FOR_SCHEDULE, scheduled.actionReadiness.primaryReason)
        assertEquals(RelationshipReadinessAction.NONE, scheduled.actionReadiness.primaryAction)
    }

    @Test
    fun `approved due messages expose allowed-window readiness during quiet hours`() = runTest(dispatcher) {
        val now = System.currentTimeMillis()
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        every { preferencesRepository.getQuietHoursStart() } returns currentHour
        every { preferencesRepository.getQuietHoursEnd() } returns ((currentHour + 1) % 24)
        every { messageRepository.getPendingListItems() } returns MutableStateFlow(
            listOf(
                pending(
                    "paused",
                    "c_ready",
                    "e_ready",
                    MessageChannel.SMS.raw,
                    now - 60_000L,
                    status = MessageStatus.APPROVED.raw,
                ),
            )
        )
        every { contactRepository.getMessageContexts() } returns MutableStateFlow(
            listOf(contact("c_ready", "Ready", primaryPhone = "+919999900000"))
        )

        val viewModel = newViewModel()
        advanceUntilIdle()

        val scheduled = viewModel.uiState.value.scheduledMessages.single()
        assertEquals("paused", scheduled.id)
        assertEquals(MessageReadiness.APPROVED_WAITING_FOR_ALLOWED_WINDOW, scheduled.readiness)
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
            preferencesRepository = preferencesRepository,
        )
    }
}
