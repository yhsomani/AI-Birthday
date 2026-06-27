package com.example.core.automation.notifications

import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.dao.saveMessageStatusUpdate
import com.example.core.db.dao.saveMessageStatusUpdateByOccasion
import com.example.domain.model.MessageStatus
import com.example.domain.model.common.OccasionId
import com.example.domain.model.message.MessageDraft
import com.example.domain.model.message.MessageStatusUpdate
import com.example.domain.model.message.MessageStatusUpdateByOccasion

internal suspend fun PendingMessageDao.savePendingMessageStatus(
    pending: MessageDraft,
    status: MessageStatus,
) {
    saveMessageStatusUpdate(
        MessageStatusUpdate(
            id = pending.id,
            status = status,
        )
    )
}

internal suspend fun PendingMessageDao.savePendingMessageStatusByOccasion(
    occasionId: String,
    status: MessageStatus,
) {
    if (occasionId.isBlank()) return
    saveMessageStatusUpdateByOccasion(
        MessageStatusUpdateByOccasion(
            occasionId = OccasionId(occasionId),
            status = status,
        )
    )
}
