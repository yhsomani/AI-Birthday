package com.example.core.automation.notifications

import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.entities.PendingMessageEntity
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ApprovalReceiverMessageAdaptersTest {
    private val pendingMessageDao: PendingMessageDao = mockk(relaxed = true)

    @Test
    fun getApprovalNotificationDraftByIdOrOccasion_mapsMessageIdLookupToPureDraft() = runTest {
        coEvery { pendingMessageDao.getById("msg_1") } returns pendingMessage()

        val draft = pendingMessageDao.getApprovalNotificationDraftByIdOrOccasion(
            messageId = "msg_1",
            eventId = "event_ignored",
        )

        assertEquals(MessageDraftId("msg_1"), draft?.id)
        assertEquals(ContactId("contact_1"), draft?.contactId)
        assertEquals(OccasionId("event_1"), draft?.occasionId)
        assertEquals(ApprovalMode.ALWAYS_ASK, draft?.approvalMode)
        assertEquals(MessageStatus.PENDING, draft?.status)
        assertEquals(MessageChannel.SMS, draft?.channel)
        coVerify(exactly = 0) { pendingMessageDao.getByEventId(any()) }
    }

    @Test
    fun getApprovalNotificationDraftByIdOrOccasion_fallsBackToEventLookupWhenMessageIdMissing() = runTest {
        coEvery { pendingMessageDao.getByEventId("event_1") } returns pendingMessage(id = "msg_event")

        val draft = pendingMessageDao.getApprovalNotificationDraftByIdOrOccasion(
            messageId = "",
            eventId = "event_1",
        )

        assertEquals(MessageDraftId("msg_event"), draft?.id)
        assertEquals(OccasionId("event_1"), draft?.occasionId)
    }

    @Test
    fun getApprovalNotificationDraftByIdOrOccasion_returnsNullWhenInputsMissing() = runTest {
        val draft = pendingMessageDao.getApprovalNotificationDraftByIdOrOccasion(
            messageId = " ",
            eventId = " ",
        )

        assertEquals(null, draft)
        coVerify(exactly = 0) { pendingMessageDao.getById(any()) }
        coVerify(exactly = 0) { pendingMessageDao.getByEventId(any()) }
    }

    private fun pendingMessage(
        id: String = "msg_1",
        eventId: String = "event_1",
    ): PendingMessageEntity {
        return PendingMessageEntity(
            id = id,
            contactId = "contact_1",
            eventId = eventId,
            shortVariant = "Short",
            standardVariant = "Standard",
            longVariant = "Long",
            formalVariant = "Formal",
            funnyVariant = "Funny",
            emotionalVariant = "Emotional",
            selectedVariant = "standard",
            selectedVariantText = "Standard",
            channel = MessageChannel.SMS.raw,
            scheduledForMs = 1_800_000_000_000L,
            approvalMode = ApprovalMode.ALWAYS_ASK.raw,
            status = MessageStatus.PENDING.raw,
        )
    }
}
