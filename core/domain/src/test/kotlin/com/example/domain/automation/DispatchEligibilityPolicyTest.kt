package com.example.domain.automation

import com.example.core.db.entities.PendingMessageEntity
import com.example.domain.model.ApprovalMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DispatchEligibilityPolicyTest {

    private val nowMs = 1_000_000L

    @Test
    fun `approved future message is deferred until scheduled time`() {
        val pending = pending(
            status = "APPROVED",
            approvalMode = "FULLY_AUTO",
            scheduledForMs = nowMs + 60_000L,
        )

        val decision = DispatchEligibilityPolicy.evaluate(pending, nowMs = nowMs)

        assertTrue(decision is DispatchDecision.DeferUntil)
        assertEquals(nowMs + 60_000L, (decision as DispatchDecision.DeferUntil).epochMs)
        assertEquals(DispatchDeferReason.BEFORE_SCHEDULED_TIME, decision.reason)
    }

    @Test
    fun `approved due message can send now`() {
        val pending = pending(
            status = "APPROVED",
            approvalMode = "FULLY_AUTO",
            scheduledForMs = nowMs,
        )

        val decision = DispatchEligibilityPolicy.evaluate(pending, nowMs = nowMs)

        assertEquals(DispatchDecision.SendNow, decision)
    }

    @Test
    fun `smart approve pending message needs approval before schedule`() {
        val pending = pending(
            status = "PENDING",
            approvalMode = "SMART_APPROVE",
            scheduledForMs = nowMs + 60_000L,
        )

        val decision = DispatchEligibilityPolicy.evaluate(pending, nowMs = nowMs)

        assertTrue(decision is DispatchDecision.NeedsApproval)
        assertEquals(ApprovalMode.SMART_APPROVE, (decision as DispatchDecision.NeedsApproval).approvalMode)
    }

    @Test
    fun `smart approve pending message can send at scheduled time`() {
        val pending = pending(
            status = "PENDING",
            approvalMode = "SMART_APPROVE",
            scheduledForMs = nowMs,
        )

        val decision = DispatchEligibilityPolicy.evaluate(pending, nowMs = nowMs)

        assertEquals(DispatchDecision.SendNow, decision)
    }

    @Test
    fun `vip approve pending message expires after approval window`() {
        val pending = pending(
            status = "PENDING",
            approvalMode = "VIP_APPROVE",
            scheduledForMs = nowMs - DispatchEligibilityPolicy.DEFAULT_APPROVAL_WINDOW_MS,
        )

        val decision = DispatchEligibilityPolicy.evaluate(pending, nowMs = nowMs)

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
            approvalMode = "ALWAYS_ASK",
            scheduledForMs = nowMs,
        )

        val decision = DispatchEligibilityPolicy.evaluate(pending, nowMs = nowMs)

        assertTrue(decision is DispatchDecision.NeedsApproval)
        assertEquals(ApprovalMode.ALWAYS_ASK, (decision as DispatchDecision.NeedsApproval).approvalMode)
    }

    @Test
    fun `dispatching message is blocked as already handled`() {
        val pending = pending(
            status = "DISPATCHING",
            approvalMode = "FULLY_AUTO",
            scheduledForMs = nowMs,
        )

        val decision = DispatchEligibilityPolicy.evaluate(pending, nowMs = nowMs)

        assertTrue(decision is DispatchDecision.Blocked)
        assertEquals(DispatchBlockReason.ALREADY_HANDLED, (decision as DispatchDecision.Blocked).reason)
    }

    private fun pending(
        status: String,
        approvalMode: String,
        scheduledForMs: Long,
    ) = PendingMessageEntity(
        id = "msg_1",
        contactId = "contact_1",
        eventId = "event_1",
        shortVariant = "Short",
        standardVariant = "Standard",
        longVariant = "Long",
        formalVariant = "Formal",
        funnyVariant = "Funny",
        emotionalVariant = "Emotional",
        selectedVariant = "standard",
        selectedVariantText = "Standard",
        channel = "SMS",
        scheduledForMs = scheduledForMs,
        approvalMode = approvalMode,
        status = status,
    )
}
