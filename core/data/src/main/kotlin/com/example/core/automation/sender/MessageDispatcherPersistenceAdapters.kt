package com.example.core.automation.sender

import com.example.core.db.dao.ContactDao
import com.example.core.db.dao.DispatchAttemptDao
import com.example.core.db.dao.EventDao
import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.dao.SentMessageDao
import com.example.core.db.dao.saveMessageStatusUpdate
import com.example.core.db.entities.SentMessageEntity
import com.example.domain.event.toOccasion
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.MessageStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.DispatchAttemptId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.common.SentMessageId
import com.example.domain.model.contact.ContactPostDispatchUpdate
import com.example.domain.model.dispatch.DispatchAttemptOutcomeUpdate
import com.example.domain.model.dispatch.DispatchAttemptResult
import com.example.domain.model.dispatch.MessageDispatchOccasion
import com.example.domain.model.message.MessageStatusUpdate
import com.example.domain.model.message.SentMessageDeliveryStatusUpdate
import com.example.domain.model.message.SentMessageDispatchRecord
import com.example.domain.model.occasion.OccasionType

internal fun sentPendingMessageStatusUpdate(messageId: MessageDraftId): MessageStatusUpdate {
    return MessageStatusUpdate(
        id = messageId,
        status = MessageStatus.SENT,
    )
}

internal fun failedPendingMessageStatusUpdate(messageId: MessageDraftId): MessageStatusUpdate {
    return MessageStatusUpdate(
        id = messageId,
        status = MessageStatus.FAILED,
    )
}

internal suspend fun PendingMessageDao.savePendingMessageDispatchStatusUpdate(update: MessageStatusUpdate) {
    saveMessageStatusUpdate(update)
}

internal fun successfulDispatchAttemptOutcomeUpdate(
    dispatchAttemptId: String?,
    resolvedAtMs: Long,
    channel: MessageChannel,
): DispatchAttemptOutcomeUpdate? {
    val isPendingDelivery = channel == MessageChannel.SMS
    return dispatchAttemptOutcomeUpdate(
        dispatchAttemptId = dispatchAttemptId,
        resolvedAtMs = resolvedAtMs,
        result = if (isPendingDelivery) {
            DispatchAttemptResult.PENDING_DELIVERY
        } else {
            DispatchAttemptResult.SENT
        },
        channel = channel,
        deliveryStatus = if (isPendingDelivery) {
            MessageDeliveryStatus.PENDING_DELIVERY
        } else {
            MessageDeliveryStatus.SENT
        },
        errorType = null,
        errorCode = null,
        redactedErrorMessage = null,
        deadLetteredAtMs = null,
        nextRetryAtMs = null,
    )
}

internal fun failedDispatchAttemptOutcomeUpdate(
    dispatchAttemptId: String?,
    failedAtMs: Long,
    channel: MessageChannel,
    failure: ProviderDispatchFailure,
    retryCount: Int = 0,
): DispatchAttemptOutcomeUpdate? {
    return dispatchAttemptOutcomeUpdate(
        dispatchAttemptId = dispatchAttemptId,
        resolvedAtMs = failedAtMs,
        result = failure.result,
        channel = channel,
        deliveryStatus = MessageDeliveryStatus.FAILED,
        errorType = failure.errorType,
        errorCode = failure.errorCode,
        redactedErrorMessage = failure.redactedErrorMessage,
        deadLetteredAtMs = if (failure.result == DispatchAttemptResult.FAILED_FINAL) failedAtMs else null,
        nextRetryAtMs = failure.nextRetryDelayMs?.let { delayMs -> failedAtMs + delayMs },
        retryCount = retryCount,
    )
}

internal fun dispatchAttemptOutcomeUpdate(
    dispatchAttemptId: String?,
    resolvedAtMs: Long,
    result: DispatchAttemptResult,
    channel: MessageChannel?,
    deliveryStatus: MessageDeliveryStatus,
    errorType: String?,
    errorCode: String?,
    redactedErrorMessage: String?,
    deadLetteredAtMs: Long?,
    nextRetryAtMs: Long?,
    retryCount: Int = 0,
): DispatchAttemptOutcomeUpdate? {
    val id = dispatchAttemptId.takeUnless { it.isNullOrBlank() } ?: return null
    return DispatchAttemptOutcomeUpdate(
        id = DispatchAttemptId(id),
        attemptedAtMs = resolvedAtMs,
        resolvedAtMs = resolvedAtMs,
        result = result,
        channel = channel,
        deliveryStatus = deliveryStatus,
        providerMessageId = null,
        errorType = errorType,
        errorCode = errorCode,
        redactedErrorMessage = redactedErrorMessage,
        retryCount = retryCount,
        nextRetryAtMs = nextRetryAtMs,
        deadLetteredAtMs = deadLetteredAtMs,
    )
}

