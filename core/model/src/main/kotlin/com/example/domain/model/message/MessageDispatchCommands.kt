package com.example.domain.model.message

import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId

data class MessageDispatchWorkCommand(
    val messageId: MessageDraftId,
    val occasionId: OccasionId,
)

sealed interface MessageDispatchWorkerInputCommand {
    data class PendingMessage(
        val messageId: MessageDraftId,
        val occasionId: OccasionId?,
    ) : MessageDispatchWorkerInputCommand

    data class LegacyOccasion(
        val occasionId: OccasionId,
    ) : MessageDispatchWorkerInputCommand
}

data class ExactSendCommand(
    val messageId: MessageDraftId,
)

data class LegacyExactSendCancelCommand(
    val occasionId: OccasionId,
)

data class ExactSendScheduleState(
    val messageId: MessageDraftId,
    val occasionId: OccasionId,
    val scheduledForMs: Long,
) {
    fun scheduleUpdate(scheduledForMs: Long): ExactSendScheduleUpdate {
        return ExactSendScheduleUpdate(
            messageId = messageId,
            scheduledForMs = scheduledForMs,
        )
    }
}

data class ExactSendScheduleUpdate(
    val messageId: MessageDraftId,
    val scheduledForMs: Long,
)
