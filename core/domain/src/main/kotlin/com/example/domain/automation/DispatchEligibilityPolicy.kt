package com.example.domain.automation

import com.example.core.db.entities.PendingMessageEntity
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageStatus

object DispatchEligibilityPolicy {
    const val DEFAULT_APPROVAL_WINDOW_MS: Long = 2 * 60 * 60 * 1000L

    fun evaluate(
        pending: PendingMessageEntity,
        nowMs: Long = System.currentTimeMillis(),
        approvalWindowMs: Long = DEFAULT_APPROVAL_WINDOW_MS,
    ): DispatchDecision {
        val status = MessageStatus.fromRaw(pending.status)
        val approvalMode = ApprovalMode.fromRaw(pending.approvalMode)

        return when (status) {
            MessageStatus.APPROVED -> approvedDecision(pending, nowMs)
            MessageStatus.PENDING -> pendingDecision(pending, approvalMode, nowMs, approvalWindowMs)
            MessageStatus.SENT,
            MessageStatus.DISPATCHING -> DispatchDecision.Blocked(DispatchBlockReason.ALREADY_HANDLED)
            MessageStatus.REJECTED -> DispatchDecision.Blocked(DispatchBlockReason.REJECTED)
            MessageStatus.EXPIRED -> DispatchDecision.Blocked(DispatchBlockReason.EXPIRED)
            MessageStatus.FAILED -> DispatchDecision.Blocked(DispatchBlockReason.FAILED)
            MessageStatus.UNKNOWN -> DispatchDecision.Blocked(DispatchBlockReason.UNSUPPORTED_STATE)
        }
    }

    private fun approvedDecision(
        pending: PendingMessageEntity,
        nowMs: Long,
    ): DispatchDecision {
        return if (nowMs < pending.scheduledForMs) {
            DispatchDecision.DeferUntil(
                epochMs = pending.scheduledForMs,
                reason = DispatchDeferReason.BEFORE_SCHEDULED_TIME,
            )
        } else {
            DispatchDecision.SendNow
        }
    }

    private fun pendingDecision(
        pending: PendingMessageEntity,
        approvalMode: ApprovalMode,
        nowMs: Long,
        approvalWindowMs: Long,
    ): DispatchDecision {
        return when (approvalMode) {
            ApprovalMode.FULLY_AUTO -> approvedDecision(pending, nowMs)
            ApprovalMode.SMART_APPROVE -> {
                if (nowMs < pending.scheduledForMs) {
                    DispatchDecision.NeedsApproval(approvalMode)
                } else {
                    DispatchDecision.SendNow
                }
            }
            ApprovalMode.VIP_APPROVE -> {
                val approvalDeadlineMs = pending.scheduledForMs + approvalWindowMs
                if (nowMs >= approvalDeadlineMs) {
                    DispatchDecision.Expire(DispatchExpireReason.APPROVAL_WINDOW_ELAPSED)
                } else {
                    DispatchDecision.NeedsApproval(approvalMode)
                }
            }
            ApprovalMode.ALWAYS_ASK,
            ApprovalMode.DEFAULT,
            ApprovalMode.UNKNOWN -> DispatchDecision.NeedsApproval(approvalMode)
        }
    }
}

sealed interface DispatchDecision {
    data object SendNow : DispatchDecision
    data class DeferUntil(
        val epochMs: Long,
        val reason: DispatchDeferReason,
    ) : DispatchDecision
    data class NeedsApproval(val approvalMode: ApprovalMode) : DispatchDecision
    data class Expire(val reason: DispatchExpireReason) : DispatchDecision
    data class Blocked(val reason: DispatchBlockReason) : DispatchDecision
}

enum class DispatchDeferReason {
    BEFORE_SCHEDULED_TIME,
}

enum class DispatchExpireReason {
    APPROVAL_WINDOW_ELAPSED,
}

enum class DispatchBlockReason {
    ALREADY_HANDLED,
    REJECTED,
    EXPIRED,
    FAILED,
    UNSUPPORTED_STATE,
}
