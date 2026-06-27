package com.example.domain.automation

import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.message.MessageDraft
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DispatchEligibilityPolicyTest {

    private val nowMs = 1_000_000L

    @Test
    fun `approved future message is deferred until scheduled time`() {
        val pending = pending(
            status = "APPROVED",
            approvalMode = ApprovalMode.FULLY_AUTO,
            scheduledForMs = nowMs + 60_000L,
        )

        val decision = DispatchEligibilityPolicy.evaluate(
            draft = pending,
            approvalMode = ApprovalMode.FULLY_AUTO,
            nowMs = nowMs,
        )

        assertTrue(decision is DispatchDecision.DeferUntil)
        assertEquals(nowMs + 60_000L, (decision as DispatchDecision.DeferUntil).epochMs)
        assertEquals(DispatchDeferReason.BEFORE_SCHEDULED_TIME, decision.reason)
    }

    @Test
    fun `approved due message can send now`() {
        val pending = pending(
            status = "APPROVED",
            approvalMode = ApprovalMode.FULLY_AUTO,
            scheduledForMs = nowMs,
        )

        val decision = DispatchEligibilityPolicy.evaluate(
            draft = pending,
            approvalMode = ApprovalMode.FULLY_AUTO,
            nowMs = nowMs,
        )

        assertEquals(DispatchDecision.SendNow, decision)
    }

    @Test
    fun `approved due message defers during quiet hours`() {
        val quietNowMs = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val pending = pending(
            status = "APPROVED",
            approvalMode = ApprovalMode.FULLY_AUTO,
            scheduledForMs = quietNowMs,
        )

        val decision = DispatchEligibilityPolicy.evaluate(
            draft = pending,
            approvalMode = ApprovalMode.FULLY_AUTO,
            nowMs = quietNowMs,
            quietHoursStart = 22,
            quietHoursEnd = 8,
            blackoutDatesJson = "[]",
        )

        assertTrue(decision is DispatchDecision.DeferUntil)
        assertEquals(
            DispatchDeferReason.QUIET_HOURS_OR_BLACKOUT_DATE,
            (decision as DispatchDecision.DeferUntil).reason,
        )
        assertTrue(decision.epochMs > quietNowMs)
    }

    @Test
    fun `smart approve pending message needs approval before schedule`() {
        val pending = pending(
            status = "PENDING",
            approvalMode = ApprovalMode.SMART_APPROVE,
            scheduledForMs = nowMs + 60_000L,
        )

        val decision = DispatchEligibilityPolicy.evaluate(
            draft = pending,
            approvalMode = ApprovalMode.SMART_APPROVE,
            nowMs = nowMs,
        )

        assertTrue(decision is DispatchDecision.NeedsApproval)
        assertEquals(ApprovalMode.SMART_APPROVE, (decision as DispatchDecision.NeedsApproval).approvalMode)
    }

    @Test
    fun `smart approve pending message can send at scheduled time`() {
        val pending = pending(
            status = "PENDING",
            approvalMode = ApprovalMode.SMART_APPROVE,
            scheduledForMs = nowMs,
        )

        val decision = DispatchEligibilityPolicy.evaluate(
            draft = pending,
            approvalMode = ApprovalMode.SMART_APPROVE,
            nowMs = nowMs,
        )

        assertEquals(DispatchDecision.SendNow, decision)
    }

    @Test
    fun `vip approve pending message expires after approval window`() {
        val pending = pending(
            status = "PENDING",
            approvalMode = ApprovalMode.VIP_APPROVE,
            scheduledForMs = nowMs - DispatchEligibilityPolicy.DEFAULT_APPROVAL_WINDOW_MS,
        )

        val decision = DispatchEligibilityPolicy.evaluate(
            draft = pending,
            approvalMode = ApprovalMode.VIP_APPROVE,
            nowMs = nowMs,
        )

        assertTrue(decision is DispatchDecision.Expire)
        assertEquals(
            DispatchExpireReason.APPROVAL_WINDOW_ELAPSED,
            (decision as DispatchDecision.Expire).reason,
        )
    }

    @Test
    fun `always ask pending message needs explicit approval`() {
        val pending = pending(
            status = "PENDING",
            approvalMode = ApprovalMode.ALWAYS_ASK,
            scheduledForMs = nowMs,
        )

        val decision = DispatchEligibilityPolicy.evaluate(
            draft = pending,
            approvalMode = ApprovalMode.ALWAYS_ASK,
            nowMs = nowMs,
        )

        assertTrue(decision is DispatchDecision.NeedsApproval)
        assertEquals(ApprovalMode.ALWAYS_ASK, (decision as DispatchDecision.NeedsApproval).approvalMode)
    }

    @Test
    fun `dispatching message is blocked as already handled`() {
        val pending = pending(
            status = "DISPATCHING",
            approvalMode = ApprovalMode.FULLY_AUTO,
            scheduledForMs = nowMs,
        )

        val decision = DispatchEligibilityPolicy.evaluate(
            draft = pending,
            approvalMode = ApprovalMode.FULLY_AUTO,
            nowMs = nowMs,
        )

        assertTrue(decision is DispatchDecision.Blocked)
        assertEquals(DispatchBlockReason.ALREADY_HANDLED, (decision as DispatchDecision.Blocked).reason)
    }

    private fun pending(
        status: String,
        approvalMode: ApprovalMode,
        scheduledForMs: Long,
    ) = MessageDraft(
        id = MessageDraftId("msg_1"),
        contactId = ContactId("contact_1"),
        occasionId = OccasionId("event_1"),
        scheduledForMs = scheduledForMs,
        approvalMode = approvalMode,
        status = MessageStatus.fromRaw(status),
        channel = MessageChannel.SMS,
        scheduledYear = 2026,
        qualityScore = 80,
        isUsingFallback = false,
    )
}
