package com.example.domain.message

import com.example.domain.model.MessageChannel
import com.example.domain.model.contact.ContactMessageContext
import com.example.domain.model.message.WishPreviewDraft
import com.example.domain.model.occasion.OccasionType

enum class WishPreviewDispatchState {
    NEEDS_APPROVAL,
    READY_TO_SEND,
    SCHEDULED,
    DEFERRED,
    EXPIRED,
    BLOCKED,
}

enum class WishPreviewDispatchReason {
    APPROVAL_REQUIRED,
    READY_NOW,
    BEFORE_SCHEDULED_TIME,
    QUIET_HOURS_OR_BLACKOUT_DATE,
    APPROVAL_WINDOW_ELAPSED,
    ALREADY_HANDLED,
    REJECTED,
    EXPIRED,
    FAILED,
    UNSUPPORTED_STATE,
}

data class WishPreviewDispatchContext(
    val state: WishPreviewDispatchState = WishPreviewDispatchState.NEEDS_APPROVAL,
    val reason: WishPreviewDispatchReason = WishPreviewDispatchReason.APPROVAL_REQUIRED,
    val effectiveAtMs: Long? = null,
)

enum class WishPreviewRouteState {
    READY,
    BLOCKED,
}

enum class WishPreviewRouteReason {
    READY,
    CONTACT_MISSING,
    CHANNEL_DISABLED,
    MISSING_PHONE,
    MISSING_EMAIL,
    EMAIL_SETUP_MISSING,
    EMAIL_SENDER_INVALID,
    UNSUPPORTED_CHANNEL,
}

data class WishPreviewRouteContext(
    val state: WishPreviewRouteState = WishPreviewRouteState.READY,
    val reason: WishPreviewRouteReason = WishPreviewRouteReason.READY,
)

enum class WishPreviewRouteSelectionState {
    PREFERRED_ROUTE,
    FALLBACK_ROUTE,
}

enum class WishPreviewRouteSelectionReason {
    SELECTED_PREFERRED_CHANNEL,
    SELECTED_NON_PREFERRED_CHANNEL,
}

data class WishPreviewRouteSelectionContext(
    val preferredChannel: String,
    val selectedChannel: String,
    val state: WishPreviewRouteSelectionState = WishPreviewRouteSelectionState.PREFERRED_ROUTE,
    val reason: WishPreviewRouteSelectionReason = WishPreviewRouteSelectionReason.SELECTED_PREFERRED_CHANNEL,
)

enum class WishPreviewDeviceSetupState {
    READY,
    ACTION_REQUIRED,
    NOT_REQUIRED,
}

enum class WishPreviewDeviceSetupReason {
    SMS_READY,
    SMS_PERMISSION_MISSING,
    SMS_DISABLED,
    WHATSAPP_READY,
    WHATSAPP_CONSENT_REQUIRED,
    WHATSAPP_APP_MISSING,
    WHATSAPP_ACCESSIBILITY_MISSING,
    WHATSAPP_DISABLED,
}

data class WishPreviewDeviceSetupContext(
    val state: WishPreviewDeviceSetupState = WishPreviewDeviceSetupState.READY,
    val reason: WishPreviewDeviceSetupReason = WishPreviewDeviceSetupReason.SMS_READY,
)

data class WishPreviewSendSummary(
    val eventType: String,
    val channel: String,
    val scheduledForMs: Long,
    val approvalMode: String,
    val usesFallback: Boolean,
    val dispatchContext: WishPreviewDispatchContext = WishPreviewDispatchContext(),
    val routeContext: WishPreviewRouteContext? = null,
    val routeSelectionContext: WishPreviewRouteSelectionContext? = null,
    val deviceSetupContext: WishPreviewDeviceSetupContext? = null,
)

object WishPreviewSendSummaryPolicy {
    fun build(
        draft: WishPreviewDraft,
        eventType: OccasionType?,
        nowMs: Long = System.currentTimeMillis(),
        quietHoursStart: Int? = null,
        quietHoursEnd: Int? = null,
        blackoutDatesJson: String? = null,
        routeContact: ContactMessageContext? = null,
        channelBlackoutJson: String? = null,
        senderEmail: String? = null,
        senderEmailPassword: String? = null,
        preferredChannel: MessageChannel? = null,
        smsAllowed: Boolean? = null,
        whatsAppConsentGranted: Boolean? = null,
        whatsAppAccessibilityEnabled: Boolean? = null,
        whatsAppInstalled: Boolean? = null,
    ): WishPreviewSendSummary {
        return WishPreviewSendSummary(
            eventType = eventType?.raw ?: OccasionType.BIRTHDAY.raw,
            channel = draft.channel.raw,
            scheduledForMs = draft.scheduledForMs,
            approvalMode = draft.approvalMode.raw,
            usesFallback = draft.isUsingFallback,
            dispatchContext = buildDispatchContext(
                draft = draft,
                nowMs = nowMs,
                quietHoursStart = quietHoursStart,
                quietHoursEnd = quietHoursEnd,
                blackoutDatesJson = blackoutDatesJson,
            ),
            routeContext = buildRouteContext(
                draft = draft,
                routeContact = routeContact,
                channelBlackoutJson = channelBlackoutJson,
                senderEmail = senderEmail,
                senderEmailPassword = senderEmailPassword,
            ),
            routeSelectionContext = buildRouteSelectionContext(
                selectedChannel = draft.channel,
                preferredChannel = preferredChannel,
            ),
            deviceSetupContext = buildDeviceSetupContext(
                draft = draft,
                channelBlackoutJson = channelBlackoutJson,
                smsAllowed = smsAllowed,
                whatsAppConsentGranted = whatsAppConsentGranted,
                whatsAppAccessibilityEnabled = whatsAppAccessibilityEnabled,
                whatsAppInstalled = whatsAppInstalled,
            ),
        )
    }
}
