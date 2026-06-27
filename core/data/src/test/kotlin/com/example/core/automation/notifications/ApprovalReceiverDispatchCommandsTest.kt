package com.example.core.automation.notifications

import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.message.MessageDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApprovalReceiverDispatchCommandsTest {
    @Test
    fun toMessageDispatchWorkCommand_mapsDraftIdsToWorkCommand() {
        val command = messageDraft().toMessageDispatchWorkCommand()

        assertEquals(MessageDraftId("msg_1"), command.messageId)
        assertEquals(OccasionId("event_1"), command.occasionId)
    }

    @Test
    fun toExactSendCommand_mapsDraftIdToExactSendCommand() {
        val command = messageDraft().toExactSendCommand()

        assertEquals(MessageDraftId("msg_1"), command.messageId)
    }

    @Test
    fun toLegacyExactSendCancelCommand_mapsNonBlankOccasionId() {
        val command = "event_1".toLegacyExactSendCancelCommand()

        assertEquals(OccasionId("event_1"), command?.occasionId)
    }

    @Test
    fun toLegacyExactSendCancelCommand_ignoresBlankOccasionId() {
        assertNull(" ".toLegacyExactSendCancelCommand())
    }

    private fun messageDraft(): MessageDraft {
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
