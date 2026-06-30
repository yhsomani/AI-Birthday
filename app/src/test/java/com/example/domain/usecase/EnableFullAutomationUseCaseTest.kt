package com.example.domain.usecase

import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.PendingMessageEntity
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.dispatch.MessageDispatchRecipient
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.MessageRepository
import com.example.domain.service.PreferencesRepository
import com.example.domain.service.SchedulerService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class EnableFullAutomationUseCaseTest {

    private val preferencesRepository: PreferencesRepository = mockk(relaxed = true)
    private val messageRepository: MessageRepository = mockk(relaxed = true)
    private val contactRepository: ContactRepository = mockk(relaxed = true)
    private val schedulerService: SchedulerService = mockk(relaxed = true)

    private val useCase = EnableFullAutomationUseCase(
        preferencesRepository = preferencesRepository,
        messageRepository = messageRepository,
        contactRepository = contactRepository,
        schedulerService = schedulerService,
    )

    @Test
    fun `invoke enables full auto and schedules queued routable messages`() = runTest {
        val promoted = slot<PendingMessageEntity>()
        val updatedContact = slot<ContactEntity>()
        var promotedMessageScheduled = false
        every { preferencesRepository.getChannelBlackout() } returns "[]"
        every { preferencesRepository.getSenderEmail() } returns ""
        every { preferencesRepository.getSenderEmailPassword() } returns ""
        every { schedulerService.scheduleExactSend(any()) } answers {
            promotedMessageScheduled = true
        }
        every { preferencesRepository.setGlobalAutomationMode(ApprovalMode.FULLY_AUTO) } answers {
            assertTrue(
                "Global Fully Auto should be committed after existing queued messages are scheduled.",
                promotedMessageScheduled,
            )
        }
        coEvery { contactRepository.getAllSync() } returns listOf(
            ContactEntity(
                id = "contact_1",
                name = "Taylor",
                automationMode = ApprovalMode.VIP_APPROVE.raw,
                skipAutoWish = true,
            ),
        )
        coEvery { messageRepository.getAllPendingSync() } returns listOf(
            pendingMessage(
                id = "pending_1",
                contactId = "contact_1",
                approvalMode = ApprovalMode.VIP_APPROVE,
            ),
        )
        coEvery { contactRepository.getMessageDispatchRecipient("contact_1") } returns recipient(
            id = "contact_1",
            phone = "+15551234567",
        )

        val outcome = useCase()

        assertEquals(1, outcome.updatedContacts)
        assertEquals(1, outcome.promotedMessages)
        assertEquals(0, outcome.skippedWithoutRoute)
        verify { preferencesRepository.setGlobalAutomationMode(ApprovalMode.FULLY_AUTO) }
        coVerify { contactRepository.update(capture(updatedContact)) }
        assertEquals(ApprovalMode.DEFAULT.raw, updatedContact.captured.automationMode)
        assertEquals(false, updatedContact.captured.skipAutoWish)
        coVerify { messageRepository.insertPending(capture(promoted)) }
        assertEquals(MessageStatus.APPROVED.raw, promoted.captured.status)
        assertEquals(ApprovalMode.FULLY_AUTO.raw, promoted.captured.approvalMode)
        assertEquals(
            "Happy birthday Taylor, still remember our Jaipur food walk. Hope this year brings more trips and good coffee.",
            promoted.captured.selectedVariantText,
        )
        verify { schedulerService.scheduleExactSend("pending_1") }
    }

    @Test
    fun `invoke does not persist full auto when preparation fails`() = runTest {
        every { preferencesRepository.getChannelBlackout() } returns "[]"
        every { preferencesRepository.getSenderEmail() } returns ""
        every { preferencesRepository.getSenderEmailPassword() } returns ""
        coEvery { contactRepository.getAllSync() } returns emptyList()
        coEvery { messageRepository.getAllPendingSync() } throws IllegalStateException("pending load failed")

        try {
            useCase()
            fail("Expected preparation failure to be propagated")
        } catch (expected: IllegalStateException) {
            assertEquals("pending load failed", expected.message)
        }

        verify(exactly = 0) { preferencesRepository.setGlobalAutomationMode(ApprovalMode.FULLY_AUTO) }
    }

    @Test
    fun `invoke skips queued messages without a delivery route and ignores handled messages`() = runTest {
        every { preferencesRepository.getChannelBlackout() } returns """["${MessageChannel.SMS.raw}","${MessageChannel.WHATSAPP.raw}"]"""
        every { preferencesRepository.getSenderEmail() } returns ""
        every { preferencesRepository.getSenderEmailPassword() } returns ""
        coEvery { contactRepository.getAllSync() } returns emptyList()
        coEvery { messageRepository.getAllPendingSync() } returns listOf(
            pendingMessage(id = "pending_1", contactId = "contact_1", approvalMode = ApprovalMode.ALWAYS_ASK),
            pendingMessage(id = "sent_1", contactId = "contact_2", status = MessageStatus.SENT),
        )
        coEvery { contactRepository.getMessageDispatchRecipient("contact_1") } returns recipient(
            id = "contact_1",
            phone = null,
            email = null,
        )

        val outcome = useCase()

        assertEquals(0, outcome.updatedContacts)
        assertEquals(0, outcome.promotedMessages)
        assertEquals(1, outcome.skippedWithoutRoute)
        verify { preferencesRepository.setGlobalAutomationMode(ApprovalMode.FULLY_AUTO) }
        coVerify(exactly = 0) { messageRepository.insertPending(any()) }
        verify(exactly = 0) { schedulerService.scheduleExactSend(any()) }
        coVerify(exactly = 0) { contactRepository.getMessageDispatchRecipient("contact_2") }
    }

    @Test
    fun `invoke skips email-only queued messages with invalid recipient email`() = runTest {
        every { preferencesRepository.getChannelBlackout() } returns
            """["${MessageChannel.SMS.raw}","${MessageChannel.WHATSAPP.raw}"]"""
        every { preferencesRepository.getSenderEmail() } returns "sender@example.com"
        every { preferencesRepository.getSenderEmailPassword() } returns "app-password"
        coEvery { contactRepository.getAllSync() } returns emptyList()
        coEvery { messageRepository.getAllPendingSync() } returns listOf(
            pendingMessage(
                id = "invalid_recipient",
                contactId = "contact_1",
                channel = MessageChannel.EMAIL,
            ),
        )
        coEvery { contactRepository.getMessageDispatchRecipient("contact_1") } returns recipient(
            id = "contact_1",
            email = "not an email",
        )

        val outcome = useCase()

        assertEquals(0, outcome.promotedMessages)
        assertEquals(1, outcome.skippedWithoutRoute)
        coVerify(exactly = 0) { messageRepository.insertPending(any()) }
        verify(exactly = 0) { schedulerService.scheduleExactSend(any()) }
    }

    @Test
    fun `invoke skips email-only queued messages with invalid sender email`() = runTest {
        every { preferencesRepository.getChannelBlackout() } returns
            """["${MessageChannel.SMS.raw}","${MessageChannel.WHATSAPP.raw}"]"""
        every { preferencesRepository.getSenderEmail() } returns "not an email"
        every { preferencesRepository.getSenderEmailPassword() } returns "app-password"
        coEvery { contactRepository.getAllSync() } returns emptyList()
        coEvery { messageRepository.getAllPendingSync() } returns listOf(
            pendingMessage(
                id = "invalid_sender",
                contactId = "contact_1",
                channel = MessageChannel.EMAIL,
            ),
        )
        coEvery { contactRepository.getMessageDispatchRecipient("contact_1") } returns recipient(
            id = "contact_1",
            email = "valid@example.com",
        )

        val outcome = useCase()

        assertEquals(0, outcome.promotedMessages)
        assertEquals(1, outcome.skippedWithoutRoute)
        coVerify(exactly = 0) { messageRepository.insertPending(any()) }
        verify(exactly = 0) { schedulerService.scheduleExactSend(any()) }
    }

    @Test
    fun `invoke promotes nonblank fallback drafts when a delivery route exists`() = runTest {
        val promoted = slot<PendingMessageEntity>()
        every { preferencesRepository.getChannelBlackout() } returns "[]"
        every { preferencesRepository.getSenderEmail() } returns ""
        every { preferencesRepository.getSenderEmailPassword() } returns ""
        coEvery { contactRepository.getAllSync() } returns emptyList()
        coEvery { messageRepository.getAllPendingSync() } returns listOf(
            pendingMessage(
                id = "generic_1",
                contactId = "contact_1",
                selectedText = "Wishing you a very happy birthday! Hope you have a wonderful day!",
                isUsingFallback = true,
            ),
        )
        coEvery { contactRepository.getMessageDispatchRecipient("contact_1") } returns recipient(
            id = "contact_1",
            phone = "+15551234567",
        )

        val outcome = useCase()

        assertEquals(1, outcome.promotedMessages)
        assertEquals(0, outcome.skippedWithoutRoute)
        assertEquals(0, outcome.skippedNeedsReview)
        coVerify { messageRepository.insertPending(capture(promoted)) }
        assertEquals(MessageStatus.APPROVED.raw, promoted.captured.status)
        assertEquals(ApprovalMode.FULLY_AUTO.raw, promoted.captured.approvalMode)
        verify { schedulerService.scheduleExactSend("generic_1") }
    }

    @Test
    fun `invoke leaves blank routable drafts in review instead of promoting`() = runTest {
        every { preferencesRepository.getChannelBlackout() } returns "[]"
        every { preferencesRepository.getSenderEmail() } returns ""
        every { preferencesRepository.getSenderEmailPassword() } returns ""
        coEvery { contactRepository.getAllSync() } returns emptyList()
        coEvery { messageRepository.getAllPendingSync() } returns listOf(
            pendingMessage(
                id = "blank_1",
                contactId = "contact_1",
                selectedText = "",
            ),
        )
        coEvery { contactRepository.getMessageDispatchRecipient("contact_1") } returns recipient(
            id = "contact_1",
            phone = "+15551234567",
        )

        val outcome = useCase()

        assertEquals(0, outcome.promotedMessages)
        assertEquals(0, outcome.skippedWithoutRoute)
        assertEquals(1, outcome.skippedNeedsReview)
        coVerify(exactly = 0) { messageRepository.insertPending(any()) }
        verify(exactly = 0) { schedulerService.scheduleExactSend(any()) }
    }

    private fun pendingMessage(
        id: String,
        contactId: String,
        status: MessageStatus = MessageStatus.PENDING,
        approvalMode: ApprovalMode = ApprovalMode.SMART_APPROVE,
        selectedText: String = "Happy birthday Taylor, still remember our Jaipur food walk. Hope this year brings more trips and good coffee.",
        isUsingFallback: Boolean = false,
        channel: MessageChannel = MessageChannel.SMS,
    ) = PendingMessageEntity(
        id = id,
        contactId = contactId,
        eventId = "event_$id",
        shortVariant = selectedText,
        standardVariant = selectedText,
        longVariant = selectedText,
        formalVariant = selectedText,
        funnyVariant = selectedText,
        emotionalVariant = selectedText,
        selectedVariantText = selectedText,
        channel = channel.raw,
        scheduledForMs = 0L,
        approvalMode = approvalMode.raw,
        status = status.raw,
        isUsingFallback = isUsingFallback,
    )

    private fun recipient(
        id: String,
        phone: String? = null,
        email: String? = null,
    ) = MessageDispatchRecipient(
        id = ContactId(id),
        displayName = "Taylor",
        primaryPhone = phone,
        primaryEmail = email,
    )
}
