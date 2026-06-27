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
import com.example.core.db.dao.saveMessageStatusUpdate
import com.example.core.resilience.StructuredLogger
import com.example.domain.automation.DispatchBlockReason
import com.example.domain.automation.DispatchDecision
import com.example.domain.automation.DispatchEligibilityPolicy
import com.example.domain.dispatch.buildMessageDispatchRequest
import com.example.domain.dispatch.newDispatchAttempt
import com.example.domain.dispatch.toEntity
import com.example.domain.model.MessageStatus
import com.example.domain.model.dispatch.DispatchAttemptCreator
import com.example.domain.model.dispatch.DispatchAttemptResult
import com.example.domain.model.dispatch.DispatchEligibilityRecord
import com.example.domain.model.message.MessageDispatchState
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
        val inputCommand = inputData.toMessageDispatchWorkerInputCommand()
        if (inputCommand == null) {
            StructuredLogger.e(TAG, "Missing pending_message_id and event_id in input data")
            return Result.failure()
        }

        StructuredLogger.i(TAG, "Dispatching message", inputCommand.logFields())

        val pendingMsg = pendingMessageDao.getMessageDispatchState(inputCommand) ?: run {
            StructuredLogger.w(TAG, "No pending message found", extras = inputCommand.logFields())
            return Result.failure()
        }

        val recipient = contactDao.getMessageDispatchRecipientById(pendingMsg.contactId.value) ?: run {
            recordDispatchAttempt(
                pending = pendingMsg,
                eligibilityDecision = DispatchEligibilityRecord.BLOCKED,
                result = DispatchAttemptResult.BLOCKED,
                reason = "CONTACT_NOT_FOUND",
                resolvedAtMs = System.currentTimeMillis(),
            )
            StructuredLogger.w(TAG, "Contact not found for ${pendingMsg.contactId.value}")
            return Result.failure()
        }

        val now = System.currentTimeMillis()
        when (val decision = DispatchEligibilityPolicy.evaluate(
            draft = pendingMsg.draft,
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
                    "pendingMessageId" to pendingMsg.id.value,
                    "scheduledForMs" to decision.epochMs.toString(),
                    "reason" to decision.reason.name,
                ))
                if (decision.epochMs != pendingMsg.draft.scheduledForMs) {
                    pendingMessageDao.saveMessageDispatchDeferralScheduleUpdate(
                        pendingMsg.toExactSendScheduleUpdate(decision.epochMs)
                    )
                }
                DailyScheduler.scheduleExactSendCommand(context, pendingMsg.toExactSendCommand())
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
                    "pendingMessageId" to pendingMsg.id.value,
                    "approvalMode" to decision.approvalMode.raw,
                ))
                return Result.success()
            }
            is DispatchDecision.Expire -> {
                StructuredLogger.i(TAG, "Approval deadline passed without user action; expiring", mapOf(
                    "pendingMessageId" to pendingMsg.id.value,
                    "reason" to decision.reason.name,
                ))
                pendingMessageDao.saveMessageStatusUpdate(pendingMsg.statusUpdate(MessageStatus.EXPIRED))
                val expired = pendingMsg.withStatus(MessageStatus.EXPIRED)
                recordDispatchAttempt(
                    pending = expired,
                    eligibilityDecision = DispatchEligibilityRecord.EXPIRED,
                    result = DispatchAttemptResult.EXPIRED,
                    reason = decision.reason.name,
                    resolvedAtMs = System.currentTimeMillis(),
                )
                com.example.core.automation.notifications.NotificationHelper.showSetupNotification(
                    context,
                    context.getString(R.string.notification_setup_message_expired_title),
                    context.getString(R.string.notification_setup_message_expired_message, recipient.displayName),
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
                    "pendingMessageId" to pendingMsg.id.value,
                    "status" to pendingMsg.status.raw,
                    "reason" to decision.reason.name,
                ))
                if (decision.reason == DispatchBlockReason.ALREADY_HANDLED) {
                    com.example.core.automation.notifications.NotificationHelper.showSetupNotification(
                        context,
                        context.getString(R.string.notification_setup_double_send_title),
                        context.getString(R.string.notification_setup_double_send_message, recipient.displayName),
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
        pendingMessageDao.saveMessageStatusUpdate(pendingMsg.statusUpdate(MessageStatus.DISPATCHING))

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
                    message = pendingMsg.dispatchDraft,
                    recipient = recipient,
                    dispatchAttemptId = attemptId,
                ),
            )
        } catch (e: Exception) {
            StructuredLogger.e(TAG, "Dispatch failed unexpectedly for message ${pendingMsg.id.value}", e)
            val failedAtMs = System.currentTimeMillis()
            runCatching {
                dispatchAttemptDao.saveMessageDispatchExceptionOutcome(
                    messageDispatchExceptionOutcomeUpdate(
                        dispatchAttemptId = attemptId,
                        failedAtMs = failedAtMs,
                        exception = e,
                    )
                )
            }.onFailure { attemptError ->
                StructuredLogger.e(TAG, "Failed to update dispatch attempt $attemptId after dispatch exception", attemptError)
            }
            runCatching {
                pendingMessageDao.saveMessageStatusUpdate(pendingMsg.statusUpdate(MessageStatus.FAILED))
            }.onFailure { statusError ->
                StructuredLogger.e(TAG, "Failed to mark message ${pendingMsg.id.value} as FAILED after dispatch exception", statusError)
            }
            return Result.failure()
        }

        return Result.success()
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
