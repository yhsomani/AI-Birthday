package com.example.core.automation.sender

import com.example.core.db.dao.ContactDao
import com.example.core.db.dao.DispatchAttemptDao
import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.dao.SentMessageDao
import com.example.domain.model.MessageChannel
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.SentMessageId
import com.example.domain.model.dispatch.MessageDispatchOccasion
import java.util.Calendar
import java.util.UUID

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
) {
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
    } else {
        saveFailedMessageDispatchFinalization(
            dispatchAttemptDao = dispatchAttemptDao,
            pendingMessageDao = pendingMessageDao,
            messageId = messageId,
            dispatchAttemptId = dispatchAttemptId,
            preferredChannel = preferredChannel,
            finalChannel = finalChannel,
            messageText = messageText,
            providerFailureSelection = routeLoopState.providerFailureSelection,
            noDeliveryRoute = noDeliveryRoute,
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
    dispatchAttemptDao.saveMessageDispatchAttemptOutcome(
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
    pendingMessageDao.savePendingMessageDispatchStatusUpdate(
        sentPendingMessageStatusUpdate(messageId)
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
) {
    pendingMessageDao.savePendingMessageDispatchStatusUpdate(
        failedPendingMessageStatusUpdate(messageId)
    )
    val resolvedFailedAtMs = failedAtMs ?: System.currentTimeMillis()
    val failure = failedMessageDispatchFailure(
        providerFailureSelection = providerFailureSelection,
        noDeliveryRoute = noDeliveryRoute,
    )
    dispatchAttemptDao.saveMessageDispatchAttemptOutcome(
        failedDispatchAttemptOutcomeUpdate(
            dispatchAttemptId = dispatchAttemptId,
            failedAtMs = resolvedFailedAtMs,
            channel = finalChannel,
            failure = failure,
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
        )
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
