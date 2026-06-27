package com.example.core.automation.scheduler

import com.example.core.automation.workers.MessageDispatchWorkRequests
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessageDispatchReceiverWorkCommandsTest {
    @Test
    fun toMessageDispatchReceiverWorkCommand_mapsPendingMessageWithOccasion() {
        val command = toMessageDispatchReceiverWorkCommand(
            pendingMessageId = "msg_1",
            eventId = "event_1",
        )

        assertEquals(
            MessageDispatchReceiverWorkCommand.PendingMessage(
                messageId = MessageDraftId("msg_1"),
                occasionId = OccasionId("event_1"),
            ),
            command,
        )
    }

    @Test
    fun toMessageDispatchReceiverWorkCommand_mapsPendingMessageWithoutOccasion() {
        val command = toMessageDispatchReceiverWorkCommand(
            pendingMessageId = "msg_1",
            eventId = " ",
        )

        assertEquals(
            MessageDispatchReceiverWorkCommand.PendingMessage(
                messageId = MessageDraftId("msg_1"),
                occasionId = null,
            ),
            command,
        )
    }

    @Test
    fun toMessageDispatchReceiverWorkCommand_mapsLegacyOccasionWhenPendingIdMissing() {
        val command = toMessageDispatchReceiverWorkCommand(
            pendingMessageId = null,
            eventId = "event_1",
        )

        assertEquals(
            MessageDispatchReceiverWorkCommand.LegacyOccasion(OccasionId("event_1")),
            command,
        )
    }

    @Test
    fun toMessageDispatchReceiverWorkCommand_prefersPendingMessageWhenBothIdsExist() {
        val command = toMessageDispatchReceiverWorkCommand(
            pendingMessageId = "msg_1",
            eventId = "event_1",
        )

        assertEquals(MessageDispatchReceiverWorkCommand.PendingMessage::class, command?.javaClass?.kotlin)
    }

    @Test
    fun toMessageDispatchReceiverWorkCommand_ignoresMissingIds() {
        assertNull(toMessageDispatchReceiverWorkCommand(pendingMessageId = null, eventId = null))
        assertNull(toMessageDispatchReceiverWorkCommand(pendingMessageId = " ", eventId = " "))
    }

    @Test
    fun toOneTimeWorkRequest_mapsPendingMessageCommandToWorkInput() {
        val request = MessageDispatchReceiverWorkCommand.PendingMessage(
            messageId = MessageDraftId("msg_1"),
            occasionId = OccasionId("event_1"),
        ).toOneTimeWorkRequest()

        assertEquals("msg_1", request.workSpec.input.getString(MessageDispatchWorkRequests.KEY_PENDING_MESSAGE_ID))
        assertEquals("event_1", request.workSpec.input.getString(MessageDispatchWorkRequests.KEY_EVENT_ID))
    }

    @Test
    fun toOneTimeWorkRequest_mapsLegacyOccasionCommandToWorkInput() {
        val request = MessageDispatchReceiverWorkCommand.LegacyOccasion(
            occasionId = OccasionId("event_1"),
        ).toOneTimeWorkRequest()

        assertNull(request.workSpec.input.getString(MessageDispatchWorkRequests.KEY_PENDING_MESSAGE_ID))
        assertEquals("event_1", request.workSpec.input.getString(MessageDispatchWorkRequests.KEY_EVENT_ID))
    }
}
