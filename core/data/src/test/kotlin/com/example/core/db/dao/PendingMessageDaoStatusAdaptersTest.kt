package com.example.core.db.dao

import com.example.domain.model.MessageStatus
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.message.MessageStatusUpdate
import com.example.domain.model.message.MessageStatusUpdateByOccasion
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PendingMessageDaoStatusAdaptersTest {
    private val pendingMessageDao: PendingMessageDao = mockk(relaxed = true)

    @Test
    fun saveMessageStatusUpdate_mapsTypedStatusUpdateToRawDaoWrite() = runTest {
        pendingMessageDao.saveMessageStatusUpdate(
            MessageStatusUpdate(
                id = MessageDraftId("msg_1"),
                status = MessageStatus.FAILED,
            )
        )

        coVerify {
            pendingMessageDao.updateStatus(
                id = "msg_1",
                status = MessageStatus.FAILED.raw,
            )
        }
    }

    @Test
    fun saveMessageStatusUpdateByOccasion_mapsTypedStatusUpdateToLegacyEventDaoWrite() = runTest {
        pendingMessageDao.saveMessageStatusUpdateByOccasion(
            MessageStatusUpdateByOccasion(
                occasionId = OccasionId("event_1"),
                status = MessageStatus.REJECTED,
            )
        )

        coVerify {
            pendingMessageDao.updateStatusByEventId(
                eventId = "event_1",
                status = MessageStatus.REJECTED.raw,
            )
        }
    }
}
