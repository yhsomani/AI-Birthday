package com.example.domain.message

import com.example.domain.automation.AutomationSchedulePolicy
import com.example.domain.automation.DeliveryRouteBlockReason
import com.example.domain.automation.DeliveryRouteReadiness
import com.example.domain.automation.DeliveryRouteReadinessPolicy
import com.example.domain.automation.DispatchBlockReason
import com.example.domain.automation.DispatchDecision
import com.example.domain.automation.DispatchDeferReason
import com.example.domain.automation.DispatchEligibilityPolicy
import com.example.domain.automation.DispatchExpireReason
import com.example.domain.automation.SetupChannelReadinessPolicy
import com.example.domain.automation.SmsSetupReadiness
import com.example.domain.automation.SmsSetupReadinessReason
import com.example.domain.automation.WhatsAppSetupReadiness
import com.example.domain.automation.WhatsAppSetupReadinessReason
import com.example.domain.model.MessageChannel
import com.example.domain.model.contact.ContactMessageContext
import com.example.domain.model.message.MessageDraft
import com.example.domain.model.message.WishPreviewDraft

internal fun buildRouteSelectionContext(
    selectedChannel: MessageChannel,
    preferredChannel: MessageChannel?,
): WishPreviewRouteSelectionContext? {
    val preferred = preferredChannel?.takeIf { it != MessageChannel.UNKNOWN } ?: return null
    if (selectedChannel == MessageChannel.UNKNOWN) {
        return null
    }
    val selectedPreferred = selectedChannel == preferred
    return WishPreviewRouteSelectionContext(
        state = if (selectedPreferred) {
            WishPreviewRouteSelectionState.PREFERRED_ROUTE
        } else {
            WishPreviewRouteSelectionState.FALLBACK_ROUTE
        },
        reason = if (selectedPreferred) {
            WishPreviewRouteSelectionReason.SELECTED_PREFERRED_CHANNEL
        } else {
            WishPreviewRouteSelectionReason.SELECTED_NON_PREFERRED_CHANNEL
        },
        preferredChannel = preferred.raw,
        selectedChannel = selectedChannel.raw,
    )
}

internal fun buildDeviceSetupContext(
    draft: WishPreviewDraft,
    channelBlackoutJson: String?,
    smsAllowed: Boolean?,
    whatsAppConsentGranted: Boolean?,
    whatsAppAccessibilityEnabled: Boolean?,
    whatsAppInstalled: Boolean?,
): WishPreviewDeviceSetupContext? {
    val blackoutJson = channelBlackoutJson ?: return null
    return when (draft.channel) {
        MessageChannel.SMS -> smsAllowed?.let { allowed ->
            SetupChannelReadinessPolicy.evaluateSms(
                smsAllowed = allowed,
                selectedSmsContactCount = 1,
                smsDisabled = AutomationSchedulePolicy.isChannelBlocked(
                    MessageChannel.SMS,
                    blackoutJson,
                ),
            ).toWishPreviewDeviceSetupContext()
        }
        MessageChannel.WHATSAPP -> {
            if (
                whatsAppConsentGranted == null ||
                whatsAppAccessibilityEnabled == null ||
                whatsAppInstalled == null
            ) {
                null
            } else {
                SetupChannelReadinessPolicy.evaluateWhatsApp(
                    consentGranted = whatsAppConsentGranted,
                    accessibilityEnabled = whatsAppAccessibilityEnabled,
                    whatsAppInstalled = whatsAppInstalled,
                    selectedWhatsAppContactCount = 1,
                    whatsAppDisabled = AutomationSchedulePolicy.isChannelBlocked(
                        MessageChannel.WHATSAPP,
                        blackoutJson,
                    ),
                ).toWishPreviewDeviceSetupContext()
            }
        }
        MessageChannel.EMAIL,
        MessageChannel.UNKNOWN -> null
    }
}

internal fun buildRouteContext(
    draft: WishPreviewDraft,
    routeContact: ContactMessageContext?,
    channelBlackoutJson: String?,
    senderEmail: String?,
    senderEmailPassword: String?,
): WishPreviewRouteContext? {
    if (channelBlackoutJson == null || senderEmail == null || senderEmailPassword == null) {
        return null
    }
    return DeliveryRouteReadinessPolicy.evaluate(
        channel = draft.channel,
        contact = routeContact,
        channelBlackoutJson = channelBlackoutJson,
        senderEmail = senderEmail,
        senderEmailPassword = senderEmailPassword,
    ).toWishPreviewRouteContext()
}

