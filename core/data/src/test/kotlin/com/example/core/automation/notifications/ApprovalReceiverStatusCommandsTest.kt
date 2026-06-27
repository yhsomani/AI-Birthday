package com.example.core.automation.notifications

import com.example.core.db.dao.PendingMessageDao
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.message.MessageDraft
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ApprovalReceiverStatusCommandsTest {
    private val pendingMessageDao: PendingMessageDao = mockk(relaxed = true)

    @Test
    fun savePendingMessageStatus_mapsDraftToTypedStatusUpdate() = runTest {
        pendingMessageDao.savePendingMessageStatus(
            pending = pendingMessage(),
            status = MessageStatus.APPROVED,
        )

        coVerify {
            pendingMessageDao.updateStatus(
                id = "msg_1",
                status = MessageStatus.APPROVED.raw,
            )
        }
    }

    @Test
    fun savePendingMessageStatusByOccasion_mapsOccasionToTypedStatusUpdate() = runTest {
        pendingMessageDao.savePendingMessageStatusByOccasion(
            occasionId = "event_1",
            status = MessageStatus.REJECTED,
        )

        coVerify {
            pendingMessageDao.updateStatusByEventId(
                eventId = "event_1",
                status = MessageStatus.REJECTED.raw,
            )
        }
    }

    @Test
    fun savePendingMessageStatusByOccasion_ignoresBlankOccasion() = runTest {
        pendingMessageDao.savePendingMessageStatusByOccasion(
            occasionId = " ",
            status = MessageStatus.REJECTED,
        )

        coVerify(exactly = 0) { pendingMessageDao.updateStatusByEventId(any(), any()) }
    }

    private fun pendingMessage(): MessageDraft {
        return MessageDraft(
            id = MessageDraftId("msg_1"),
            contactId = ContactId("contact_1"),
            occasionId = OccasionId("event_1"),
            scheduledForMs = 1_800_000_000_000L,
            approvalMode = ApprovalMode.ALWAYS_ASK,
            status = MessageStatus.PENDING,
            channel = MessageChannel.SMS,
            scheduledYear = 2026,
            qualityScore = 0,
            isUsingFallback = false,
        )
    }
}
