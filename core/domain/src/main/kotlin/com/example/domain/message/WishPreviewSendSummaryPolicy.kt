package com.example.domain.message

import com.example.domain.automation.DispatchBlockReason
import com.example.domain.automation.DispatchDecision
import com.example.domain.automation.DispatchDeferReason
import com.example.domain.automation.DispatchEligibilityPolicy
import com.example.domain.automation.DispatchExpireReason
import com.example.domain.automation.AutomationSchedulePolicy
import com.example.domain.automation.DeliveryRouteBlockReason
import com.example.domain.automation.DeliveryRouteReadiness
import com.example.domain.automation.DeliveryRouteReadinessPolicy
import com.example.domain.automation.SetupChannelReadinessPolicy
import com.example.domain.automation.SmsSetupReadiness
import com.example.domain.automation.SmsSetupReadinessReason
import com.example.domain.automation.WhatsAppSetupReadiness
import com.example.domain.automation.WhatsAppSetupReadinessReason
import com.example.domain.model.MessageChannel
import com.example.domain.model.contact.ContactMessageContext
import com.example.domain.model.message.MessageDraft
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

    private fun buildRouteSelectionContext(
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

    private fun buildDeviceSetupContext(
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

    private fun buildRouteContext(
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

    private fun buildDispatchContext(
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
}
