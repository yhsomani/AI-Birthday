package com.example.core.automation.notifications

import android.content.Context
import androidx.work.WorkManager
import com.example.core.automation.scheduler.DailyScheduler
import com.example.core.automation.workers.MessageDispatchWorkRequests
import com.example.domain.model.common.OccasionId
import com.example.domain.model.message.ExactSendCommand
import com.example.domain.model.message.LegacyExactSendCancelCommand
import com.example.domain.model.message.MessageDispatchWorkCommand
import com.example.domain.model.message.MessageDraft

internal fun MessageDraft.toMessageDispatchWorkCommand(): MessageDispatchWorkCommand {
    return MessageDispatchWorkCommand(
        messageId = id,
        occasionId = occasionId,
    )
}

internal fun MessageDraft.toExactSendCommand(): ExactSendCommand {
    return ExactSendCommand(messageId = id)
}

internal fun String.toLegacyExactSendCancelCommand(): LegacyExactSendCancelCommand? {
    return takeIf { it.isNotBlank() }?.let { LegacyExactSendCancelCommand(OccasionId(it)) }
}

internal fun Context.enqueueMessageDispatchWork(command: MessageDispatchWorkCommand) {
    WorkManager.getInstance(this)
        .enqueue(
            MessageDispatchWorkRequests.create(
                pendingMessageId = command.messageId.value,
                eventId = command.occasionId.value,
            )
        )
}

internal fun Context.scheduleExactSend(command: ExactSendCommand) {
    DailyScheduler.scheduleExactSend(this, command.messageId.value)
}

internal fun Context.cancelExactSend(command: ExactSendCommand) {
    DailyScheduler.cancelExactSend(this, command.messageId.value)
}

internal fun Context.cancelLegacyExactSend(command: LegacyExactSendCancelCommand) {
    DailyScheduler.cancelLegacyExactSend(this, command.occasionId.value)
}
