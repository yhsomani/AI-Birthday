package com.example.domain.usecase

import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.PendingMessageEntity
import com.example.domain.model.ActivityLogSeverity
import com.example.domain.model.ActivityLogStatus
import com.example.domain.model.DispatchActivityDecision
import com.example.domain.model.MessageChannel
import com.example.domain.repository.ActivityLogRepository
import com.example.domain.repository.ContactRepository
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
    private val useCase = DispatchMessageUseCase(
        messageRepository,
        contactRepository,
        messageDispatcherService,
        activityLogRepository,
    )

    @Test
    fun `invoke with missing pending message returns PendingNotFound`() = runTest {
        coEvery { messageRepository.getPendingById("e1") } returns null
        coEvery { messageRepository.getPendingByEventId("e1") } returns null

        val result = useCase("e1")

        assertEquals(DispatchMessageUseCase.DispatchOutcome.PendingNotFound, result)
    }

    @Test
    fun `invoke with pending message not approved returns NotApproved`() = runTest {
        val pendingMsg = PendingMessageEntity(
            id = "msg_1",
            contactId = "c1",
            eventId = "e1",
            shortVariant = "", standardVariant = "hi", longVariant = "",
            formalVariant = "", funnyVariant = "", emotionalVariant = "",
            selectedVariant = "standard", selectedVariantText = "hi",
            channel = MessageChannel.SMS.raw, scheduledForMs = 0, approvalMode = "MANUAL",
            status = "PENDING"
        )
        coEvery { messageRepository.getPendingById("e1") } returns null
        coEvery { messageRepository.getPendingByEventId("e1") } returns pendingMsg

        val result = useCase("e1")

        assertTrue(result is DispatchMessageUseCase.DispatchOutcome.NotApproved)
        assertEquals("PENDING", (result as DispatchMessageUseCase.DispatchOutcome.NotApproved).status)
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
        val pendingMsg = PendingMessageEntity(
            id = "msg_1",
            contactId = "c1",
            eventId = "e1",
            shortVariant = "", standardVariant = "hi", longVariant = "",
            formalVariant = "", funnyVariant = "", emotionalVariant = "",
            selectedVariant = "standard", selectedVariantText = "hi",
            channel = MessageChannel.SMS.raw, scheduledForMs = 0, approvalMode = "MANUAL",
            status = "APPROVED"
        )
        coEvery { messageRepository.getPendingById("e1") } returns null
        coEvery { messageRepository.getPendingByEventId("e1") } returns pendingMsg
        coEvery { contactRepository.getById("c1") } returns null

        val result = useCase("e1")

        assertEquals(DispatchMessageUseCase.DispatchOutcome.ContactNotFound, result)
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
        val pendingMsg = PendingMessageEntity(
            id = "msg_1",
            contactId = "c1",
            eventId = "e1",
            shortVariant = "", standardVariant = "hi", longVariant = "",
            formalVariant = "", funnyVariant = "", emotionalVariant = "",
            selectedVariant = "standard", selectedVariantText = "hi",
            channel = MessageChannel.SMS.raw, scheduledForMs = 0, approvalMode = "MANUAL",
            status = "APPROVED"
        )
        val contact = ContactEntity(id = "c1", name = "John Doe")

        coEvery { messageRepository.getPendingById("e1") } returns null
        coEvery { messageRepository.getPendingByEventId("e1") } returns pendingMsg
        coEvery { contactRepository.getById("c1") } returns contact

        val result = useCase("e1")

        assertTrue(result is DispatchMessageUseCase.DispatchOutcome.Sent)
        val sent = result as DispatchMessageUseCase.DispatchOutcome.Sent
        assertEquals("msg_1", sent.pendingId)
        assertEquals(MessageChannel.SMS.raw, sent.channel)

        coVerify { messageDispatcherService.dispatch(pendingMsg, contact) }
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
        val pendingMsg = PendingMessageEntity(
            id = "msg_1",
            contactId = "c1",
            eventId = "e1",
            shortVariant = "", standardVariant = "hi", longVariant = "",
            formalVariant = "", funnyVariant = "", emotionalVariant = "",
            selectedVariant = "standard", selectedVariantText = "hi",
            channel = MessageChannel.SMS.raw, scheduledForMs = scheduledForMs, approvalMode = "FULLY_AUTO",
            status = "APPROVED"
        )

        coEvery { messageRepository.getPendingById("msg_1") } returns pendingMsg

        val result = useCase("msg_1")

        assertTrue(result is DispatchMessageUseCase.DispatchOutcome.Deferred)
        assertEquals(scheduledForMs, (result as DispatchMessageUseCase.DispatchOutcome.Deferred).scheduledForMs)
        coVerify(exactly = 0) { messageDispatcherService.dispatch(any(), any()) }
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
        val pendingMsg = PendingMessageEntity(
            id = "msg_1",
            contactId = "c1",
            eventId = "e1",
            shortVariant = "", standardVariant = "hi", longVariant = "",
            formalVariant = "", funnyVariant = "", emotionalVariant = "",
            selectedVariant = "standard", selectedVariantText = "hi",
            channel = MessageChannel.SMS.raw, scheduledForMs = 0, approvalMode = "SMART_APPROVE",
            status = "PENDING"
        )
        val contact = ContactEntity(id = "c1", name = "John Doe")

        coEvery { messageRepository.getPendingById("msg_1") } returns pendingMsg
        coEvery { contactRepository.getById("c1") } returns contact

        val result = useCase("msg_1")

        assertTrue(result is DispatchMessageUseCase.DispatchOutcome.Sent)
        coVerify { messageDispatcherService.dispatch(pendingMsg, contact) }
        coVerify {
            activityLogRepository.record(match {
                it.title == "Dispatch sent" &&
                    it.metadataJson.contains("\"approvalMode\":\"SMART_APPROVE\"")
            })
        }
    }

    @Test
    fun `invoke with expired vip pending message updates status`() = runTest {
        val pendingMsg = PendingMessageEntity(
            id = "msg_1",
            contactId = "c1",
            eventId = "e1",
            shortVariant = "", standardVariant = "hi", longVariant = "",
            formalVariant = "", funnyVariant = "", emotionalVariant = "",
            selectedVariant = "standard", selectedVariantText = "hi",
            channel = MessageChannel.SMS.raw,
            scheduledForMs = System.currentTimeMillis() - (3 * 60 * 60 * 1000L),
            approvalMode = "VIP_APPROVE",
            status = "PENDING"
        )

        coEvery { messageRepository.getPendingById("msg_1") } returns pendingMsg
        coEvery { messageRepository.updatePendingStatus(any(), any()) } returns Unit

        val result = useCase("msg_1")

        assertEquals(DispatchMessageUseCase.DispatchOutcome.Expired("msg_1"), result)
        coVerify { messageRepository.updatePendingStatus("msg_1", "EXPIRED") }
        coVerify(exactly = 0) { messageDispatcherService.dispatch(any(), any()) }
        coVerify {
            activityLogRepository.record(match {
                it.title == "Dispatch expired" &&
                    it.severity == ActivityLogSeverity.WARNING.raw &&
                    it.status == ActivityLogStatus.RESOLVED.raw &&
                    it.metadataJson.contains("\"decision\":\"${DispatchActivityDecision.EXPIRED.raw}\"")
            })
        }
    }

    @Test
    fun `invoke with already sent message records blocked activity`() = runTest {
        val pendingMsg = PendingMessageEntity(
            id = "msg_1",
            contactId = "c1",
            eventId = "e1",
            shortVariant = "", standardVariant = "hi", longVariant = "",
            formalVariant = "", funnyVariant = "", emotionalVariant = "",
            selectedVariant = "standard", selectedVariantText = "hi",
            channel = MessageChannel.SMS.raw, scheduledForMs = 0, approvalMode = "ALWAYS_ASK",
            status = "SENT"
        )
        coEvery { messageRepository.getPendingById("msg_1") } returns pendingMsg

        val result = useCase("msg_1")

        assertTrue(result is DispatchMessageUseCase.DispatchOutcome.NotApproved)
        coVerify(exactly = 0) { messageDispatcherService.dispatch(any(), any()) }
        coVerify {
            activityLogRepository.record(match {
                it.title == "Dispatch blocked" &&
                    it.detail == "Message was already handled." &&
                    it.metadataJson.contains("\"decision\":\"${DispatchActivityDecision.BLOCKED.raw}\"") &&
                    it.metadataJson.contains("\"reason\":\"ALREADY_HANDLED\"")
            })
        }
    }
}
