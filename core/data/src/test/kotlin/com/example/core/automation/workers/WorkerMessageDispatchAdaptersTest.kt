package com.example.core.automation.workers

import androidx.work.workDataOf
import com.example.core.db.dao.ContactDao
import com.example.core.db.dao.DispatchAttemptDao
import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.PendingMessageEntity
import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.DispatchAttemptId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.dispatch.DispatchAttemptOutcomeUpdate
import com.example.domain.model.dispatch.DispatchAttemptResult
import com.example.domain.model.message.ExactSendCommand
import com.example.domain.model.message.ExactSendScheduleUpdate
import com.example.domain.model.message.MessageDispatchWorkerInputCommand
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkerMessageDispatchAdaptersTest {
    private val pendingMessageDao: PendingMessageDao = mockk(relaxed = true)
    private val contactDao: ContactDao = mockk(relaxed = true)
    private val dispatchAttemptDao: DispatchAttemptDao = mockk(relaxed = true)

    @Test
    fun toMessageDispatchWorkerInputCommand_mapsPendingMessageWithOccasion() {
        val command = toMessageDispatchWorkerInputCommand(
            pendingMessageId = "pm_1",
            eventId = "event_1",
        )

        assertEquals(
            MessageDispatchWorkerInputCommand.PendingMessage(
                messageId = MessageDraftId("pm_1"),
                occasionId = OccasionId("event_1"),
            ),
            command,
        )
    }

    @Test
    fun toMessageDispatchWorkerInputCommand_mapsLegacyOccasionWhenPendingIdMissing() {
        val command = toMessageDispatchWorkerInputCommand(
            pendingMessageId = null,
            eventId = "event_1",
        )

        assertEquals(
            MessageDispatchWorkerInputCommand.LegacyOccasion(OccasionId("event_1")),
            command,
        )
    }

    @Test
    fun toMessageDispatchWorkerInputCommand_prefersPendingMessageWhenBothIdsExist() {
        val command = toMessageDispatchWorkerInputCommand(
            pendingMessageId = "pm_1",
            eventId = "event_1",
        )

        assertEquals(MessageDispatchWorkerInputCommand.PendingMessage::class, command?.javaClass?.kotlin)
    }

    @Test
    fun toMessageDispatchWorkerInputCommand_ignoresMissingIds() {
        assertNull(toMessageDispatchWorkerInputCommand(pendingMessageId = null, eventId = null))
        assertNull(toMessageDispatchWorkerInputCommand(pendingMessageId = " ", eventId = " "))
    }

    @Test
    fun toMessageDispatchWorkerInputCommand_readsWorkManagerInputData() {
        val data = workDataOf(
            MessageDispatchWorkRequests.KEY_PENDING_MESSAGE_ID to "pm_1",
            MessageDispatchWorkRequests.KEY_EVENT_ID to "event_1",
        )

        assertEquals(
            MessageDispatchWorkerInputCommand.PendingMessage(
                messageId = MessageDraftId("pm_1"),
                occasionId = OccasionId("event_1"),
            ),
            data.toMessageDispatchWorkerInputCommand(),
        )
    }

    @Test
    fun logFields_preservesLegacyStructuredLogKeys() {
        val pendingCommand = MessageDispatchWorkerInputCommand.PendingMessage(
            messageId = MessageDraftId("pm_1"),
            occasionId = OccasionId("event_1"),
        )
        val legacyCommand = MessageDispatchWorkerInputCommand.LegacyOccasion(OccasionId("event_2"))

        assertEquals(
            mapOf("pendingMessageId" to "pm_1", "eventId" to "event_1"),
            pendingCommand.logFields(),
        )
        assertEquals(
            mapOf("pendingMessageId" to "", "eventId" to "event_2"),
            legacyCommand.logFields(),
        )
    }

    @Test
    fun getMessageDispatchState_mapsPendingCommandToPureDispatchState() = runTest {
        coEvery { pendingMessageDao.getById("pm_1") } returns pendingMessage(
            id = "pm_1",
            channel = " email ",
            approvalMode = " smart_approve ",
            status = " approved ",
        ).copy(
            editedByUser = true,
            userEditedText = "Edited dispatch text",
        )

        val state = pendingMessageDao.getMessageDispatchState(
            MessageDispatchWorkerInputCommand.PendingMessage(
                messageId = MessageDraftId("pm_1"),
                occasionId = null,
            )
        )

        assertEquals(MessageDraftId("pm_1"), state?.id)
        assertEquals(ContactId("contact_1"), state?.contactId)
        assertEquals(OccasionId("event_1"), state?.occasionId)
        assertEquals(MessageChannel.EMAIL, state?.channel)
        assertEquals(ApprovalMode.SMART_APPROVE, state?.draft?.approvalMode)
        assertEquals(MessageStatus.APPROVED, state?.status)
        assertEquals("Edited dispatch text", state?.dispatchDraft?.messageText)
    }

    @Test
    fun getMessageDispatchState_mapsLegacyOccasionCommandToPureDispatchState() = runTest {
        coEvery { pendingMessageDao.getByEventId("event_1") } returns pendingMessage(
            id = "pm_event",
            eventId = "event_1",
        )

        val state = pendingMessageDao.getMessageDispatchState(
            MessageDispatchWorkerInputCommand.LegacyOccasion(OccasionId("event_1"))
        )

        assertEquals(MessageDraftId("pm_event"), state?.id)
        assertEquals(OccasionId("event_1"), state?.occasionId)
    }

    @Test
    fun toExactSendCommand_mapsDispatchStateIdToSchedulerCommand() = runTest {
        coEvery { pendingMessageDao.getById("pm_1") } returns pendingMessage(id = "pm_1")
        val state = pendingMessageDao.getMessageDispatchState(
            MessageDispatchWorkerInputCommand.PendingMessage(
                messageId = MessageDraftId("pm_1"),
                occasionId = null,
            )
        )

        assertEquals(
            ExactSendCommand(MessageDraftId("pm_1")),
            state?.toExactSendCommand(),
        )
    }

    @Test
    fun toExactSendScheduleUpdate_mapsDispatchStateIdAndDeferredTime() = runTest {
        coEvery { pendingMessageDao.getById("pm_1") } returns pendingMessage(id = "pm_1")
        val state = pendingMessageDao.getMessageDispatchState(
            MessageDispatchWorkerInputCommand.PendingMessage(
                messageId = MessageDraftId("pm_1"),
                occasionId = null,
            )
        )

        assertEquals(
            ExactSendScheduleUpdate(
                messageId = MessageDraftId("pm_1"),
                scheduledForMs = 1_900_000_000_000L,
            ),
            state?.toExactSendScheduleUpdate(1_900_000_000_000L),
        )
    }

    @Test
    fun saveMessageDispatchDeferralScheduleUpdate_mapsTypedUpdateToTargetedDaoWrite() = runTest {
        pendingMessageDao.saveMessageDispatchDeferralScheduleUpdate(
            ExactSendScheduleUpdate(
                messageId = MessageDraftId("pm_1"),
                scheduledForMs = 1_900_000_000_000L,
            )
        )

        coVerify {
            pendingMessageDao.updateScheduledFor(
                id = "pm_1",
                scheduledForMs = 1_900_000_000_000L,
            )
        }
    }

    @Test
    fun messageDispatchExceptionOutcomeUpdate_mapsExceptionToTypedFinalFailureOutcome() {
        val update = messageDispatchExceptionOutcomeUpdate(
            dispatchAttemptId = "attempt_1",
            failedAtMs = 1_800_000_000_000L,
            exception = IllegalStateException("dispatcher crashed"),
        )

        assertEquals(
            DispatchAttemptOutcomeUpdate(
                id = DispatchAttemptId("attempt_1"),
                attemptedAtMs = 1_800_000_000_000L,
                resolvedAtMs = 1_800_000_000_000L,
                result = DispatchAttemptResult.FAILED_FINAL,
                channel = null,
                deliveryStatus = MessageDeliveryStatus.FAILED,
                providerMessageId = null,
                errorType = "IllegalStateException",
                errorCode = null,
                redactedErrorMessage = "Dispatcher failed before completing send.",
                retryCount = 0,
                nextRetryAtMs = null,
                deadLetteredAtMs = 1_800_000_000_000L,
            ),
            update,
        )
    }

    @Test
    fun saveMessageDispatchExceptionOutcome_mapsTypedOutcomeToRawDaoCall() = runTest {
        dispatchAttemptDao.saveMessageDispatchExceptionOutcome(
            DispatchAttemptOutcomeUpdate(
                id = DispatchAttemptId("attempt_1"),
                attemptedAtMs = 1_800_000_000_000L,
                resolvedAtMs = 1_800_000_000_000L,
                result = DispatchAttemptResult.FAILED_FINAL,
                channel = null,
                deliveryStatus = MessageDeliveryStatus.FAILED,
                providerMessageId = null,
                errorType = "IllegalStateException",
                errorCode = null,
                redactedErrorMessage = "Dispatcher failed before completing send.",
                retryCount = 0,
                nextRetryAtMs = null,
                deadLetteredAtMs = 1_800_000_000_000L,
            )
        )

        coVerify {
            dispatchAttemptDao.updateOutcome(
                id = "attempt_1",
                attemptedAtMs = 1_800_000_000_000L,
                resolvedAtMs = 1_800_000_000_000L,
                result = DispatchAttemptResult.FAILED_FINAL.raw,
                channel = null,
                deliveryStatus = MessageDeliveryStatus.FAILED.raw,
                providerMessageId = null,
                errorType = "IllegalStateException",
                errorCode = null,
                redactedErrorMessage = "Dispatcher failed before completing send.",
                retryCount = 0,
                nextRetryAtMs = null,
                deadLetteredAtMs = 1_800_000_000_000L,
            )
        }
    }

    @Test
    fun getMessageDispatchRecipientById_mapsContactRowToPureRecipient() = runTest {
        coEvery { contactDao.getById("contact_1") } returns ContactEntity(
            id = "contact_1",
            name = "Asha Sharma",
            primaryPhone = "+911234567890",
            primaryEmail = "asha@example.com",
            notesText = "Not needed for dispatch.",
        )

        val recipient = contactDao.getMessageDispatchRecipientById("contact_1")

        assertEquals(ContactId("contact_1"), recipient?.id)
        assertEquals("Asha Sharma", recipient?.displayName)
        assertEquals("+911234567890", recipient?.primaryPhone)
        assertEquals("asha@example.com", recipient?.primaryEmail)
    }

    private fun pendingMessage(
        id: String,
        eventId: String = "event_1",
        channel: String = MessageChannel.SMS.raw,
        approvalMode: String = ApprovalMode.ALWAYS_ASK.raw,
        status: String = MessageStatus.PENDING.raw,
    ): PendingMessageEntity {
        return PendingMessageEntity(
            id = id,
            contactId = "contact_1",
            eventId = eventId,
            shortVariant = "Short wish",
            standardVariant = "Standard wish",
            longVariant = "Long wish",
            formalVariant = "Formal wish",
            funnyVariant = "Funny wish",
            emotionalVariant = "Emotional wish",
            selectedVariant = "standard",
            selectedVariantText = "Standard wish",
            channel = channel,
            scheduledForMs = 1_800_000_000_000,
            approvalMode = approvalMode,
            status = status,
        )
    }
}
