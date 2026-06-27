package com.example.core.automation.scheduler

import com.example.core.db.dao.PendingMessageDao
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.message.ExactSendCommand
import com.example.domain.model.message.ExactSendScheduleState
import com.example.domain.model.message.ExactSendScheduleUpdate

internal suspend fun PendingMessageDao.getBootRecoverableExactSendCommands(): List<ExactSendCommand> {
    return getBootRecoverableAutoSends().map { pending ->
        ExactSendCommand(messageId = MessageDraftId(pending.id))
    }
}

internal suspend fun PendingMessageDao.getExactSendScheduleState(
    pendingMessageId: String,
): ExactSendScheduleState? {
    return getById(pendingMessageId)?.let { pending ->
        ExactSendScheduleState(
            messageId = MessageDraftId(pending.id),
            occasionId = OccasionId(pending.eventId),
            scheduledForMs = pending.scheduledForMs,
        )
    }
}

internal suspend fun PendingMessageDao.saveExactSendScheduleUpdate(
    update: ExactSendScheduleUpdate,
) {
    updateScheduledFor(
        id = update.messageId.value,
        scheduledForMs = update.scheduledForMs,
    )
}
