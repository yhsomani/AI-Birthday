package com.example.domain.dispatch

import com.example.core.db.entities.DispatchAttemptEntity
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

fun DispatchAttemptEntity.toDispatchAttempt(): DispatchAttempt {
    return DispatchAttempt(
        id = DispatchAttemptId(id),
        messageDraftId = MessageDraftId(messageDraftId),
        contactId = contactId?.let(::ContactId),
        occasionId = occasionId?.let(::OccasionId),
        channel = MessageChannel.fromRaw(channel),
        routeRank = routeRank,
        eligibilityDecision = DispatchEligibilityRecord.fromRaw(eligibilityDecision),
        blockOrDeferReason = blockOrDeferReason,
        requestedAtMs = requestedAtMs,
        attemptedAtMs = attemptedAtMs,
        resolvedAtMs = resolvedAtMs,
        result = DispatchAttemptResult.fromRaw(result),
        deliveryStatus = MessageDeliveryStatus.fromRaw(deliveryStatus),
        providerMessageId = providerMessageId,
        errorType = errorType,
        errorCode = errorCode,
        redactedErrorMessage = redactedErrorMessage,
        retryCount = retryCount,
        nextRetryAtMs = nextRetryAtMs,
        deadLetteredAtMs = deadLetteredAtMs,
        createdBy = DispatchAttemptCreator.fromRaw(createdBy),
        metadataJson = metadataJson,
    )
}

fun DispatchAttempt.toEntity(): DispatchAttemptEntity {
    return DispatchAttemptEntity(
        id = id.value,
        messageDraftId = messageDraftId.value,
        contactId = contactId?.value,
        occasionId = occasionId?.value,
        channel = channel.raw,
        routeRank = routeRank,
        eligibilityDecision = eligibilityDecision.raw,
        blockOrDeferReason = blockOrDeferReason,
        requestedAtMs = requestedAtMs,
        attemptedAtMs = attemptedAtMs,
        resolvedAtMs = resolvedAtMs,
        result = result.raw,
        deliveryStatus = deliveryStatus.raw,
        providerMessageId = providerMessageId,
        errorType = errorType,
        errorCode = errorCode,
        redactedErrorMessage = redactedErrorMessage,
        retryCount = retryCount,
        nextRetryAtMs = nextRetryAtMs,
        deadLetteredAtMs = deadLetteredAtMs,
        createdBy = createdBy.raw,
        metadataJson = metadataJson,
    )
}
