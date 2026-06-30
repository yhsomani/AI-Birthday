package com.example.core.automation.sender

import com.example.core.db.dao.ContactDao
import com.example.core.db.dao.DispatchAttemptDao
import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.dao.SentMessageDao
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.SentMessageId
import com.example.domain.model.dispatch.MessageDispatchOccasion
import java.util.Calendar
import java.util.UUID

internal data class MessageDispatchFinalizationResult(
    val retryAtMs: Long?,
    val retryCount: Int,
) {
    val shouldScheduleRetry: Boolean
        get() = retryAtMs != null
}

internal suspend fun saveMessageDispatchFinalization(
    dispatchAttemptDao: DispatchAttemptDao?,
    pendingMessageDao: PendingMessageDao,
    sentMessageDao: SentMessageDao,
    contactDao: ContactDao?,
    messageId: MessageDraftId,
    contactId: ContactId,
    dispatchAttemptId: String?,
    preferredChannel: MessageChannel,
    finalChannel: MessageChannel,
    dispatchOccasion: MessageDispatchOccasion,
    messageText: String,
    routeLoopState: MessageDispatchRouteLoopState,
    noDeliveryRoute: Boolean,
    automaticRetryCount: Int = 1,
): MessageDispatchFinalizationResult {
    if (routeLoopState.success) {
        saveSuccessfulMessageDispatchFinalization(
            dispatchAttemptDao = dispatchAttemptDao,
            pendingMessageDao = pendingMessageDao,
            sentMessageDao = sentMessageDao,
            contactDao = contactDao,
            messageId = messageId,
            contactId = contactId,
            dispatchAttemptId = dispatchAttemptId,
            channel = finalChannel,
            dispatchOccasion = dispatchOccasion,
            messageText = messageText,
            sentMessageAlreadyInserted = routeLoopState.successfulSentMessageInserted,
        )
        return MessageDispatchFinalizationResult(retryAtMs = null, retryCount = 0)
    } else {
        return saveFailedMessageDispatchFinalization(
            dispatchAttemptDao = dispatchAttemptDao,
            pendingMessageDao = pendingMessageDao,
            messageId = messageId,
            dispatchAttemptId = dispatchAttemptId,
            preferredChannel = preferredChannel,
            finalChannel = finalChannel,
            messageText = messageText,
            providerFailureSelection = routeLoopState.providerFailureSelection,
            noDeliveryRoute = noDeliveryRoute,
            automaticRetryCount = automaticRetryCount,
        )
    }
}

internal suspend fun saveSuccessfulMessageDispatchFinalization(
    dispatchAttemptDao: DispatchAttemptDao?,
    pendingMessageDao: PendingMessageDao,
    sentMessageDao: SentMessageDao,
    contactDao: ContactDao?,
    messageId: MessageDraftId,
    contactId: ContactId,
    dispatchAttemptId: String?,
    channel: MessageChannel,
    dispatchOccasion: MessageDispatchOccasion,
    messageText: String,
    sentMessageAlreadyInserted: Boolean,
    resolvedAtMs: Long = System.currentTimeMillis(),
    sentAtMs: Long = System.currentTimeMillis(),
    wishedAtMs: Long = System.currentTimeMillis(),
    eventYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    sentMessageId: SentMessageId = SentMessageId(UUID.randomUUID().toString()),
) {
    dispatchAttemptDao.saveSuccessfulMessageDispatchAttemptOutcome(
        successfulDispatchAttemptOutcomeUpdate(
            dispatchAttemptId = dispatchAttemptId,
            resolvedAtMs = resolvedAtMs,
            channel = channel,
        )
    )
    recordMessageDispatchLifecycleLog(
        messageDispatchSucceededLog(
            messageId = messageId,
            channel = channel,
        )
    )
    pendingMessageDao.saveSuccessfulPendingMessageDispatchStatusUpdate(
        messageId = messageId,
        channel = channel,
    )
    if (!sentMessageAlreadyInserted) {
        sentMessageDao.saveSentMessageDispatchRecord(
            successfulSentMessageDispatchRecord(
                id = sentMessageId,
                contactId = contactId,
                dispatchOccasion = dispatchOccasion,
                eventYear = eventYear,
                messageText = messageText,
                channel = channel,
                sentAtMs = sentAtMs,
            )
        )
    }
    contactDao?.saveContactPostDispatchUpdate(
        contactPostDispatchUpdate(
            contactId = contactId,
            wishedAtMs = wishedAtMs,
        )
    )
}

