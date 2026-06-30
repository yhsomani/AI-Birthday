package com.example.core.automation.scheduler

import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.example.core.automation.workers.MessageDispatchWorkRequests
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId

internal sealed interface MessageDispatchReceiverWorkCommand {
    data class PendingMessage(
        val messageId: MessageDraftId,
        val occasionId: OccasionId?,
    ) : MessageDispatchReceiverWorkCommand

    data class LegacyOccasion(
        val occasionId: OccasionId,
    ) : MessageDispatchReceiverWorkCommand
}

internal fun Intent.toMessageDispatchReceiverWorkCommand(): MessageDispatchReceiverWorkCommand? {
    return toMessageDispatchReceiverWorkCommand(
        pendingMessageId = getStringExtra(MessageDispatchWorkRequests.KEY_PENDING_MESSAGE_ID),
        eventId = getStringExtra(MessageDispatchWorkRequests.KEY_EVENT_ID),
    )
}

internal fun toMessageDispatchReceiverWorkCommand(
    pendingMessageId: String?,
    eventId: String?,
): MessageDispatchReceiverWorkCommand? {
    val messageId = pendingMessageId.takeUnless { it.isNullOrBlank() }
    val occasionId = eventId.takeUnless { it.isNullOrBlank() }
    return when {
        messageId != null -> MessageDispatchReceiverWorkCommand.PendingMessage(
            messageId = MessageDraftId(messageId),
            occasionId = occasionId?.let(::OccasionId),
        )
        occasionId != null -> MessageDispatchReceiverWorkCommand.LegacyOccasion(
            occasionId = OccasionId(occasionId),
        )
        else -> null
    }
}

internal fun WorkManager.enqueueMessageDispatchReceiverWork(command: MessageDispatchReceiverWorkCommand) {
    when (command) {
        is MessageDispatchReceiverWorkCommand.PendingMessage -> enqueueUniquePendingMessageDispatchWork(
            pendingMessageId = command.messageId.value,
            eventId = command.occasionId?.value,
            existingWorkPolicy = ExistingWorkPolicy.KEEP,
        )
        is MessageDispatchReceiverWorkCommand.LegacyOccasion -> enqueue(command.toOneTimeWorkRequest())
    }
}

internal fun MessageDispatchReceiverWorkCommand.toOneTimeWorkRequest(): OneTimeWorkRequest {
    return when (this) {
        is MessageDispatchReceiverWorkCommand.PendingMessage -> MessageDispatchWorkRequests.create(
            pendingMessageId = messageId.value,
            eventId = occasionId?.value,
        )
        is MessageDispatchReceiverWorkCommand.LegacyOccasion -> MessageDispatchWorkRequests.createForEvent(
            eventId = occasionId.value,
        )
    }
}

internal fun WorkManager.enqueueUniquePendingMessageDispatchWork(
    pendingMessageId: String,
    eventId: String? = null,
    initialDelayMs: Long = 0L,
    existingWorkPolicy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP,
) {
    beginUniqueWork(
        pendingMessageDispatchUniqueWorkName(pendingMessageId),
        existingWorkPolicy,
        MessageDispatchWorkRequests.create(
            pendingMessageId = pendingMessageId,
            eventId = eventId,
            initialDelayMs = initialDelayMs,
        ),
    ).enqueue()
}

internal fun pendingMessageDispatchUniqueWorkName(pendingMessageId: String): String {
    return "message_dispatch_$pendingMessageId"
}
