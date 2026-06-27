package com.example.domain.usecase

import com.example.domain.dispatch.newDispatchAttempt
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.MessageStatus
import com.example.domain.model.common.MessageDraftId
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
        val pending = messageRepository.getPendingById(pendingMessageId)
            ?: return RetryOutcome.PendingNotFound

        val status = MessageStatus.fromRaw(pending.status)
        if (status != MessageStatus.FAILED) {
            return RetryOutcome.NotFailed(pending.id, status)
        }

        val retryAtMs = System.currentTimeMillis()
        val latestFailure = dispatchAttemptRepository.getLatestFailureForMessageDraft(MessageDraftId(pending.id))
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

        messageRepository.insertPending(
            pending.copy(
                status = MessageStatus.APPROVED.raw,
                scheduledForMs = retryAtMs,
            ),
        )
        schedulerService.scheduleExactSend(pending.id)

        return RetryOutcome.RetryQueued(
            pendingMessageId = pending.id,
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