internal fun buildDispatchContext(
    draft: WishPreviewDraft,
    nowMs: Long,
    quietHoursStart: Int?,
    quietHoursEnd: Int?,
    blackoutDatesJson: String?,
): WishPreviewDispatchContext {
    return DispatchEligibilityPolicy.evaluate(
        draft = draft.toMessageDraft(),
        nowMs = nowMs,
        quietHoursStart = quietHoursStart,
        quietHoursEnd = quietHoursEnd,
        blackoutDatesJson = blackoutDatesJson,
    ).toWishPreviewDispatchContext()
}

private fun SmsSetupReadiness.toWishPreviewDeviceSetupContext(): WishPreviewDeviceSetupContext {
    return when (reason) {
        SmsSetupReadinessReason.DISABLED -> WishPreviewDeviceSetupContext(
            state = WishPreviewDeviceSetupState.NOT_REQUIRED,
            reason = WishPreviewDeviceSetupReason.SMS_DISABLED,
        )
        SmsSetupReadinessReason.NOT_USED,
        SmsSetupReadinessReason.READY -> WishPreviewDeviceSetupContext(
            state = WishPreviewDeviceSetupState.READY,
            reason = WishPreviewDeviceSetupReason.SMS_READY,
        )
        SmsSetupReadinessReason.PERMISSION_MISSING -> WishPreviewDeviceSetupContext(
            state = WishPreviewDeviceSetupState.ACTION_REQUIRED,
            reason = WishPreviewDeviceSetupReason.SMS_PERMISSION_MISSING,
        )
    }
}

private fun WhatsAppSetupReadiness.toWishPreviewDeviceSetupContext(): WishPreviewDeviceSetupContext {
    return when (reason) {
        WhatsAppSetupReadinessReason.DISABLED -> WishPreviewDeviceSetupContext(
            state = WishPreviewDeviceSetupState.NOT_REQUIRED,
            reason = WishPreviewDeviceSetupReason.WHATSAPP_DISABLED,
        )
        WhatsAppSetupReadinessReason.NOT_USED,
        WhatsAppSetupReadinessReason.READY -> WishPreviewDeviceSetupContext(
            state = WishPreviewDeviceSetupState.READY,
            reason = WishPreviewDeviceSetupReason.WHATSAPP_READY,
        )
        WhatsAppSetupReadinessReason.CONSENT_REQUIRED -> WishPreviewDeviceSetupContext(
            state = WishPreviewDeviceSetupState.ACTION_REQUIRED,
            reason = WishPreviewDeviceSetupReason.WHATSAPP_CONSENT_REQUIRED,
        )
        WhatsAppSetupReadinessReason.APP_MISSING -> WishPreviewDeviceSetupContext(
            state = WishPreviewDeviceSetupState.ACTION_REQUIRED,
            reason = WishPreviewDeviceSetupReason.WHATSAPP_APP_MISSING,
        )
        WhatsAppSetupReadinessReason.ACCESSIBILITY_MISSING -> WishPreviewDeviceSetupContext(
            state = WishPreviewDeviceSetupState.ACTION_REQUIRED,
            reason = WishPreviewDeviceSetupReason.WHATSAPP_ACCESSIBILITY_MISSING,
        )
    }
}

private fun DeliveryRouteReadiness.toWishPreviewRouteContext(): WishPreviewRouteContext {
    return when (this) {
        DeliveryRouteReadiness.Ready -> WishPreviewRouteContext(
            state = WishPreviewRouteState.READY,
            reason = WishPreviewRouteReason.READY,
        )
        is DeliveryRouteReadiness.Blocked -> WishPreviewRouteContext(
            state = WishPreviewRouteState.BLOCKED,
            reason = reason.toWishPreviewRouteReason(),
        )
    }
}

