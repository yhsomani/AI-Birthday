package com.example.domain.dispatch

import com.example.core.db.entities.PendingMessageEntity
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
import java.util.UUID

fun PendingMessageEntity.newDispatchAttempt(
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
    return DispatchAttempt(
        id = DispatchAttemptId(UUID.randomUUID().toString()),
        messageDraftId = MessageDraftId(id),
        contactId = ContactId(contactId),
        occasionId = OccasionId(eventId),
        channel = MessageChannel.fromRaw(channel).takeUnless { it == MessageChannel.UNKNOWN } ?: MessageChannel.SMS,
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