internal suspend fun PendingMessageDao.saveSuccessfulPendingMessageDispatchStatusUpdate(
    messageId: MessageDraftId,
    channel: MessageChannel,
) {
    if (channel == MessageChannel.SMS) {
        markSmsHandoffSentIfAwaitingCallback(messageId.value)
    } else {
        savePendingMessageDispatchStatusUpdate(sentPendingMessageStatusUpdate(messageId))
    }
}

internal suspend fun saveFailedMessageDispatchFinalization(
    dispatchAttemptDao: DispatchAttemptDao?,
    pendingMessageDao: PendingMessageDao,
    messageId: MessageDraftId,
    dispatchAttemptId: String?,
    preferredChannel: MessageChannel,
    finalChannel: MessageChannel,
    messageText: String,
    providerFailureSelection: MessageDispatchProviderFailureSelection,
    noDeliveryRoute: Boolean,
    failedAtMs: Long? = null,
    automaticRetryCount: Int = 1,
): MessageDispatchFinalizationResult {
    val resolvedFailedAtMs = failedAtMs ?: System.currentTimeMillis()
    val baseFailure = failedMessageDispatchFailure(
        providerFailureSelection = providerFailureSelection,
        noDeliveryRoute = noDeliveryRoute,
    )
    val retryCount = if (baseFailure.isRetryable) automaticRetryCount.coerceAtLeast(1) else 0
    val failure = DispatchProviderRetryPolicy.applyAutomaticRetryLimit(
        failure = baseFailure,
        retryCount = retryCount,
    )
    val retryAtMs = failure.nextRetryDelayMs?.let { delayMs -> resolvedFailedAtMs + delayMs }
    if (failure.isRetryable && retryAtMs != null) {
        pendingMessageDao.updateRetryState(
            id = messageId.value,
            status = MessageStatus.APPROVED.raw,
            scheduledForMs = retryAtMs,
        )
    } else {
        pendingMessageDao.savePendingMessageDispatchStatusUpdate(
            failedPendingMessageStatusUpdate(messageId)
        )
    }
    dispatchAttemptDao.saveMessageDispatchAttemptOutcome(
        failedDispatchAttemptOutcomeUpdate(
            dispatchAttemptId = dispatchAttemptId,
            failedAtMs = resolvedFailedAtMs,
            channel = finalChannel,
            failure = failure,
            retryCount = retryCount,
        )
    )
    recordMessageDispatchLifecycleLog(
        messageDispatchFailedLog(
            messageId = messageId,
            preferredChannel = preferredChannel,
            failure = failure,
        )
    )
    recordMessageDispatchFailureSideEffects(
        messageDispatchFailureSideEffects(
            messageId = messageId,
            preferredChannel = preferredChannel,
            messageText = messageText,
            failure = failure,
            retryCount = retryCount,
        )
    )
    return MessageDispatchFinalizationResult(
        retryAtMs = retryAtMs.takeIf { failure.isRetryable },
        retryCount = retryCount,
    )
}

private fun failedMessageDispatchFailure(
    providerFailureSelection: MessageDispatchProviderFailureSelection,
    noDeliveryRoute: Boolean,
): ProviderDispatchFailure {
    return if (noDeliveryRoute) {
        DispatchProviderRetryPolicy.noDeliveryRoute()
    } else {
        providerFailureSelection.failureOrDispatchFailure()
    }
}
