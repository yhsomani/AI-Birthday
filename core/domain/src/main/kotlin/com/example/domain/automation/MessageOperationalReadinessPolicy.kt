package com.example.domain.automation

import com.example.domain.model.MessageStatus
import com.example.domain.model.contact.ContactMessageContext
import com.example.domain.model.message.MessageDraft
import com.example.domain.model.message.PendingMessageListItem

enum class MessageOperationalReadiness(
    val blocksTaskFlow: Boolean,
    val requiresContactOrChannelFix: Boolean,
) {
    READY_FOR_REVIEW(blocksTaskFlow = false, requiresContactOrChannelFix = false),
    APPROVED_SCHEDULED(blocksTaskFlow = false, requiresContactOrChannelFix = false),
    APPROVED_WAITING_FOR_SCHEDULE(blocksTaskFlow = false, requiresContactOrChannelFix = false),
    APPROVED_WAITING_FOR_ALLOWED_WINDOW(blocksTaskFlow = false, requiresContactOrChannelFix = false),
    SENDING_NOW(blocksTaskFlow = false, requiresContactOrChannelFix = false),
    CONTACT_MISSING(blocksTaskFlow = true, requiresContactOrChannelFix = true),
    CHANNEL_DISABLED(blocksTaskFlow = true, requiresContactOrChannelFix = true),
    MISSING_PHONE(blocksTaskFlow = true, requiresContactOrChannelFix = true),
    MISSING_EMAIL(blocksTaskFlow = true, requiresContactOrChannelFix = true),
    EMAIL_SETUP_MISSING(blocksTaskFlow = true, requiresContactOrChannelFix = true),
    FAILED_CHECK_SETUP(blocksTaskFlow = false, requiresContactOrChannelFix = false),
}

object MessageOperationalReadinessPolicy {
    fun evaluate(
        message: PendingMessageListItem,
        contact: ContactMessageContext?,
        channelBlackoutJson: String,
        senderEmail: String,
        senderEmailPassword: String,
        nowMs: Long = System.currentTimeMillis(),
        quietHoursStart: Int? = null,
        quietHoursEnd: Int? = null,
        blackoutDatesJson: String? = null,
    ): MessageOperationalReadiness {
        val routeReadiness = DeliveryRouteReadinessPolicy.evaluate(
            channel = message.channel,
            contact = contact,
            channelBlackoutJson = channelBlackoutJson,
            senderEmail = senderEmail,
            senderEmailPassword = senderEmailPassword,
        )
        if (routeReadiness is DeliveryRouteReadiness.Blocked) {
            return routeReadiness.reason.toMessageOperationalReadiness()
        }

        return when (message.status) {
            MessageStatus.APPROVED -> approvedReadiness(
                message = message,
                nowMs = nowMs,
                quietHoursStart = quietHoursStart,
                quietHoursEnd = quietHoursEnd,
                blackoutDatesJson = blackoutDatesJson,
            )
            MessageStatus.DISPATCHING -> MessageOperationalReadiness.SENDING_NOW
            MessageStatus.FAILED -> MessageOperationalReadiness.FAILED_CHECK_SETUP
            else -> MessageOperationalReadiness.READY_FOR_REVIEW
        }
    }

    private fun approvedReadiness(
        message: PendingMessageListItem,
        nowMs: Long,
        quietHoursStart: Int?,
        quietHoursEnd: Int?,
        blackoutDatesJson: String?,
    ): MessageOperationalReadiness {
        return when (
            val decision = DispatchEligibilityPolicy.evaluate(
                draft = message.toDispatchDraft(),
                nowMs = nowMs,
                quietHoursStart = quietHoursStart,
                quietHoursEnd = quietHoursEnd,
                blackoutDatesJson = blackoutDatesJson,
            )
        ) {
            DispatchDecision.SendNow -> MessageOperationalReadiness.APPROVED_SCHEDULED
            is DispatchDecision.DeferUntil -> when (decision.reason) {
                DispatchDeferReason.BEFORE_SCHEDULED_TIME ->
                    MessageOperationalReadiness.APPROVED_WAITING_FOR_SCHEDULE
                DispatchDeferReason.QUIET_HOURS_OR_BLACKOUT_DATE ->
                    MessageOperationalReadiness.APPROVED_WAITING_FOR_ALLOWED_WINDOW
            }
            is DispatchDecision.NeedsApproval,
            is DispatchDecision.Expire,
            is DispatchDecision.Blocked -> MessageOperationalReadiness.APPROVED_SCHEDULED
        }
    }

    private fun PendingMessageListItem.toDispatchDraft(): MessageDraft {
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
            isUsingFallback = false,
        )
    }

    private fun DeliveryRouteBlockReason.toMessageOperationalReadiness(): MessageOperationalReadiness {
        return when (this) {
            DeliveryRouteBlockReason.CONTACT_MISSING -> MessageOperationalReadiness.CONTACT_MISSING
            DeliveryRouteBlockReason.CHANNEL_DISABLED,
            DeliveryRouteBlockReason.UNSUPPORTED_CHANNEL -> MessageOperationalReadiness.CHANNEL_DISABLED
            DeliveryRouteBlockReason.MISSING_PHONE -> MessageOperationalReadiness.MISSING_PHONE
            DeliveryRouteBlockReason.MISSING_EMAIL -> MessageOperationalReadiness.MISSING_EMAIL
            DeliveryRouteBlockReason.EMAIL_SENDER_NOT_CONFIGURED,
            DeliveryRouteBlockReason.EMAIL_SENDER_INVALID -> MessageOperationalReadiness.EMAIL_SETUP_MISSING
        }
    }
}
