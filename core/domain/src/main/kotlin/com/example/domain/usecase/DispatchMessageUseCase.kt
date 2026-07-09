package com.example.domain.usecase

import com.example.domain.automation.DispatchDecision
import com.example.domain.automation.DispatchEligibilityPolicy
import com.example.domain.dispatch.DispatchExceptionFailurePolicy
import com.example.domain.dispatch.buildMessageDispatchRequest
import com.example.domain.dispatch.newDispatchAttempt
import com.example.domain.repository.ActivityLogRepository
import com.example.domain.model.ActivityLogSeverity
import com.example.domain.model.ActivityLogStatus
import com.example.domain.model.DispatchActivityDecision
import com.example.domain.model.MessageStatus
import com.example.domain.model.common.DispatchAttemptId
import com.example.domain.model.dispatch.DispatchAttemptCreator
import com.example.domain.model.dispatch.DispatchAttemptResult
import com.example.domain.model.dispatch.DispatchEligibilityRecord
import com.example.domain.model.message.MessageDispatchState
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.DispatchAttemptRepository
import com.example.domain.repository.MessageRepository
import com.example.domain.service.MessageDispatcherService
import com.example.domain.service.PreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dispatches a pending message via SMS, WhatsApp, or Email.
 * - Looks up pending message by id first, then eventId for legacy callers
 * - Uses the shared dispatch eligibility policy before sending
 * - No-ops if contact/pending message is missing
 * - Updates contact's last-wished timestamp and consecutive-years-wished count
 */
