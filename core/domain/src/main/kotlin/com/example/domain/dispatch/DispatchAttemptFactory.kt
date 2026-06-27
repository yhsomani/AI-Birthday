package com.example.domain.dispatch

import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.DispatchAttemptId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.dispatch.DispatchAttempt
import com.example.domain.model.dispatch.DispatchAttemptCreator
import com.example.domain.model.dispatch.DispatchAttemptResult
import com.example.domain.model.dispatch.DispatchEligibilityRecord
import com.example.domain.model.message.MessageDraft
import com.example.domain.model.message.RetryableMessageDraft
import java.util.UUID

fun MessageDraft.newDispatchAttempt(
    eligibilityDecision: DispatchEligibilityRecord,
    result: DispatchAttemptResult,
    createdBy: DispatchAttemptCreator,
    blockOrDeferReason: String? = null,
    requestedAtMs: Long = System.currentTimeMillis(),
    attemptedAtMs: Long? = null,
    resolvedAtMs: Long? = null,
    deliveryStatus: MessageDeliveryStatus = defaultDeliveryStatus(result),
    retryCount: Int = 0,
    nextRetryAtMs: Long? = null,
    deadLetteredAtMs: Long? = null,
    metadataJson: String = "{}",
): DispatchAttempt {
    return newDispatchAttempt(
        messageDraftId = id,
        contactId = contactId,
        occasionId = occasionId,
        channel = channel,
        eligibilityDecision = eligibilityDecision,
        result = result,
        createdBy = createdBy,
        blockOrDeferReason = blockOrDeferReason,
        requestedAtMs = requestedAtMs,
        attemptedAtMs = attemptedAtMs,
        resolvedAtMs = resolvedAtMs,
        deliveryStatus = deliveryStatus,
        retryCount = retryCount,
        nextRetryAtMs = nextRetryAtMs,
        deadLetteredAtMs = deadLetteredAtMs,
        metadataJson = metadataJson,
    )
}

fun RetryableMessageDraft.newDispatchAttempt(
    eligibilityDecision: DispatchEligibilityRecord,
    result: DispatchAttemptResult,
    createdBy: DispatchAttemptCreator,
    blockOrDeferReason: String? = null,
    requestedAtMs: Long = System.currentTimeMillis(),
    attemptedAtMs: Long? = null,
    resolvedAtMs: Long? = null,
    deliveryStatus: MessageDeliveryStatus = defaultDeliveryStatus(result),
    retryCount: Int = 0,
    nextRetryAtMs: Long? = null,
    deadLetteredAtMs: Long? = null,
    metadataJson: String = "{}",
): DispatchAttempt {
    return newDispatchAttempt(
        messageDraftId = id,
        contactId = contactId,
        occasionId = occasionId,
        channel = channel,
        eligibilityDecision = eligibilityDecision,
        result = result,
        createdBy = createdBy,
        blockOrDeferReason = blockOrDeferReason,
        requestedAtMs = requestedAtMs,
        attemptedAtMs = attemptedAtMs,
        resolvedAtMs = resolvedAtMs,
        deliveryStatus = deliveryStatus,
        retryCount = retryCount,
        nextRetryAtMs = nextRetryAtMs,
        deadLetteredAtMs = deadLetteredAtMs,
        metadataJson = metadataJson,
    )
}

private fun newDispatchAttempt(
    messageDraftId: MessageDraftId,
    contactId: ContactId?,
    occasionId: OccasionId?,
    channel: MessageChannel,
    eligibilityDecision: DispatchEligibilityRecord,
    result: DispatchAttemptResult,
    createdBy: DispatchAttemptCreator,
    blockOrDeferReason: String?,
    requestedAtMs: Long,
    attemptedAtMs: Long?,
    resolvedAtMs: Long?,
    deliveryStatus: MessageDeliveryStatus,
    retryCount: Int,
    nextRetryAtMs: Long?,
    deadLetteredAtMs: Long?,
    metadataJson: String,
): DispatchAttempt {
    return DispatchAttempt(
        id = DispatchAttemptId(UUID.randomUUID().toString()),
        messageDraftId = messageDraftId,
        contactId = contactId,
        occasionId = occasionId,
        channel = channel.takeUnless { it == MessageChannel.UNKNOWN } ?: MessageChannel.SMS,
        routeRank = 0,
        eligibilityDecision = eligibilityDecision,
        blockOrDeferReason = blockOrDeferReason,
        requestedAtMs = requestedAtMs,
        attemptedAtMs = attemptedAtMs,
        resolvedAtMs = resolvedAtMs,
        result = result,
        deliveryStatus = deliveryStatus,
        providerMessageId = null,
        errorType = null,
        errorCode = null,
        redactedErrorMessage = null,
        retryCount = retryCount,
        nextRetryAtMs = nextRetryAtMs,
        deadLetteredAtMs = deadLetteredAtMs,
        createdBy = createdBy,
        metadataJson = metadataJson,
    )
}

private fun defaultDeliveryStatus(result: DispatchAttemptResult): MessageDeliveryStatus {
    return when (result) {
        DispatchAttemptResult.QUEUED,
        DispatchAttemptResult.RETRY_QUEUED,
        DispatchAttemptResult.PENDING_DELIVERY -> MessageDeliveryStatus.PENDING_DELIVERY
        DispatchAttemptResult.SENT,
        DispatchAttemptResult.DELIVERED -> MessageDeliveryStatus.SENT
        DispatchAttemptResult.FAILED_RETRYABLE,
        DispatchAttemptResult.FAILED_FINAL -> MessageDeliveryStatus.FAILED
        DispatchAttemptResult.DEFERRED,
        DispatchAttemptResult.NEEDS_APPROVAL,
        DispatchAttemptResult.BLOCKED,
        DispatchAttemptResult.EXPIRED,
        DispatchAttemptResult.CANCELLED,
        DispatchAttemptResult.UNKNOWN -> MessageDeliveryStatus.UNKNOWN
    }
}