private fun DispatchDecision.toWishPreviewDispatchContext(): WishPreviewDispatchContext {
    return when (this) {
        DispatchDecision.SendNow -> WishPreviewDispatchContext(
            state = WishPreviewDispatchState.READY_TO_SEND,
            reason = WishPreviewDispatchReason.READY_NOW,
        )
        is DispatchDecision.DeferUntil -> WishPreviewDispatchContext(
            state = when (reason) {
                DispatchDeferReason.BEFORE_SCHEDULED_TIME -> WishPreviewDispatchState.SCHEDULED
                DispatchDeferReason.QUIET_HOURS_OR_BLACKOUT_DATE -> WishPreviewDispatchState.DEFERRED
            },
            reason = when (reason) {
                DispatchDeferReason.BEFORE_SCHEDULED_TIME ->
                    WishPreviewDispatchReason.BEFORE_SCHEDULED_TIME
                DispatchDeferReason.QUIET_HOURS_OR_BLACKOUT_DATE ->
                    WishPreviewDispatchReason.QUIET_HOURS_OR_BLACKOUT_DATE
            },
            effectiveAtMs = epochMs,
        )
        is DispatchDecision.NeedsApproval -> WishPreviewDispatchContext(
            state = WishPreviewDispatchState.NEEDS_APPROVAL,
            reason = WishPreviewDispatchReason.APPROVAL_REQUIRED,
        )
        is DispatchDecision.Expire -> WishPreviewDispatchContext(
            state = WishPreviewDispatchState.EXPIRED,
            reason = reason.toWishPreviewDispatchReason(),
        )
        is DispatchDecision.Blocked -> WishPreviewDispatchContext(
            state = WishPreviewDispatchState.BLOCKED,
            reason = reason.toWishPreviewDispatchReason(),
        )
    }
}

private fun DispatchExpireReason.toWishPreviewDispatchReason(): WishPreviewDispatchReason {
    return when (this) {
        DispatchExpireReason.APPROVAL_WINDOW_ELAPSED ->
            WishPreviewDispatchReason.APPROVAL_WINDOW_ELAPSED
    }
}

private fun DispatchBlockReason.toWishPreviewDispatchReason(): WishPreviewDispatchReason {
    return when (this) {
        DispatchBlockReason.ALREADY_HANDLED -> WishPreviewDispatchReason.ALREADY_HANDLED
        DispatchBlockReason.REJECTED -> WishPreviewDispatchReason.REJECTED
        DispatchBlockReason.EXPIRED -> WishPreviewDispatchReason.EXPIRED
        DispatchBlockReason.FAILED -> WishPreviewDispatchReason.FAILED
        DispatchBlockReason.UNSUPPORTED_STATE -> WishPreviewDispatchReason.UNSUPPORTED_STATE
    }
}

private fun DeliveryRouteBlockReason.toWishPreviewRouteReason(): WishPreviewRouteReason {
    return when (this) {
        DeliveryRouteBlockReason.CONTACT_MISSING -> WishPreviewRouteReason.CONTACT_MISSING
        DeliveryRouteBlockReason.CHANNEL_DISABLED -> WishPreviewRouteReason.CHANNEL_DISABLED
        DeliveryRouteBlockReason.MISSING_PHONE -> WishPreviewRouteReason.MISSING_PHONE
        DeliveryRouteBlockReason.MISSING_EMAIL -> WishPreviewRouteReason.MISSING_EMAIL
        DeliveryRouteBlockReason.EMAIL_SENDER_NOT_CONFIGURED -> WishPreviewRouteReason.EMAIL_SETUP_MISSING
        DeliveryRouteBlockReason.EMAIL_SENDER_INVALID -> WishPreviewRouteReason.EMAIL_SENDER_INVALID
        DeliveryRouteBlockReason.UNSUPPORTED_CHANNEL -> WishPreviewRouteReason.UNSUPPORTED_CHANNEL
    }
}

private fun WishPreviewDraft.toMessageDraft(): MessageDraft {
    return MessageDraft(
        id = id,
        contactId = contactId,
        occasionId = occasionId,
        scheduledForMs = scheduledForMs,
        approvalMode = approvalMode,
        status = status,
        channel = channel,
        scheduledYear = 0,
        qualityScore = 0,
        isUsingFallback = isUsingFallback,
    )
}
