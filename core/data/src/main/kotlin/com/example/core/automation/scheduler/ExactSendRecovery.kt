package com.example.core.automation.scheduler

import android.content.Context
import com.example.core.db.AppDatabase
import com.example.core.db.dao.DispatchAttemptDao
import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.entities.DispatchAttemptEntity
import com.example.core.db.entities.PendingMessageEntity
import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.MessageStatus
import com.example.domain.model.dispatch.DispatchAttemptResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal object ExactSendRecovery {
    private const val STALE_DISPATCHING_GRACE_MS = 30 * 60 * 1000L
    private const val INTERRUPTED_DISPATCH_ERROR_TYPE = "INTERRUPTED_DISPATCH"
    private const val INTERRUPTED_DISPATCH_MESSAGE =
        "Dispatch was interrupted before RelateAI could confirm provider outcome. Review before retrying to avoid duplicate sends."

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
        val staleAttemptCutoffMs = nowMs - staleDispatchingGraceMs.coerceAtLeast(0L)
        pendingMessageDao.getDispatchingMessages().forEach { pending ->
            val latestAttempt = dispatchAttemptDao.getLatestForMessageDraft(pending.id) ?: return@forEach
            if (latestAttempt.requestedAtMs > staleAttemptCutoffMs) return@forEach

            recoverInterruptedDispatch(
                pendingMessageDao = pendingMessageDao,
                dispatchAttemptDao = dispatchAttemptDao,
                pending = pending,
                latestAttempt = latestAttempt,
                nowMs = nowMs,
            )
        }
    }

    private suspend fun recoverInterruptedDispatch(
        pendingMessageDao: PendingMessageDao,
        dispatchAttemptDao: DispatchAttemptDao,
        pending: PendingMessageEntity,
        latestAttempt: DispatchAttemptEntity,
        nowMs: Long,
    ) {
        when (DispatchAttemptResult.fromRaw(latestAttempt.result)) {
            DispatchAttemptResult.SENT,
            DispatchAttemptResult.DELIVERED,
            DispatchAttemptResult.PENDING_DELIVERY -> {
                pendingMessageDao.updateStatusIfCurrent(
                    id = pending.id,
                    expectedStatus = MessageStatus.DISPATCHING.raw,
                    newStatus = MessageStatus.SENT.raw,
                )
            }

            DispatchAttemptResult.RETRY_QUEUED,
            DispatchAttemptResult.FAILED_RETRYABLE -> {
                latestAttempt.nextRetryAtMs?.let { retryAtMs ->
                    pendingMessageDao.updateStatusAndScheduledForIfCurrent(
                        id = pending.id,
                        expectedStatus = MessageStatus.DISPATCHING.raw,
                        newStatus = MessageStatus.APPROVED.raw,
                        scheduledForMs = retryAtMs,
                    )
                } ?: failInterruptedDispatch(
                    pendingMessageDao = pendingMessageDao,
                    dispatchAttemptDao = dispatchAttemptDao,
                    pending = pending,
                    latestAttempt = latestAttempt,
                    nowMs = nowMs,
                )
            }

            DispatchAttemptResult.EXPIRED -> {
                pendingMessageDao.updateStatusIfCurrent(
                    id = pending.id,
                    expectedStatus = MessageStatus.DISPATCHING.raw,
                    newStatus = MessageStatus.EXPIRED.raw,
                )
            }

            DispatchAttemptResult.QUEUED,
            DispatchAttemptResult.DEFERRED,
            DispatchAttemptResult.NEEDS_APPROVAL,
            DispatchAttemptResult.BLOCKED,
            DispatchAttemptResult.FAILED_FINAL,
            DispatchAttemptResult.CANCELLED,
            DispatchAttemptResult.UNKNOWN -> {
                failInterruptedDispatch(
                    pendingMessageDao = pendingMessageDao,
                    dispatchAttemptDao = dispatchAttemptDao,
                    pending = pending,
                    latestAttempt = latestAttempt,
                    nowMs = nowMs,
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
            result = DispatchAttemptResult.FAILED_FINAL.raw,
            channel = null,
            deliveryStatus = MessageDeliveryStatus.FAILED.raw,
            providerMessageId = latestAttempt.providerMessageId,
            errorType = latestAttempt.errorType ?: INTERRUPTED_DISPATCH_ERROR_TYPE,
            errorCode = latestAttempt.errorCode,
            redactedErrorMessage = latestAttempt.redactedErrorMessage ?: INTERRUPTED_DISPATCH_MESSAGE,
            retryCount = latestAttempt.retryCount,
            nextRetryAtMs = null,
            deadLetteredAtMs = nowMs,
        )
    }
}