internal suspend fun DispatchAttemptDao.saveDispatchAttemptOutcome(update: DispatchAttemptOutcomeUpdate) {
    updateOutcome(
        id = update.id.value,
        attemptedAtMs = update.attemptedAtMs,
        resolvedAtMs = update.resolvedAtMs,
        result = update.result.raw,
        channel = update.channel?.raw,
        deliveryStatus = update.deliveryStatus.raw,
        providerMessageId = update.providerMessageId,
        errorType = update.errorType,
        errorCode = update.errorCode,
        redactedErrorMessage = update.redactedErrorMessage,
        retryCount = update.retryCount,
        nextRetryAtMs = update.nextRetryAtMs,
        deadLetteredAtMs = update.deadLetteredAtMs,
    )
}

internal suspend fun DispatchAttemptDao.saveInitialSmsHandoffOutcomeIfAwaitingCallback(
    update: DispatchAttemptOutcomeUpdate,
) {
    updateInitialSmsHandoffOutcomeIfAwaitingCallback(
        id = update.id.value,
        attemptedAtMs = update.attemptedAtMs,
        resolvedAtMs = update.resolvedAtMs,
        result = update.result.raw,
        channel = update.channel?.raw ?: MessageChannel.SMS.raw,
        deliveryStatus = update.deliveryStatus.raw,
        providerMessageId = update.providerMessageId,
        errorType = update.errorType,
        errorCode = update.errorCode,
        redactedErrorMessage = update.redactedErrorMessage,
        retryCount = update.retryCount,
        nextRetryAtMs = update.nextRetryAtMs,
        deadLetteredAtMs = update.deadLetteredAtMs,
    )
}

internal suspend fun DispatchAttemptDao?.saveMessageDispatchAttemptOutcome(update: DispatchAttemptOutcomeUpdate?) {
    update ?: return
    runCatching {
        this?.saveDispatchAttemptOutcome(update)
    }.onFailure { e ->
        recordMessageDispatchLifecycleLog(
            messageDispatchAttemptOutcomeUpdateFailedLog(
                dispatchAttemptId = update.id.value,
                cause = e,
            )
        )
    }
}

internal suspend fun DispatchAttemptDao?.saveSuccessfulMessageDispatchAttemptOutcome(
    update: DispatchAttemptOutcomeUpdate?,
) {
    update ?: return
    runCatching {
        if (
            update.channel == MessageChannel.SMS &&
            update.result == DispatchAttemptResult.PENDING_DELIVERY
        ) {
            this?.saveInitialSmsHandoffOutcomeIfAwaitingCallback(update)
        } else {
            this?.saveDispatchAttemptOutcome(update)
        }
    }.onFailure { e ->
        recordMessageDispatchLifecycleLog(
            messageDispatchAttemptOutcomeUpdateFailedLog(
                dispatchAttemptId = update.id.value,
                cause = e,
            )
        )
    }
}

internal fun contactPostDispatchUpdate(
    contactId: ContactId,
    wishedAtMs: Long,
): ContactPostDispatchUpdate {
    return ContactPostDispatchUpdate(
        contactId = contactId,
        wishedAtMs = wishedAtMs,
        healthScoreDelta = 5,
    )
}

internal suspend fun ContactDao.saveContactPostDispatchUpdate(update: ContactPostDispatchUpdate) {
    val contactId = update.contactId.value
    updateLastWished(contactId, update.wishedAtMs)
    incrementConsecutiveYearsWished(contactId)
    updateHealthScoreDelta(contactId, update.healthScoreDelta)
}

internal fun smsPendingDeliverySentMessageDispatchRecord(
    id: SentMessageId,
    contactId: ContactId,
    dispatchOccasion: MessageDispatchOccasion,
    eventYear: Int,
    messageText: String,
    sentAtMs: Long,
): SentMessageDispatchRecord {
    return sentMessageDispatchRecord(
        id = id,
        contactId = contactId,
        occasionId = dispatchOccasion.occasionId,
        occasionType = dispatchOccasion.occasionType,
        occasionLabel = dispatchOccasion.occasionLabel,
        eventYear = eventYear,
        messageText = messageText,
        channel = MessageChannel.SMS,
        sentAtMs = sentAtMs,
        deliveryStatus = MessageDeliveryStatus.PENDING_DELIVERY,
    )
}

