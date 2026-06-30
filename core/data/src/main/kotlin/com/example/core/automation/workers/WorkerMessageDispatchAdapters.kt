package com.example.core.automation.workers

import androidx.work.Data
import com.example.core.automation.scheduler.saveExactSendScheduleUpdate
import com.example.core.automation.sender.dispatchAttemptOutcomeUpdate
import com.example.core.automation.sender.saveDispatchAttemptOutcome
import com.example.core.db.dao.ContactDao
import com.example.core.db.dao.DispatchAttemptDao
import com.example.core.db.dao.PendingMessageDao
import com.example.domain.contact.toMessageDispatchRecipient
import com.example.domain.message.toMessageDispatchState
import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.MessageStatus
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.dispatch.DispatchAttemptOutcomeUpdate
import com.example.domain.model.dispatch.DispatchAttemptResult
import com.example.domain.model.dispatch.MessageDispatchRecipient
import com.example.domain.model.message.ExactSendCommand
import com.example.domain.model.message.ExactSendScheduleUpdate
import com.example.domain.model.message.MessageDispatchState
import com.example.domain.model.message.MessageDispatchWorkerInputCommand

internal fun Data.toMessageDispatchWorkerInputCommand(): MessageDispatchWorkerInputCommand? {
    return toMessageDispatchWorkerInputCommand(
        pendingMessageId = getString(MessageDispatchWorkRequests.KEY_PENDING_MESSAGE_ID),
        eventId = getString(MessageDispatchWorkRequests.KEY_EVENT_ID),
    )
}

internal fun toMessageDispatchWorkerInputCommand(
    pendingMessageId: String?,
    eventId: String?,
): MessageDispatchWorkerInputCommand? {
    val messageId = pendingMessageId.takeUnless { it.isNullOrBlank() }
    val occasionId = eventId.takeUnless { it.isNullOrBlank() }
    return when {
        messageId != null -> MessageDispatchWorkerInputCommand.PendingMessage(
            messageId = MessageDraftId(messageId),
            occasionId = occasionId?.let(::OccasionId),
        )
        occasionId != null -> MessageDispatchWorkerInputCommand.LegacyOccasion(
            occasionId = OccasionId(occasionId),
        )
        else -> null
    }
}

internal fun MessageDispatchWorkerInputCommand.logFields(): Map<String, String> {
    return when (this) {
        is MessageDispatchWorkerInputCommand.PendingMessage -> mapOf(
            "pendingMessageId" to messageId.value,
            "eventId" to (occasionId?.value ?: ""),
        )
        is MessageDispatchWorkerInputCommand.LegacyOccasion -> mapOf(
            "pendingMessageId" to "",
            "eventId" to occasionId.value,
        )
    }
}

internal suspend fun PendingMessageDao.getMessageDispatchState(
    command: MessageDispatchWorkerInputCommand,
): MessageDispatchState? {
    val message = when (command) {
        is MessageDispatchWorkerInputCommand.PendingMessage -> getById(command.messageId.value)
        is MessageDispatchWorkerInputCommand.LegacyOccasion -> getByEventId(command.occasionId.value)
    }
    return message?.toMessageDispatchState()
}

internal suspend fun PendingMessageDao.saveMessageDispatchDeferralScheduleUpdate(
    update: ExactSendScheduleUpdate,
) {
    saveExactSendScheduleUpdate(update)
}

internal fun MessageDispatchState.toExactSendCommand(): ExactSendCommand {
    return ExactSendCommand(messageId = id)
}

internal fun MessageDispatchState.toExactSendScheduleUpdate(scheduledForMs: Long): ExactSendScheduleUpdate {
    return ExactSendScheduleUpdate(
        messageId = id,
        scheduledForMs = scheduledForMs,
    )
}

internal suspend fun PendingMessageDao.claimMessageDispatching(
    pending: MessageDispatchState,
): Boolean {
    return updateStatusIfCurrent(
        id = pending.id.value,
        expectedStatus = pending.status.raw,
        newStatus = MessageStatus.DISPATCHING.raw,
    ) > 0
}

internal fun messageDispatchExceptionOutcomeUpdate(
    dispatchAttemptId: String,
    failedAtMs: Long,
    exception: Exception,
): DispatchAttemptOutcomeUpdate {
    return requireNotNull(
        dispatchAttemptOutcomeUpdate(
            dispatchAttemptId = dispatchAttemptId,
            resolvedAtMs = failedAtMs,
            result = DispatchAttemptResult.FAILED_FINAL,
            channel = null,
            deliveryStatus = MessageDeliveryStatus.FAILED,
            errorType = exception::class.simpleName ?: "DISPATCH_EXCEPTION",
            errorCode = null,
            redactedErrorMessage = "Dispatcher failed before completing send.",
            deadLetteredAtMs = failedAtMs,
            nextRetryAtMs = null,
        )
    )
}

internal suspend fun DispatchAttemptDao.saveMessageDispatchExceptionOutcome(update: DispatchAttemptOutcomeUpdate) {
    saveDispatchAttemptOutcome(update)
}

internal suspend fun ContactDao.getMessageDispatchRecipientById(
    contactId: String,
): MessageDispatchRecipient? {
    return getById(contactId)?.toMessageDispatchRecipient()
}
