package com.example.core.automation.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.core.automation.sender.MessageDispatcher
import com.example.core.automation.scheduler.DailyScheduler
import com.example.core.data.R
import com.example.core.db.dao.ContactDao
import com.example.core.db.dao.DispatchAttemptDao
import com.example.core.db.dao.EventDao
import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.dao.SentMessageDao
import com.example.core.db.entities.PendingMessageEntity
import com.example.core.resilience.StructuredLogger
import com.example.domain.automation.DispatchBlockReason
import com.example.domain.automation.DispatchDecision
import com.example.domain.automation.DispatchEligibilityPolicy
import com.example.domain.contact.toMessageDispatchRecipient
import com.example.domain.dispatch.buildMessageDispatchRequest
import com.example.domain.dispatch.newDispatchAttempt
import com.example.domain.dispatch.toEntity
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.MessageStatus
import com.example.domain.model.dispatch.DispatchAttemptCreator
import com.example.domain.model.dispatch.DispatchAttemptResult
import com.example.domain.model.dispatch.DispatchEligibilityRecord
import com.example.domain.message.toMessageDispatchDraft
import com.example.domain.message.toMessageDraft
import com.example.domain.service.PreferencesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import androidx.hilt.work.HiltWorker

@HiltWorker
class MessageDispatchWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val pendingMessageDao: PendingMessageDao,
    private val sentMessageDao: SentMessageDao,
    private val contactDao: ContactDao,
    private val eventDao: EventDao,
    private val dispatchAttemptDao: DispatchAttemptDao,
    private val preferencesRepository: PreferencesRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val pendingMessageId = inputData.getString(MessageDispatchWorkRequests.KEY_PENDING_MESSAGE_ID)
        val eventId = inputData.getString(MessageDispatchWorkRequests.KEY_EVENT_ID)
        if (pendingMessageId.isNullOrBlank() && eventId.isNullOrBlank()) {
            StructuredLogger.e(TAG, "Missing pending_message_id and event_id in input data")
            return Result.failure()
        }

        StructuredLogger.i(TAG, "Dispatching message", mapOf(
            "pendingMessageId" to (pendingMessageId ?: ""),
            "eventId" to (eventId ?: ""),
        ))

        val pendingMsg = if (!pendingMessageId.isNullOrBlank()) {
            pendingMessageDao.getById(pendingMessageId)
        } else {
            pendingMessageDao.getByEventId(eventId.orEmpty())
        } ?: run {
            StructuredLogger.w(TAG, "No pending message found", extras = mapOf(
                "pendingMessageId" to (pendingMessageId ?: ""),
                "eventId" to (eventId ?: ""),
            ))
            return Result.failure()
        }

        val contact = contactDao.getById(pendingMsg.contactId) ?: run {
            recordDispatchAttempt(
                pending = pendingMsg,
                eligibilityDecision = DispatchEligibilityRecord.BLOCKED,
                result = DispatchAttemptResult.BLOCKED,
                reason = "CONTACT_NOT_FOUND",
                resolvedAtMs = System.currentTimeMillis(),
            )
            StructuredLogger.w(TAG, "Contact not found for ${pendingMsg.contactId}")
            return Result.failure()
        }

        val now = System.currentTimeMillis()
        when (val decision = DispatchEligibilityPolicy.evaluate(
            draft = pendingMsg.toMessageDraft(),
            approvalMode = ApprovalMode.fromRaw(pendingMsg.approvalMode),
            nowMs = now,
            quietHoursStart = preferencesRepository.getQuietHoursStart(),
            quietHoursEnd = preferencesRepository.getQuietHoursEnd(),
            blackoutDatesJson = preferencesRepository.getBlackoutDates(),
        )) {
            DispatchDecision.SendNow -> Unit
            is DispatchDecision.DeferUntil -> {
                recordDispatchAttempt(
                    pending = pendingMsg,
                    eligibilityDecision = DispatchEligibilityRecord.DEFERRED,
                    result = DispatchAttemptResult.DEFERRED,
                    reason = decision.reason.name,
                    resolvedAtMs = System.currentTimeMillis(),
                )
                StructuredLogger.i(TAG, "Deferring dispatch", mapOf(
                    "pendingMessageId" to pendingMsg.id,
                    "scheduledForMs" to decision.epochMs.toString(),
                    "reason" to decision.reason.name,
                ))
                if (decision.epochMs != pendingMsg.scheduledForMs) {
                    pendingMessageDao.insert(pendingMsg.copy(scheduledForMs = decision.epochMs))
                }
                DailyScheduler.scheduleExactSend(context, pendingMsg.id)
                return Result.success()
            }
            is DispatchDecision.NeedsApproval -> {
                recordDispatchAttempt(
                    pending = pendingMsg,
                    eligibilityDecision = DispatchEligibilityRecord.NEEDS_APPROVAL,
                    result = DispatchAttemptResult.NEEDS_APPROVAL,
                    reason = decision.approvalMode.raw,
                    resolvedAtMs = System.currentTimeMillis(),
                )
                StructuredLogger.i(TAG, "Message still pending approval; waiting", mapOf(
                    "pendingMessageId" to pendingMsg.id,
                    "approvalMode" to decision.approvalMode.raw,
                ))
                return Result.success()
            }
            is DispatchDecision.Expire -> {
                StructuredLogger.i(TAG, "Approval deadline passed without user action; expiring", mapOf(
                    "pendingMessageId" to pendingMsg.id,
                    "reason" to decision.reason.name,
                ))
                pendingMessageDao.updateStatus(pendingMsg.id, MessageStatus.EXPIRED.raw)
                recordDispatchAttempt(
                    pending = pendingMsg.copy(status = MessageStatus.EXPIRED.raw),
                    eligibilityDecision = DispatchEligibilityRecord.EXPIRED,
                    result = DispatchAttemptResult.EXPIRED,
                    reason = decision.reason.name,
                    resolvedAtMs = System.currentTimeMillis(),
                )
                com.example.core.automation.notifications.NotificationHelper.showSetupNotification(
                    context,
                    context.getString(R.string.notification_setup_message_expired_title),
                    context.getString(R.string.notification_setup_message_expired_message, contact.name),
                )
                return Result.success()
            }
            is DispatchDecision.Blocked -> {
                recordDispatchAttempt(
                    pending = pendingMsg,
                    eligibilityDecision = DispatchEligibilityRecord.BLOCKED,
                    result = DispatchAttemptResult.BLOCKED,
                    reason = decision.reason.name,
                    resolvedAtMs = System.currentTimeMillis(),
                )
                StructuredLogger.i(TAG, "Dispatch blocked by message state", mapOf(
                    "pendingMessageId" to pendingMsg.id,
                    "status" to pendingMsg.status,
                    "reason" to decision.reason.name,
                ))
                if (decision.reason == DispatchBlockReason.ALREADY_HANDLED) {
                    com.example.core.automation.notifications.NotificationHelper.showSetupNotification(
                        context,
                        context.getString(R.string.notification_setup_double_send_title),
                        context.getString(R.string.notification_setup_double_send_message, contact.name),
                    )
                }
                return Result.success()
            }
        }

        val attemptId = recordDispatchAttempt(
            pending = pendingMsg,
            eligibilityDecision = DispatchEligibilityRecord.SEND_NOW,
            result = DispatchAttemptResult.QUEUED,
            reason = null,
            resolvedAtMs = null,
        )

        // Idempotency: mark status as DISPATCHING immediately
        pendingMessageDao.updateStatus(pendingMsg.id, MessageStatus.DISPATCHING.raw)

        try {
            val dispatcher = MessageDispatcher(
                context = context,
                pendingMessageDao = pendingMessageDao,
                sentMessageDao = sentMessageDao,
                contactDao = contactDao,
                eventDao = eventDao,
                dispatchAttemptDao = dispatchAttemptDao,
            )
            dispatcher.dispatch(
                buildMessageDispatchRequest(
                    message = pendingMsg.toMessageDispatchDraft(),
                    recipient = contact.toMessageDispatchRecipient(),
                    dispatchAttemptId = attemptId,
                ),
            )
        } catch (e: Exception) {
            StructuredLogger.e(TAG, "Dispatch failed unexpectedly for message ${pendingMsg.id}", e)
            val failedAtMs = System.currentTimeMillis()
            runCatching {
                dispatchAttemptDao.updateOutcome(
                    id = attemptId,
                    attemptedAtMs = failedAtMs,
                    resolvedAtMs = failedAtMs,
                    result = DispatchAttemptResult.FAILED_FINAL.raw,
                    channel = null,
                    deliveryStatus = MessageDeliveryStatus.FAILED.raw,
                    providerMessageId = null,
                    errorType = e::class.simpleName ?: "DISPATCH_EXCEPTION",
                    errorCode = null,
                    redactedErrorMessage = "Dispatcher failed before completing send.",
                    retryCount = 0,
                    nextRetryAtMs = null,
                    deadLetteredAtMs = failedAtMs,
                )
            }.onFailure { attemptError ->
                StructuredLogger.e(TAG, "Failed to update dispatch attempt $attemptId after dispatch exception", attemptError)
            }
            runCatching {
                pendingMessageDao.updateStatus(pendingMsg.id, MessageStatus.FAILED.raw)
            }.onFailure { statusError ->
                StructuredLogger.e(TAG, "Failed to mark message ${pendingMsg.id} as FAILED after dispatch exception", statusError)
            }
            return Result.failure()
        }

        return Result.success()
    }

    private suspend fun recordDispatchAttempt(
        pending: PendingMessageEntity,
        eligibilityDecision: DispatchEligibilityRecord,
        result: DispatchAttemptResult,
        reason: String?,
        resolvedAtMs: Long?,
    ): String {
        val requestedAtMs = System.currentTimeMillis()
        val attempt = pending.toMessageDraft().newDispatchAttempt(
            eligibilityDecision = eligibilityDecision,
            result = result,
            createdBy = DispatchAttemptCreator.WORKER,
            blockOrDeferReason = reason,
            requestedAtMs = requestedAtMs,
            resolvedAtMs = resolvedAtMs?.coerceAtLeast(requestedAtMs),
        )
        dispatchAttemptDao.upsert(attempt.toEntity())
        return attempt.id.value
    }

    companion object {
        private const val TAG = "MessageDispatchWorker"
    }
}
