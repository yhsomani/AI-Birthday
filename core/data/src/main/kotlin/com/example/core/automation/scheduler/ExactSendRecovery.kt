package com.example.core.automation.scheduler

import android.content.Context
import com.example.core.db.AppDatabase
import com.example.core.db.dao.DispatchAttemptDao
import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.entities.DispatchAttemptEntity
import com.example.core.db.entities.PendingMessageEntity
import com.example.domain.automation.ExactSendRecoveryAction
import com.example.domain.automation.ExactSendRecoveryFailure
import com.example.domain.automation.ExactSendRecoveryPolicy
import com.example.domain.model.MessageStatus
import com.example.domain.model.dispatch.DispatchAttemptResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal object ExactSendRecovery {
    private const val STALE_DISPATCHING_GRACE_MS = 30 * 60 * 1000L

    fun recoverAsync(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            recover(context)
        }
    }

    suspend fun recover(context: Context) {
        val db = AppDatabase.getInstance(context)
        recover(
            context = context,
            pendingMessageDao = db.pendingMessageDao(),
            dispatchAttemptDao = db.dispatchAttemptDao(),
        )
    }

    suspend fun recover(
        context: Context,
        pendingMessageDao: PendingMessageDao,
        dispatchAttemptDao: DispatchAttemptDao? = null,
        nowMs: Long = System.currentTimeMillis(),
        staleDispatchingGraceMs: Long = STALE_DISPATCHING_GRACE_MS,
    ) {
        if (dispatchAttemptDao != null) {
            recoverInterruptedDispatches(
                pendingMessageDao = pendingMessageDao,
                dispatchAttemptDao = dispatchAttemptDao,
                nowMs = nowMs,
                staleDispatchingGraceMs = staleDispatchingGraceMs,
            )
        }
        pendingMessageDao.getBootRecoverableExactSendCommands().forEach { command ->
            DailyScheduler.scheduleExactSendCommand(context, command)
        }
    }

    private suspend fun recoverInterruptedDispatches(
        pendingMessageDao: PendingMessageDao,
        dispatchAttemptDao: DispatchAttemptDao,
        nowMs: Long,
        staleDispatchingGraceMs: Long,
    ) {
        pendingMessageDao.getDispatchingMessages().forEach { pending ->
            val latestAttempt = dispatchAttemptDao.getLatestForMessageDraft(pending.id) ?: return@forEach

            recoverInterruptedDispatch(
                pendingMessageDao = pendingMessageDao,
                dispatchAttemptDao = dispatchAttemptDao,
                pending = pending,
                latestAttempt = latestAttempt,
                nowMs = nowMs,
                staleDispatchingGraceMs = staleDispatchingGraceMs,
            )
        }
    }

    private suspend fun recoverInterruptedDispatch(
        pendingMessageDao: PendingMessageDao,
        dispatchAttemptDao: DispatchAttemptDao,
        pending: PendingMessageEntity,
        latestAttempt: DispatchAttemptEntity,
        nowMs: Long,
        staleDispatchingGraceMs: Long,
    ) {
        val decision = ExactSendRecoveryPolicy.evaluateInterruptedDispatch(
            result = DispatchAttemptResult.fromRaw(latestAttempt.result),
            requestedAtMs = latestAttempt.requestedAtMs,
            nowMs = nowMs,
            staleDispatchingGraceMs = staleDispatchingGraceMs,
            nextRetryAtMs = latestAttempt.nextRetryAtMs,
        )

        when (decision.action) {
            ExactSendRecoveryAction.WAIT_FOR_RECOVERY_GRACE -> Unit

            ExactSendRecoveryAction.MARK_SENT -> {
                pendingMessageDao.updateStatusIfCurrent(
                    id = pending.id,
                    expectedStatus = MessageStatus.DISPATCHING.raw,
                    newStatus = requireNotNull(decision.messageStatus).raw,
                )
            }

            ExactSendRecoveryAction.RESCHEDULE_RETRY -> {
                pendingMessageDao.updateStatusAndScheduledForIfCurrent(
                    id = pending.id,
                    expectedStatus = MessageStatus.DISPATCHING.raw,
                    newStatus = requireNotNull(decision.messageStatus).raw,
                    scheduledForMs = requireNotNull(decision.scheduledForMs),
                )
            }

            ExactSendRecoveryAction.MARK_EXPIRED -> {
                pendingMessageDao.updateStatusIfCurrent(
                    id = pending.id,
                    expectedStatus = MessageStatus.DISPATCHING.raw,
                    newStatus = requireNotNull(decision.messageStatus).raw,
                )
            }

            ExactSendRecoveryAction.FAIL_FOR_REVIEW -> {
                failInterruptedDispatch(
                    pendingMessageDao = pendingMessageDao,
                    dispatchAttemptDao = dispatchAttemptDao,
                    pending = pending,
                    latestAttempt = latestAttempt,
                    nowMs = nowMs,
                    failure = requireNotNull(decision.failure),
                )
            }
        }
    }

    private suspend fun failInterruptedDispatch(
        pendingMessageDao: PendingMessageDao,
        dispatchAttemptDao: DispatchAttemptDao,
        pending: PendingMessageEntity,
        latestAttempt: DispatchAttemptEntity,
        nowMs: Long,
        failure: ExactSendRecoveryFailure,
    ) {
        pendingMessageDao.updateStatusIfCurrent(
            id = pending.id,
            expectedStatus = MessageStatus.DISPATCHING.raw,
            newStatus = MessageStatus.FAILED.raw,
        )
        dispatchAttemptDao.updateOutcome(
            id = latestAttempt.id,
            attemptedAtMs = latestAttempt.attemptedAtMs ?: latestAttempt.requestedAtMs,
            resolvedAtMs = nowMs,
            result = failure.result.raw,
            channel = null,
            deliveryStatus = failure.deliveryStatus.raw,
            providerMessageId = latestAttempt.providerMessageId,
            errorType = latestAttempt.errorType ?: failure.errorType,
            errorCode = latestAttempt.errorCode,
            redactedErrorMessage = latestAttempt.redactedErrorMessage ?: failure.redactedErrorMessage,
            retryCount = latestAttempt.retryCount,
            nextRetryAtMs = null,
            deadLetteredAtMs = if (failure.deadLetter) nowMs else null,
        )
    }
}