@Singleton
class DispatchMessageUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
    private val contactRepository: ContactRepository,
    private val messageDispatcherService: MessageDispatcherService,
    private val activityLogRepository: ActivityLogRepository,
    private val dispatchAttemptRepository: DispatchAttemptRepository,
    private val preferencesRepository: PreferencesRepository,
) {
    suspend operator fun invoke(messageRef: String): DispatchOutcome {
        val pending = messageRepository.getMessageDispatchStateById(messageRef)
            ?: messageRepository.getMessageDispatchStateByEventId(messageRef)
            ?: return DispatchOutcome.PendingNotFound

        when (val decision = DispatchEligibilityPolicy.evaluate(
            draft = pending.draft,
            quietHoursStart = preferencesRepository.getQuietHoursStart(),
            quietHoursEnd = preferencesRepository.getQuietHoursEnd(),
            blackoutDatesJson = preferencesRepository.getBlackoutDates(),
        )) {
            DispatchDecision.SendNow -> Unit
            is DispatchDecision.DeferUntil -> {
                recordDispatchAttempt(
                    pending = pending,
                    eligibilityDecision = DispatchEligibilityRecord.DEFERRED,
                    result = DispatchAttemptResult.DEFERRED,
                    reason = decision.reason.name,
                    resolvedAtMs = System.currentTimeMillis(),
                )
                activityLogRepository.recordDispatchActivity(
                    pending = pending,
                    title = "Dispatch deferred",
                    detail = "Message is scheduled for later.",
                    severity = ActivityLogSeverity.INFO,
                    status = ActivityLogStatus.OPEN,
                    decision = DispatchActivityDecision.DEFERRED,
                    reason = decision.reason.name,
                    scheduledForMs = decision.epochMs,
                )
                return DispatchOutcome.Deferred(pending.id.value, decision.epochMs)
            }
            is DispatchDecision.NeedsApproval -> {
                recordDispatchAttempt(
                    pending = pending,
                    eligibilityDecision = DispatchEligibilityRecord.NEEDS_APPROVAL,
                    result = DispatchAttemptResult.NEEDS_APPROVAL,
                    reason = decision.approvalMode.raw,
                    resolvedAtMs = System.currentTimeMillis(),
                )
                activityLogRepository.recordDispatchActivity(
                    pending = pending,
                    title = "Dispatch waiting for approval",
                    detail = "Message still needs approval before it can be sent.",
                    severity = ActivityLogSeverity.INFO,
                    status = ActivityLogStatus.OPEN,
                    decision = DispatchActivityDecision.NEEDS_APPROVAL,
                    reason = decision.approvalMode.raw,
                )
                return DispatchOutcome.NotApproved(pending.status.raw)
            }
            is DispatchDecision.Expire -> {
                messageRepository.saveMessageStatusUpdate(pending.statusUpdate(MessageStatus.EXPIRED))
                val expired = pending.withStatus(MessageStatus.EXPIRED)
                recordDispatchAttempt(
                    pending = expired,
                    eligibilityDecision = DispatchEligibilityRecord.EXPIRED,
                    result = DispatchAttemptResult.EXPIRED,
                    reason = decision.reason.name,
                    resolvedAtMs = System.currentTimeMillis(),
                )
                activityLogRepository.recordDispatchActivity(
                    pending = expired,
                    title = "Dispatch expired",
                    detail = "Message approval window expired before sending.",
                    severity = ActivityLogSeverity.WARNING,
                    status = ActivityLogStatus.RESOLVED,
                    decision = DispatchActivityDecision.EXPIRED,
                    reason = decision.reason.name,
                )
                return DispatchOutcome.Expired(pending.id.value)
            }
            is DispatchDecision.Blocked -> {
                recordDispatchAttempt(
                    pending = pending,
                    eligibilityDecision = DispatchEligibilityRecord.BLOCKED,
                    result = DispatchAttemptResult.BLOCKED,
                    reason = decision.reason.name,
                    resolvedAtMs = System.currentTimeMillis(),
                )
                activityLogRepository.recordDispatchActivity(
                    pending = pending,
                    title = "Dispatch blocked",
                    detail = blockedDispatchDetail(decision.reason),
                    severity = blockedDispatchSeverity(decision.reason),
                    status = ActivityLogStatus.OPEN,
                    decision = DispatchActivityDecision.BLOCKED,
                    reason = decision.reason.name,
                )
                return DispatchOutcome.NotApproved(pending.status.raw)
            }
        }

        val recipient = contactRepository.getMessageDispatchRecipient(pending.contactId.value) ?: run {
            recordDispatchAttempt(
                pending = pending,
                eligibilityDecision = DispatchEligibilityRecord.BLOCKED,
                result = DispatchAttemptResult.BLOCKED,
                reason = "CONTACT_NOT_FOUND",
                resolvedAtMs = System.currentTimeMillis(),
            )
            activityLogRepository.recordDispatchActivity(
                pending = pending,
                title = "Dispatch blocked",
                detail = "Message contact could not be found.",
                severity = ActivityLogSeverity.ERROR,
                status = ActivityLogStatus.OPEN,
                decision = DispatchActivityDecision.BLOCKED,
                reason = "CONTACT_NOT_FOUND",
            )
            return DispatchOutcome.ContactNotFound
        }

        val attemptId = recordDispatchAttempt(
            pending = pending,
            eligibilityDecision = DispatchEligibilityRecord.SEND_NOW,
            result = DispatchAttemptResult.QUEUED,
            reason = null,
            resolvedAtMs = null,
        )

        try {
            messageDispatcherService.dispatch(
                buildMessageDispatchRequest(
                    message = pending.dispatchDraft,
                    recipient = recipient,
                    dispatchAttemptId = attemptId,
                ),
            )
        } catch (e: Exception) {
            val failedAtMs = System.currentTimeMillis()
            val failure = DispatchExceptionFailurePolicy.evaluate(e)
            runCatching {
                dispatchAttemptRepository.updateOutcome(
                    id = DispatchAttemptId(attemptId),
                    attemptedAtMs = failedAtMs,
                    resolvedAtMs = failedAtMs,
                    result = failure.result,
                    channel = null,
                    deliveryStatus = failure.deliveryStatus,
                    providerMessageId = null,
                    errorType = failure.errorType,
                    errorCode = failure.errorCode,
                    redactedErrorMessage = failure.redactedErrorMessage,
                    retryCount = 0,
                    nextRetryAtMs = null,
                    deadLetteredAtMs = if (failure.deadLetter) failedAtMs else null,
                )
            }
            throw e
        }
        activityLogRepository.recordDispatchActivity(
            pending = pending,
            title = "Dispatch sent",
            detail = "Message dispatched through ${pending.channel.raw}.",
            severity = ActivityLogSeverity.INFO,
            status = ActivityLogStatus.RESOLVED,
            decision = DispatchActivityDecision.SENT,
        )

        return DispatchOutcome.Sent(pending.id.value, pending.channel.raw)
    }

    private suspend fun recordDispatchAttempt(
        pending: MessageDispatchState,
        eligibilityDecision: DispatchEligibilityRecord,
        result: DispatchAttemptResult,
        reason: String?,
        resolvedAtMs: Long?,
    ): String {
        val requestedAtMs = System.currentTimeMillis()
        val attempt = pending.draft.newDispatchAttempt(
            eligibilityDecision = eligibilityDecision,
            result = result,
            createdBy = DispatchAttemptCreator.USER,
            blockOrDeferReason = reason,
            requestedAtMs = requestedAtMs,
            resolvedAtMs = resolvedAtMs?.coerceAtLeast(requestedAtMs),
        )
        dispatchAttemptRepository.upsert(attempt)
        return attempt.id.value
    }

    sealed class DispatchOutcome {
        data object PendingNotFound : DispatchOutcome()
        data object ContactNotFound : DispatchOutcome()
        data class NotApproved(val status: String) : DispatchOutcome()
        data class Deferred(val pendingId: String, val scheduledForMs: Long) : DispatchOutcome()
        data class Expired(val pendingId: String) : DispatchOutcome()
        data class Sent(val pendingId: String, val channel: String) : DispatchOutcome()
    }

}
