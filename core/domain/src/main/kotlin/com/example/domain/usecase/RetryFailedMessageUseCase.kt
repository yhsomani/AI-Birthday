package com.example.domain.usecase

import com.example.domain.dispatch.newDispatchAttempt
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.MessageStatus
import com.example.domain.model.dispatch.DispatchAttempt
import com.example.domain.model.dispatch.DispatchAttemptCreator
import com.example.domain.model.dispatch.DispatchAttemptResult
import com.example.domain.model.dispatch.DispatchEligibilityRecord
import com.example.domain.repository.DispatchAttemptRepository
import com.example.domain.repository.MessageRepository
import com.example.domain.service.SchedulerService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RetryFailedMessageUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
    private val dispatchAttemptRepository: DispatchAttemptRepository,
    private val schedulerService: SchedulerService,
) {
    suspend operator fun invoke(pendingMessageId: String): RetryOutcome {
        val pending = messageRepository.getRetryableMessageDraftById(pendingMessageId)
            ?: return RetryOutcome.PendingNotFound

        if (pending.status != MessageStatus.FAILED) {
            return RetryOutcome.NotFailed(pending.id.value, pending.status)
        }

        val retryAtMs = System.currentTimeMillis()
        val latestFailure = dispatchAttemptRepository.getLatestFailureForMessageDraft(pending.id)
        val retryCount = (latestFailure?.retryCount ?: 0) + 1

        if (latestFailure != null) {
            dispatchAttemptRepository.updateOutcome(
                id = latestFailure.id,
                attemptedAtMs = latestFailure.attemptedAtMs,
                resolvedAtMs = retryAtMs,
                result = DispatchAttemptResult.RETRY_QUEUED,
                channel = latestFailure.channel.takeUnless { it == MessageChannel.UNKNOWN },
                deliveryStatus = MessageDeliveryStatus.PENDING_DELIVERY,
                providerMessageId = latestFailure.providerMessageId,
                errorType = latestFailure.errorType,
                errorCode = latestFailure.errorCode,
                redactedErrorMessage = latestFailure.redactedErrorMessage,
                retryCount = retryCount,
                nextRetryAtMs = retryAtMs,
                deadLetteredAtMs = null,
            )
        } else {
            dispatchAttemptRepository.upsert(
                pending.newDispatchAttempt(
                    eligibilityDecision = DispatchEligibilityRecord.SEND_NOW,
                    result = DispatchAttemptResult.RETRY_QUEUED,
                    createdBy = DispatchAttemptCreator.USER,
                    blockOrDeferReason = "MANUAL_RETRY",
                    requestedAtMs = retryAtMs,
                    resolvedAtMs = retryAtMs,
                    deliveryStatus = MessageDeliveryStatus.PENDING_DELIVERY,
                    retryCount = retryCount,
                    nextRetryAtMs = retryAtMs,
                ),
            )
        }

        val retryState = pending.queuedForRetry(retryAtMs)
        messageRepository.saveRetryQueuedMessageDraft(retryState)
        schedulerService.scheduleExactSend(retryState.id.value)

        return RetryOutcome.RetryQueued(
            pendingMessageId = pending.id.value,
            retryCount = retryCount,
            previousAttempt = latestFailure,
        )
    }

    sealed class RetryOutcome {
        data object PendingNotFound : RetryOutcome()
        data class NotFailed(val pendingMessageId: String, val status: MessageStatus) : RetryOutcome()
        data class RetryQueued(
            val pendingMessageId: String,
            val retryCount: Int,
            val previousAttempt: DispatchAttempt?,
        ) : RetryOutcome()
    }
}
