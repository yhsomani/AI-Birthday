package com.example.core.automation.notifications

import com.example.core.db.dao.PendingMessageDao
import com.example.domain.message.toMessageDraft
import com.example.domain.model.message.MessageDraft

internal suspend fun PendingMessageDao.getApprovalNotificationDraftByIdOrOccasion(
    messageId: String,
    eventId: String,
): MessageDraft? {
    val pending = if (messageId.isNotBlank()) {
        getById(messageId)
    } else if (eventId.isNotBlank()) {
        getByEventId(eventId)
    } else {
        null
    }
    return pending?.toMessageDraft()
}
