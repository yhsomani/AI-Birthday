package com.example.core.automation.scheduler

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import androidx.work.WorkContinuation
import androidx.work.WorkManager
import com.example.core.automation.workers.MessageDispatchWorkRequests
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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

    @Test
    fun pendingMessageDispatchUniqueWorkName_isStablePerMessage() {
        assertEquals("message_dispatch_msg_1", pendingMessageDispatchUniqueWorkName("msg_1"))
    }

    @Test
    fun enqueueMessageDispatchReceiverWork_usesUniqueWorkForPendingMessage() {
        val workManager = mockWorkManager()

        workManager.manager.enqueueMessageDispatchReceiverWork(
            MessageDispatchReceiverWorkCommand.PendingMessage(
                messageId = MessageDraftId("msg_1"),
                occasionId = OccasionId("event_1"),
            )
        )

        verify {
            workManager.manager.beginUniqueWork(
                "message_dispatch_msg_1",
                ExistingWorkPolicy.KEEP,
                any<OneTimeWorkRequest>(),
            )
        }
        verify { workManager.continuation.enqueue() }
        verify(exactly = 0) { workManager.manager.enqueue(any<OneTimeWorkRequest>()) }
    }

    @Test
    fun enqueueMessageDispatchReceiverWork_keepsLegacyOccasionWorkNonUnique() {
        val workManager = mockWorkManager()

        workManager.manager.enqueueMessageDispatchReceiverWork(
            MessageDispatchReceiverWorkCommand.LegacyOccasion(
                occasionId = OccasionId("event_1"),
            )
        )

        verify { workManager.manager.enqueue(any<OneTimeWorkRequest>()) }
        verify(exactly = 0) {
            workManager.manager.beginUniqueWork(any(), any(), any<OneTimeWorkRequest>())
        }
    }

    private fun mockWorkManager(): WorkManagerMocks {
        val manager = mockk<WorkManager>(relaxed = true)
        val continuation = mockk<WorkContinuation>(relaxed = true)
        val operation = mockk<Operation>(relaxed = true)

        every {
            manager.beginUniqueWork(any(), any(), any<OneTimeWorkRequest>())
        } returns continuation
        every { manager.enqueue(any<OneTimeWorkRequest>()) } returns operation
        every { continuation.enqueue() } returns operation

        return WorkManagerMocks(manager, continuation)
    }

    private data class WorkManagerMocks(
        val manager: WorkManager,
        val continuation: WorkContinuation,
    )
}
