package com.example.core.db.dao

import com.example.domain.model.message.MessageStatusUpdate
import com.example.domain.model.message.MessageStatusUpdateByOccasion

internal suspend fun PendingMessageDao.saveMessageStatusUpdate(update: MessageStatusUpdate) {
    updateStatus(
        id = update.id.value,
        status = update.status.raw,
    )
}

internal suspend fun PendingMessageDao.saveMessageStatusUpdateByOccasion(update: MessageStatusUpdateByOccasion) {
    updateStatusByEventId(
        eventId = update.occasionId.value,
        status = update.status.raw,
    )
}
