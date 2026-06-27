package com.example.domain.usecase

import com.example.core.db.entities.ContactEntity
import com.example.domain.model.ActivityLogSeverity
import com.example.domain.model.ActivityLogStatus
import com.example.domain.model.ApprovalMode
import com.example.domain.model.DispatchActivityDecision
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.dispatch.DispatchAttemptResult
import com.example.domain.model.dispatch.DispatchEligibilityRecord
import com.example.domain.model.dispatch.MessageDispatchDraft
import com.example.domain.model.message.MessageDispatchState
import com.example.domain.model.message.MessageDraft
import com.example.domain.repository.ActivityLogRepository
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.DispatchAttemptRepository
import com.example.domain.repository.MessageRepository
import com.example.domain.service.MessageDispatcherService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DispatchMessageUseCaseTest {

    private val messageRepository: MessageRepository = mockk(relaxed = true)
    private val contactRepository: ContactRepository = mockk(relaxed = true)
    private val messageDispatcherService: MessageDispatcherService = mockk(relaxed = true)
    private val activityLogRepository: ActivityLogRepository = mockk(relaxed = true)
    private val dispatchAttemptRepository: DispatchAttemptRepository = mockk(relaxed = true)
    private val useCase = DispatchMessageUseCase(
        messageRepository,
        contactRepository,
        messageDispatcherService,
        activityLogRepository,
        dispatchAttemptRepository,
    )

    @Test
    fun `invoke with missing pending message returns PendingNotFound`() = runTest {
        coEvery { messageRepository.getMessageDispatchStateById("e1") } returns null
        coEvery { messageRepository.getMessageDispatchStateByEventId("e1") } returns null

        val result = useCase("e1")

        assertEquals(DispatchMessageUseCase.DispatchOutcome.PendingNotFound, result)
    }

    @Test
    fun `invoke with pending message not approved returns NotApproved`() = runTest {
        val pendingMsg = dispatchMessage(
            approvalMode = ApprovalMode.UNKNOWN,
            status = MessageStatus.PENDING,
        )
        coEvery { messageRepository.getMessageDispatchStateById("e1") } returns null
        coEvery { messageRepository.getMessageDispatchStateByEventId("e1") } returns pendingMsg

        val result = useCase("e1")

        assertTrue(result is DispatchMessageUseCase.DispatchOutcome.NotApproved)
        assertEquals("PENDING", (result as DispatchMessageUseCase.DispatchOutcome.NotApproved).status)
        coVerify {
            dispatchAttemptRepository.upsert(match {
                it.messageDraftId.value == "msg_1" &&
                    it.eligibilityDecision == DispatchEligibilityRecord.NEEDS_APPROVAL &&
                    it.result == DispatchAttemptResult.NEEDS_APPROVAL &&
                    it.blockOrDeferReason == "UNKNOWN"
            })
        }
        coVerify {
            activityLogRepository.record(match {
                it.title == "Dispatch waiting for approval" &&
                    it.messageId == "msg_1" &&
                    it.status == ActivityLogStatus.OPEN.raw &&
                    it.metadataJson.contains("\"decision\":\"${DispatchActivityDecision.NEEDS_APPROVAL.raw}\"")
            })
        }
    }

    @Test
    fun `invoke with missing contact returns ContactNotFound`() = runTest {
        val pendingMsg = dispatchMessage(status = MessageStatus.APPROVED)
        coEvery { messageRepository.getMessageDispatchStateById("e1") } returns null
        coEvery { messageRepository.getMessageDispatchStateByEventId("e1") } returns pendingMsg
        coEvery { contactRepository.getById("c1") } returns null

        val result = useCase("e1")

        assertEquals(DispatchMessageUseCase.DispatchOutcome.ContactNotFound, result)
        coVerify {
            dispatchAttemptRepository.upsert(match {
                it.messageDraftId.value == "msg_1" &&
                    it.eligibilityDecision == DispatchEligibilityRecord.BLOCKED &&
                    it.result == DispatchAttemptResult.BLOCKED &&
                    it.blockOrDeferReason == "CONTACT_NOT_FOUND"
            })
        }
        coVerify {
            activityLogRepository.record(match {
                it.title == "Dispatch blocked" &&
                    it.severity == ActivityLogSeverity.ERROR.raw &&
                    it.metadataJson.contains("\"reason\":\"CONTACT_NOT_FOUND\"")
            })
        }
    }

    @Test
    fun `invoke with valid approved message dispatches successfully`() = runTest {
        val pendingMsg = dispatchMessage(status = MessageStatus.APPROVED)
        val contact = ContactEntity(id = "c1", name = "John Doe")

        coEvery { messageRepository.getMessageDispatchStateById("e1") } returns null
        coEvery { messageRepository.getMessageDispatchStateByEventId("e1") } returns pendingMsg
        coEvery { contactRepository.getById("c1") } returns contact

        val result = useCase("e1")

        assertTrue(result is DispatchMessageUseCase.DispatchOutcome.Sent)
        val sent = result as DispatchMessageUseCase.DispatchOutcome.Sent
        assertEquals("msg_1", sent.pendingId)
        assertEquals(MessageChannel.SMS.raw, sent.channel)

        coVerify {
            dispatchAttemptRepository.upsert(match {
                it.messageDraftId.value == "msg_1" &&
                    it.eligibilityDecision == DispatchEligibilityRecord.SEND_NOW &&
                    it.result == DispatchAttemptResult.QUEUED
            })
        }
        coVerify {
            messageDispatcherService.dispatch(match {
                it.messageId == pendingMsg.id &&
                    it.contactId.value == contact.id &&
                    it.messageText == "hi" &&
                    it.dispatchAttemptId != null
            })
        }
        coVerify {
            activityLogRepository.record(match {
                it.title == "Dispatch sent" &&
                    it.status == ActivityLogStatus.RESOLVED.raw &&
                    it.messageId == "msg_1" &&
                    it.metadataJson.contains("\"decision\":\"${DispatchActivityDecision.SENT.raw}\"") &&
                    !it.detail.contains("hi") &&
                    !it.metadataJson.contains("hi")
            })
        }
    }

    @Test
    fun `invoke with future approved message returns Deferred and does not dispatch`() = runTest {
        val scheduledForMs = System.currentTimeMillis() + 60_000L
        val pendingMsg = dispatchMessage(
            scheduledForMs = scheduledForMs,
            approvalMode = ApprovalMode.FULLY_AUTO,
            status = MessageStatus.APPROVED,
        )

        coEvery { messageRepository.getMessageDispatchStateById("msg_1") } returns pendingMsg

        val result = useCase("msg_1")

        assertTrue(result is DispatchMessageUseCase.DispatchOutcome.Deferred)
        assertEquals(scheduledForMs, (result as DispatchMessageUseCase.DispatchOutcome.Deferred).scheduledForMs)
        coVerify(exactly = 0) { messageDispatcherService.dispatch(any()) }
        coVerify {
            dispatchAttemptRepository.upsert(match {
                it.messageDraftId.value == "msg_1" &&
                    it.eligibilityDecision == DispatchEligibilityRecord.DEFERRED &&
                    it.result == DispatchAttemptResult.DEFERRED
            })
        }
        coVerify {
            activityLogRepository.record(match {
                it.title == "Dispatch deferred" &&
                    it.status == ActivityLogStatus.OPEN.raw &&
                    it.metadataJson.contains("\"decision\":\"${DispatchActivityDecision.DEFERRED.raw}\"") &&
                    it.metadataJson.contains("\"scheduledForMs\":\"$scheduledForMs\"")
            })
        }
    }

    @Test
    fun `invoke with due smart approve pending message dispatches successfully`() = runTest {
        val pendingMsg = dispatchMessage(
            approvalMode = ApprovalMode.SMART_APPROVE,
            status = MessageStatus.PENDING,
        )
        val contact = ContactEntity(id = "c1", name = "John Doe")

        coEvery { messageRepository.getMessageDispatchStateById("msg_1") } returns pendingMsg
        coEvery { contactRepository.getById("c1") } returns contact

        val result = useCase("msg_1")

        assertTrue(result is DispatchMessageUseCase.DispatchOutcome.Sent)
        coVerify {
            messageDispatcherService.dispatch(match {
                it.messageId == pendingMsg.id &&
                    it.contactId.value == contact.id &&
                    it.preferredChannel == MessageChannel.SMS &&
                    it.dispatchAttemptId != null
            })
        }
        coVerify {
            activityLogRepository.record(match {
                it.title == "Dispatch sent" &&
                    it.metadataJson.contains("\"approvalMode\":\"SMART_APPROVE\"")
            })
        }
    }

    @Test
    fun `invoke with expired vip pending message updates status`() = runTest {
        val pendingMsg = dispatchMessage(
            scheduledForMs = System.currentTimeMillis() - (3 * 60 * 60 * 1000L),
            approvalMode = ApprovalMode.VIP_APPROVE,
            status = MessageStatus.PENDING,
        )

        coEvery { messageRepository.getMessageDispatchStateById("msg_1") } returns pendingMsg
        coEvery { messageRepository.updatePendingStatus(any(), any()) } returns Unit

        val result = useCase("msg_1")

        assertEquals(DispatchMessageUseCase.DispatchOutcome.Expired("msg_1"), result)
        coVerify { messageRepository.updatePendingStatus("msg_1", "EXPIRED") }
        coVerify(exactly = 0) { messageDispatcherService.dispatch(any()) }
        coVerify {
            dispatchAttemptRepository.upsert(match {
                it.messageDraftId.value == "msg_1" &&
                    it.eligibilityDecision == DispatchEligibilityRecord.EXPIRED &&
                    it.result == DispatchAttemptResult.EXPIRED
            })
        }
        coVerify {
            activityLogRepository.record(match {
                it.title == "Dispatch expired" &&
                    it.severity == ActivityLogSeverity.WARNING.raw &&
                    it.status == ActivityLogStatus.RESOLVED.raw &&
                    it.metadataJson.contains("\"decision\":\"${DispatchActivityDecision.EXPIRED.raw}\"") &&
                    it.metadataJson.contains("\"status\":\"EXPIRED\"")
            })
        }
    }

    @Test
    fun `invoke with already sent message records blocked activity`() = runTest {
        val pendingMsg = dispatchMessage(
            approvalMode = ApprovalMode.ALWAYS_ASK,
            status = MessageStatus.SENT,
        )
        coEvery { messageRepository.getMessageDispatchStateById("msg_1") } returns pendingMsg

        val result = useCase("msg_1")

        assertTrue(result is DispatchMessageUseCase.DispatchOutcome.NotApproved)
        coVerify(exactly = 0) { messageDispatcherService.dispatch(any()) }
        coVerify {
            dispatchAttemptRepository.upsert(match {
                it.messageDraftId.value == "msg_1" &&
                    it.eligibilityDecision == DispatchEligibilityRecord.BLOCKED &&
                    it.result == DispatchAttemptResult.BLOCKED &&
                    it.blockOrDeferReason == "ALREADY_HANDLED"
            })
        }
        coVerify {
            activityLogRepository.record(match {
                it.title == "Dispatch blocked" &&
                    it.detail == "Message was already handled." &&
                    it.metadataJson.contains("\"decision\":\"${DispatchActivityDecision.BLOCKED.raw}\"") &&
                    it.metadataJson.contains("\"reason\":\"ALREADY_HANDLED\"")
            })
        }
    }

    private fun dispatchMessage(
        id: String = "msg_1",
        contactId: String = "c1",
        eventId: String = "e1",
        text: String = "hi",
        channel: MessageChannel = MessageChannel.SMS,
        scheduledForMs: Long = 0,
        approvalMode: ApprovalMode = ApprovalMode.UNKNOWN,
        status: MessageStatus = MessageStatus.PENDING,
    ): MessageDispatchState {
        val draftId = MessageDraftId(id)
        val occasionId = OccasionId(eventId)
        return MessageDispatchState(
            draft = MessageDraft(
                id = draftId,
                contactId = ContactId(contactId),
                occasionId = occasionId,
                scheduledForMs = scheduledForMs,
                approvalMode = approvalMode,
                status = status,
                channel = channel,
                scheduledYear = 0,
                qualityScore = 0,
                isUsingFallback = false,
            ),
            dispatchDraft = MessageDispatchDraft(
                id = draftId,
                occasionReference = occasionId,
                preferredChannel = channel,
                messageText = text,
            ),
        )
    }
}
