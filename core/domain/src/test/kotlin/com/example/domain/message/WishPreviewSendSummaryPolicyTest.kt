package com.example.domain.message

import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageStatus
import com.example.domain.automation.DispatchEligibilityPolicy
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.contact.ContactMessageContext
import com.example.domain.model.message.WishPreviewDraft
import com.example.domain.model.message.WishPreviewVariants
import com.example.domain.model.occasion.OccasionType
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WishPreviewSendSummaryPolicyTest {

    @Test
    fun `build exposes channel schedule approval and fallback state`() {
        val summary = WishPreviewSendSummaryPolicy.build(
            draft = draft(
                channel = MessageChannel.EMAIL,
                approvalMode = ApprovalMode.SMART_APPROVE,
                scheduledForMs = 1_700_000_000_000L,
                isUsingFallback = true,
            ),
            eventType = OccasionType.ANNIVERSARY,
            nowMs = 1_600_000_000_000L,
        )

        assertEquals(OccasionType.ANNIVERSARY.raw, summary.eventType)
        assertEquals(MessageChannel.EMAIL.raw, summary.channel)
        assertEquals(1_700_000_000_000L, summary.scheduledForMs)
        assertEquals(ApprovalMode.SMART_APPROVE.raw, summary.approvalMode)
        assertTrue(summary.usesFallback)
        assertEquals(WishPreviewDispatchState.NEEDS_APPROVAL, summary.dispatchContext.state)
        assertEquals(WishPreviewDispatchReason.APPROVAL_REQUIRED, summary.dispatchContext.reason)
    }

    @Test
    fun `build defaults missing event type to birthday`() {
        val summary = WishPreviewSendSummaryPolicy.build(
            draft = draft(isUsingFallback = false),
            eventType = null,
        )

        assertEquals(OccasionType.BIRTHDAY.raw, summary.eventType)
        assertFalse(summary.usesFallback)
    }

    @Test
    fun `build exposes scheduled dispatch context for approved future drafts`() {
        val summary = WishPreviewSendSummaryPolicy.build(
            draft = draft(
                status = MessageStatus.APPROVED,
                scheduledForMs = 10_000L,
            ),
            eventType = OccasionType.BIRTHDAY,
            nowMs = 1_000L,
        )

        assertEquals(WishPreviewDispatchState.SCHEDULED, summary.dispatchContext.state)
        assertEquals(WishPreviewDispatchReason.BEFORE_SCHEDULED_TIME, summary.dispatchContext.reason)
        assertEquals(10_000L, summary.dispatchContext.effectiveAtMs)
    }

    @Test
    fun `build exposes expired dispatch context for stale vip approvals`() {
        val summary = WishPreviewSendSummaryPolicy.build(
            draft = draft(
                approvalMode = ApprovalMode.VIP_APPROVE,
                scheduledForMs = 1_000L,
            ),
            eventType = OccasionType.BIRTHDAY,
            nowMs = 1_000L + DispatchEligibilityPolicy.DEFAULT_APPROVAL_WINDOW_MS,
        )

        assertEquals(WishPreviewDispatchState.EXPIRED, summary.dispatchContext.state)
        assertEquals(WishPreviewDispatchReason.APPROVAL_WINDOW_ELAPSED, summary.dispatchContext.reason)
    }

    @Test
    fun `build exposes quiet hours dispatch deferral when timing preferences are supplied`() {
        val quietNowMs = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val summary = WishPreviewSendSummaryPolicy.build(
            draft = draft(
                approvalMode = ApprovalMode.FULLY_AUTO,
                scheduledForMs = quietNowMs,
                status = MessageStatus.APPROVED,
            ),
            eventType = OccasionType.BIRTHDAY,
            nowMs = quietNowMs,
            quietHoursStart = 22,
            quietHoursEnd = 8,
            blackoutDatesJson = "[]",
        )

        assertEquals(WishPreviewDispatchState.DEFERRED, summary.dispatchContext.state)
        assertEquals(
            WishPreviewDispatchReason.QUIET_HOURS_OR_BLACKOUT_DATE,
            summary.dispatchContext.reason,
        )
        assertTrue((summary.dispatchContext.effectiveAtMs ?: 0L) > quietNowMs)
    }

    @Test
    fun `build exposes ready route context when selected channel has contact details`() {
        val summary = WishPreviewSendSummaryPolicy.build(
            draft = draft(channel = MessageChannel.SMS),
            eventType = OccasionType.BIRTHDAY,
            routeContact = contact(primaryPhone = "+15555550101"),
            channelBlackoutJson = "[]",
            senderEmail = "",
            senderEmailPassword = "",
        )

        assertEquals(WishPreviewRouteState.READY, summary.routeContext?.state)
        assertEquals(WishPreviewRouteReason.READY, summary.routeContext?.reason)
    }

    @Test
    fun `build exposes fallback route selection context when selected channel differs from preference`() {
        val summary = WishPreviewSendSummaryPolicy.build(
            draft = draft(channel = MessageChannel.SMS),
            eventType = OccasionType.BIRTHDAY,
            preferredChannel = MessageChannel.EMAIL,
        )

        assertEquals(WishPreviewRouteSelectionState.FALLBACK_ROUTE, summary.routeSelectionContext?.state)
        assertEquals(
            WishPreviewRouteSelectionReason.SELECTED_NON_PREFERRED_CHANNEL,
            summary.routeSelectionContext?.reason,
        )
        assertEquals(MessageChannel.EMAIL.raw, summary.routeSelectionContext?.preferredChannel)
        assertEquals(MessageChannel.SMS.raw, summary.routeSelectionContext?.selectedChannel)
    }

    @Test
    fun `build exposes blocked route context for missing email sender setup`() {
        val summary = WishPreviewSendSummaryPolicy.build(
            draft = draft(channel = MessageChannel.EMAIL),
            eventType = OccasionType.BIRTHDAY,
            routeContact = contact(primaryEmail = "friend@example.com"),
            channelBlackoutJson = "[]",
            senderEmail = "",
            senderEmailPassword = "",
        )

        assertEquals(WishPreviewRouteState.BLOCKED, summary.routeContext?.state)
        assertEquals(WishPreviewRouteReason.EMAIL_SETUP_MISSING, summary.routeContext?.reason)
    }

    @Test
    fun `build exposes SMS device setup context when permission is missing`() {
        val summary = WishPreviewSendSummaryPolicy.build(
            draft = draft(channel = MessageChannel.SMS),
            eventType = OccasionType.BIRTHDAY,
            channelBlackoutJson = "[]",
            smsAllowed = false,
        )

        assertEquals(WishPreviewDeviceSetupState.ACTION_REQUIRED, summary.deviceSetupContext?.state)
        assertEquals(
            WishPreviewDeviceSetupReason.SMS_PERMISSION_MISSING,
            summary.deviceSetupContext?.reason,
        )
    }

    @Test
    fun `build exposes WhatsApp device setup context when consent is missing`() {
        val summary = WishPreviewSendSummaryPolicy.build(
            draft = draft(channel = MessageChannel.WHATSAPP),
            eventType = OccasionType.BIRTHDAY,
            channelBlackoutJson = "[]",
            whatsAppConsentGranted = false,
            whatsAppAccessibilityEnabled = true,
            whatsAppInstalled = true,
        )

        assertEquals(WishPreviewDeviceSetupState.ACTION_REQUIRED, summary.deviceSetupContext?.state)
        assertEquals(
            WishPreviewDeviceSetupReason.WHATSAPP_CONSENT_REQUIRED,
            summary.deviceSetupContext?.reason,
        )
    }

    @Test
    fun `build treats disabled selected channel as no device action required`() {
        val summary = WishPreviewSendSummaryPolicy.build(
            draft = draft(channel = MessageChannel.SMS),
            eventType = OccasionType.BIRTHDAY,
            channelBlackoutJson = """["SMS"]""",
            smsAllowed = false,
        )

        assertEquals(WishPreviewDeviceSetupState.NOT_REQUIRED, summary.deviceSetupContext?.state)
        assertEquals(WishPreviewDeviceSetupReason.SMS_DISABLED, summary.deviceSetupContext?.reason)
    }

    private fun draft(
        channel: MessageChannel = MessageChannel.SMS,
        approvalMode: ApprovalMode = ApprovalMode.VIP_APPROVE,
        scheduledForMs: Long = 1_700_000_000_000L,
        isUsingFallback: Boolean = false,
        status: MessageStatus = MessageStatus.PENDING,
    ): WishPreviewDraft {
        return WishPreviewDraft(
            id = MessageDraftId("pm_1"),
            contactId = ContactId("c_1"),
            occasionId = OccasionId("e_1"),
            variants = WishPreviewVariants(
                short = "Happy birthday!",
                standard = "Wishing you a happy birthday!",
                long = "On this special day, I wish you all the best.",
                formal = "Wishing you a very happy birthday, dear friend.",
                funny = "Another year older, still awesome!",
                emotional = "You mean the world to me. Happy birthday!",
            ),
            selectedVariant = "standard",
            selectedVariantText = "Wishing you a happy birthday!",
            channel = channel,
            scheduledForMs = scheduledForMs,
            approvalMode = approvalMode,
            status = status,
            isUsingFallback = isUsingFallback,
        )
    }

    private fun contact(
        primaryPhone: String? = null,
        primaryEmail: String? = null,
    ): ContactMessageContext {
        return ContactMessageContext(
            id = ContactId("c_1"),
            displayName = "Taylor",
            avatarUrl = null,
            primaryPhone = primaryPhone,
            primaryEmail = primaryEmail,
        )
    }
}