internal fun successfulSentMessageDispatchRecord(
    id: SentMessageId,
    contactId: ContactId,
    dispatchOccasion: MessageDispatchOccasion,
    eventYear: Int,
    messageText: String,
    channel: MessageChannel,
    sentAtMs: Long,
): SentMessageDispatchRecord {
    return sentMessageDispatchRecord(
        id = id,
        contactId = contactId,
        occasionId = dispatchOccasion.occasionId,
        occasionType = dispatchOccasion.occasionType,
        occasionLabel = dispatchOccasion.occasionLabel,
        eventYear = eventYear,
        messageText = messageText,
        channel = channel,
        sentAtMs = sentAtMs,
        deliveryStatus = MessageDeliveryStatus.SENT,
    )
}

internal fun sentMessageDispatchRecord(
    id: SentMessageId,
    contactId: ContactId,
    occasionId: OccasionId?,
    occasionType: OccasionType,
    occasionLabel: String?,
    eventYear: Int,
    messageText: String,
    channel: MessageChannel,
    sentAtMs: Long,
    deliveryStatus: MessageDeliveryStatus,
): SentMessageDispatchRecord {
    return SentMessageDispatchRecord(
        id = id,
        contactId = contactId,
        occasionId = occasionId,
        occasionType = occasionType,
        occasionLabel = occasionLabel,
        eventYear = eventYear,
        messageText = messageText,
        channel = channel,
        sentAtMs = sentAtMs,
        deliveryStatus = deliveryStatus,
        aiGenerated = true,
    )
}

internal suspend fun SentMessageDao.saveSentMessageDispatchRecord(record: SentMessageDispatchRecord) {
    insert(
        SentMessageEntity(
            id = record.id.value,
            contactId = record.contactId.value,
            eventType = record.occasionType.raw,
            eventId = record.occasionId?.value,
            occasionType = record.occasionType.raw,
            occasionLabel = record.occasionLabel,
            eventYear = record.eventYear,
            messageText = record.messageText,
            channel = record.channel.raw,
            sentAtMs = record.sentAtMs,
            deliveryStatus = record.deliveryStatus.raw,
            aiGenerated = record.aiGenerated,
        )
    )
}

internal fun failedSentMessageDeliveryStatusUpdate(id: SentMessageId): SentMessageDeliveryStatusUpdate {
    return sentMessageDeliveryStatusUpdate(
        id = id,
        deliveryStatus = MessageDeliveryStatus.FAILED,
    )
}

internal fun sentMessageDeliveryStatusUpdate(
    id: SentMessageId,
    deliveryStatus: MessageDeliveryStatus,
): SentMessageDeliveryStatusUpdate {
    return SentMessageDeliveryStatusUpdate(
        id = id,
        deliveryStatus = deliveryStatus,
    )
}

internal suspend fun SentMessageDao.saveSentMessageDeliveryStatusUpdate(update: SentMessageDeliveryStatusUpdate) {
    updateDeliveryStatus(update.id.value, update.deliveryStatus.raw)
}

internal suspend fun messageDispatchOccasion(
    eventDao: EventDao?,
    occasionReference: OccasionId,
): MessageDispatchOccasion {
    val occasion = eventDao?.getById(occasionReference.value)?.toOccasion()
    return if (occasion != null) {
        MessageDispatchOccasion(
            occasionId = occasion.id,
            occasionType = occasion.type,
            occasionLabel = occasion.label,
        )
    } else {
        fallbackMessageDispatchOccasion(occasionReference)
    }
}

internal fun fallbackMessageDispatchOccasion(occasionReference: OccasionId): MessageDispatchOccasion {
    val normalized = occasionReference.value.trim().uppercase()
    val explicitType = OccasionType.fromRaw(normalized)
    val occasionType = if (explicitType != OccasionType.UNKNOWN) {
        explicitType
    } else {
        when {
            normalized.startsWith("FOLLOWUP_") || normalized.startsWith("FOLLOW_UP_") -> OccasionType.FOLLOW_UP
            normalized.startsWith("HOLIDAY_") -> OccasionType.HOLIDAY
            normalized.startsWith("REVIVAL_") -> OccasionType.REVIVAL
            else -> OccasionType.UNKNOWN
        }
    }
    return MessageDispatchOccasion(
        occasionId = null,
        occasionType = occasionType,
        occasionLabel = null,
    )
}
