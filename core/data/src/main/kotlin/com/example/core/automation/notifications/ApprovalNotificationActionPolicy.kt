package com.example.core.automation.notifications

import com.example.core.db.entities.PendingMessageEntity
import com.example.domain.automation.DispatchBlockReason
import com.example.domain.automation.DispatchDecision
import com.example.domain.automation.DispatchEligibilityPolicy
import com.example.domain.automation.DispatchExpireReason
import com.example.domain.message.toMessageDraft
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageStatus

internal object ApprovalNotificationActionPolicy {
    fun approveAction(
        pending: PendingMessageEntity,
        nowMs: Long = System.currentTimeMillis(),
    ): ApprovalNotificationAction {
        val approvalMode = ApprovalMode.fromRaw(pending.approvalMode)
        return when (val currentDecision = DispatchEligibilityPolicy.evaluate(
            draft = pending.toMessageDraft(),
            approvalMode = approvalMode,
            nowMs = nowMs,
        )) {
            is DispatchDecision.Expire -> ApprovalNotificationAction.Expire(currentDecision.reason)
            is DispatchDecision.Blocked -> ApprovalNotificationAction.Blocked(currentDecision.reason)
            DispatchDecision.SendNow,
            is DispatchDecision.DeferUntil,
            is DispatchDecision.NeedsApproval -> approvedAction(pending, approvalMode, nowMs)
        }
    }

    private fun approvedAction(
        pending: PendingMessageEntity,
        approvalMode: ApprovalMode,
        nowMs: Long,
    ): ApprovalNotificationAction {
        val approved = pending.copy(status = MessageStatus.APPROVED.raw)
        return when (val approvedDecision = DispatchEligibilityPolicy.evaluate(
            draft = approved.toMessageDraft(),
            approvalMode = approvalMode,
            nowMs = nowMs,
        )) {
            DispatchDecision.SendNow -> ApprovalNotificationAction.ApproveAndDispatchNow
            is DispatchDecision.DeferUntil -> ApprovalNotificationAction.ApproveAndSchedule(approvedDecision.epochMs)
            is DispatchDecision.Expire -> ApprovalNotificationAction.Expire(approvedDecision.reason)
            is DispatchDecision.Blocked -> ApprovalNotificationAction.Blocked(approvedDecision.reason)
            is DispatchDecision.NeedsApproval -> ApprovalNotificationAction.Blocked(
                DispatchBlockReason.UNSUPPORTED_STATE
            )
        }
    }
}

internal sealed interface ApprovalNotificationAction {
    data object ApproveAndDispatchNow : ApprovalNotificationAction
    data class ApproveAndSchedule(val scheduledForMs: Long) : ApprovalNotificationAction
    data class Expire(val reason: DispatchExpireReason) : ApprovalNotificationAction
    data class Blocked(val reason: DispatchBlockReason) : ApprovalNotificationAction
}
