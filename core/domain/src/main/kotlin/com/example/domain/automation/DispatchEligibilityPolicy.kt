package com.example.domain.automation

import com.example.core.db.entities.PendingMessageEntity
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageStatus

object DispatchEligibilityPolicy {
    const val DEFAULT_APPROVAL_WINDOW_MS: Long = 2 * 60 * 60 * 1000L

    fun evaluate(
        pending: PendingMessageEntity,
        approvalMode: ApprovalMode,
        nowMs: Long = System.currentTimeMillis(),
        approvalWindowMs: Long = DEFAULT_APPROVAL_WINDOW_MS,
        quietHoursStart: Int? = null,
        quietHoursEnd: Int? = null,
        blackoutDatesJson: String? = null,
    ): DispatchDecision {
        val status = MessageStatus.fromRaw(pending.status)

        return when (status) {
            MessageStatus.APPROVED -> approvedDecision(
                pending = pending,
                nowMs = nowMs,
                quietHoursStart = quietHoursStart,
                quietHoursEnd = quietHoursEnd,
                blackoutDatesJson = blackoutDatesJson,
            )
            MessageStatus.PENDING -> pendingDecision(
                pending = pending,
                approvalMode = approvalMode,
                nowMs = nowMs,
                approvalWindowMs = approvalWindowMs,
                quietHoursStart = quietHoursStart,
                quietHoursEnd = quietHoursEnd,
                blackoutDatesJson = blackoutDatesJson,
            )
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
        quietHoursStart: Int?,
        quietHoursEnd: Int?,
        blackoutDatesJson: String?,
    ): DispatchDecision {
        return if (nowMs < pending.scheduledForMs) {
            DispatchDecision.DeferUntil(
                epochMs = pending.scheduledForMs,
                reason = DispatchDeferReason.BEFORE_SCHEDULED_TIME,
            )
        } else {
            sendNowOrDeferForAllowedWindow(
                nowMs = nowMs,
                quietHoursStart = quietHoursStart,
                quietHoursEnd = quietHoursEnd,
                blackoutDatesJson = blackoutDatesJson,
            )
        }
    }

    private fun pendingDecision(
        pending: PendingMessageEntity,
        approvalMode: ApprovalMode,
        nowMs: Long,
        approvalWindowMs: Long,
        quietHoursStart: Int?,
        quietHoursEnd: Int?,
        blackoutDatesJson: String?,
    ): DispatchDecision {
        return when (approvalMode) {
            ApprovalMode.FULLY_AUTO -> approvedDecision(
                pending = pending,
                nowMs = nowMs,
                quietHoursStart = quietHoursStart,
                quietHoursEnd = quietHoursEnd,
                blackoutDatesJson = blackoutDatesJson,
            )
            ApprovalMode.SMART_APPROVE -> {
                if (nowMs < pending.scheduledForMs) {
                    DispatchDecision.NeedsApproval(approvalMode)
                } else {
                    sendNowOrDeferForAllowedWindow(
                        nowMs = nowMs,
                        quietHoursStart = quietHoursStart,
                        quietHoursEnd = quietHoursEnd,
                        blackoutDatesJson = blackoutDatesJson,
                    )
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

    private fun sendNowOrDeferForAllowedWindow(
        nowMs: Long,
        quietHoursStart: Int?,
        quietHoursEnd: Int?,
        blackoutDatesJson: String?,
    ): DispatchDecision {
        if (quietHoursStart == null || quietHoursEnd == null || blackoutDatesJson == null) {
            return DispatchDecision.SendNow
        }

        val nextAllowedSendMs = AutomationSchedulePolicy.nextAllowedSendMs(
            candidateMs = nowMs,
            quietHoursStart = quietHoursStart,
            quietHoursEnd = quietHoursEnd,
            blackoutDatesJson = blackoutDatesJson,
            nowMs = nowMs,
        )
        return if (nextAllowedSendMs > nowMs) {
            DispatchDecision.DeferUntil(
                epochMs = nextAllowedSendMs,
                reason = DispatchDeferReason.QUIET_HOURS_OR_BLACKOUT_DATE,
            )
        } else {
            DispatchDecision.SendNow
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
    QUIET_HOURS_OR_BLACKOUT_DATE,
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
