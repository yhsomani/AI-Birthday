package com.example.core.automation.notifications

import com.example.domain.automation.DispatchBlockReason
import com.example.domain.automation.DispatchEligibilityPolicy
import com.example.domain.automation.DispatchExpireReason
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.message.MessageDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovalNotificationActionPolicyTest {
    @Test
    fun `approve schedules future always ask message instead of dispatching immediately`() {
        val nowMs = 1_000L
        val scheduledForMs = nowMs + 60_000L
        val pending = pendingMessage(
            approvalMode = "ALWAYS_ASK",
            status = "PENDING",
            scheduledForMs = scheduledForMs,
        )

        val action = ApprovalNotificationActionPolicy.approveAction(pending, nowMs = nowMs)

        assertEquals(ApprovalNotificationAction.ApproveAndSchedule(scheduledForMs), action)
    }

    @Test
    fun `approve dispatches due always ask message`() {
        val nowMs = 1_000L
        val pending = pendingMessage(
            approvalMode = "ALWAYS_ASK",
            status = "PENDING",
            scheduledForMs = nowMs,
        )

        val action = ApprovalNotificationActionPolicy.approveAction(pending, nowMs = nowMs)

        assertEquals(ApprovalNotificationAction.ApproveAndDispatchNow, action)
    }

    @Test
    fun `approve dispatches due smart approve message through shared policy`() {
        val nowMs = 1_000L
        val pending = pendingMessage(
            approvalMode = "SMART_APPROVE",
            status = "PENDING",
            scheduledForMs = nowMs,
        )

        val action = ApprovalNotificationActionPolicy.approveAction(pending, nowMs = nowMs)

        assertEquals(ApprovalNotificationAction.ApproveAndDispatchNow, action)
    }

    @Test
    fun `approve expires vip message after approval window instead of sending`() {
        val nowMs = 10_000L
        val pending = pendingMessage(
            approvalMode = "VIP_APPROVE",
            status = "PENDING",
            scheduledForMs = nowMs - DispatchEligibilityPolicy.DEFAULT_APPROVAL_WINDOW_MS,
        )

        val action = ApprovalNotificationActionPolicy.approveAction(pending, nowMs = nowMs)

        assertEquals(
            ApprovalNotificationAction.Expire(DispatchExpireReason.APPROVAL_WINDOW_ELAPSED),
            action,
        )
    }

    @Test
    fun `approve blocks already sent message`() {
        val action = ApprovalNotificationActionPolicy.approveAction(
            pendingMessage(
                approvalMode = "ALWAYS_ASK",
                status = "SENT",
                scheduledForMs = 0L,
            ),
            nowMs = 1_000L,
        )

        assertTrue(action is ApprovalNotificationAction.Blocked)
        assertEquals(DispatchBlockReason.ALREADY_HANDLED, (action as ApprovalNotificationAction.Blocked).reason)
    }

    private fun pendingMessage(
        approvalMode: String,
        status: String,
        scheduledForMs: Long,
    ): MessageDraft {
        return MessageDraft(
            id = MessageDraftId("msg_1"),
            contactId = ContactId("c1"),
            occasionId = OccasionId("e1"),
            scheduledForMs = scheduledForMs,
            approvalMode = ApprovalMode.fromRaw(approvalMode),
            status = MessageStatus.fromRaw(status),
            channel = MessageChannel.SMS,
            scheduledYear = 2026,
            qualityScore = 0,
            isUsingFallback = false,
        )
    }
}
