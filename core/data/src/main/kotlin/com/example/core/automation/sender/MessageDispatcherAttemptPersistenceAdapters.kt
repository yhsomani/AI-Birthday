package com.example.core.automation.sender

import com.example.core.db.dao.DispatchAttemptDao
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.common.DispatchAttemptId
import com.example.domain.model.dispatch.DispatchAttemptOutcomeUpdate
import com.example.domain.model.dispatch.DispatchAttemptResult

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
