package com.example.core.automation.scheduler

import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.entities.PendingMessageEntity
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageStatus
import com.example.domain.model.message.ExactSendCommand
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.message.ExactSendScheduleUpdate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DailySchedulerMessageAdaptersTest {
    private val pendingMessageDao: PendingMessageDao = mockk(relaxed = true)

    @Test
    fun getBootRecoverableExactSendCommands_mapsBootRecoverableRowsToTypedCommands() = runTest {
        coEvery { pendingMessageDao.getBootRecoverableAutoSends() } returns listOf(
            pendingMessage(id = "msg_1"),
            pendingMessage(id = "msg_2"),
        )

        val commands = pendingMessageDao.getBootRecoverableExactSendCommands()

        assertEquals(
            listOf(
                ExactSendCommand(MessageDraftId("msg_1")),
                ExactSendCommand(MessageDraftId("msg_2")),
            ),
            commands,
        )
    }

    @Test
    fun getExactSendScheduleState_mapsPendingRowToPureScheduleState() = runTest {
        coEvery { pendingMessageDao.getById("msg_1") } returns pendingMessage()

        val state = pendingMessageDao.getExactSendScheduleState("msg_1")

        assertEquals(MessageDraftId("msg_1"), state?.messageId)
        assertEquals(OccasionId("event_1"), state?.occasionId)
        assertEquals(1_800_000_000_000L, state?.scheduledForMs)
    }

    @Test
    fun saveExactSendScheduleUpdate_mapsTypedUpdateToTargetedDaoWrite() = runTest {
        pendingMessageDao.saveExactSendScheduleUpdate(
            ExactSendScheduleUpdate(
                messageId = MessageDraftId("msg_1"),
                scheduledForMs = 1_900_000_000_000L,
            )
        )

        coVerify {
            pendingMessageDao.updateScheduledFor(
                id = "msg_1",
                scheduledForMs = 1_900_000_000_000L,
            )
        }
    }

    @Test
    fun scheduleUpdate_keepsMessageIdAndChangesScheduleTime() = runTest {
        coEvery { pendingMessageDao.getById("msg_1") } returns pendingMessage()
        val state = pendingMessageDao.getExactSendScheduleState("msg_1")

        val update = state?.scheduleUpdate(1_900_000_000_000L)

        assertEquals(MessageDraftId("msg_1"), update?.messageId)
        assertEquals(1_900_000_000_000L, update?.scheduledForMs)
    }

    private fun pendingMessage(
        id: String = "msg_1",
    ): PendingMessageEntity {
        return PendingMessageEntity(
            id = id,
            contactId = "contact_1",
            eventId = "event_1",
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
            approvalMode = ApprovalMode.FULLY_AUTO.raw,
            status = MessageStatus.APPROVED.raw,
        )
    }
}
