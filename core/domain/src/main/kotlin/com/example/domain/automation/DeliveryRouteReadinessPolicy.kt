package com.example.domain.automation

import com.example.domain.model.MessageChannel
import com.example.domain.model.contact.ContactDeliveryRouteProfile
import com.example.domain.model.contact.ContactMessageContext

object DeliveryRouteReadinessPolicy {
    fun evaluate(
        channel: MessageChannel,
        contact: ContactDeliveryRouteProfile?,
        channelBlackoutJson: String,
        senderEmail: String,
        senderEmailPassword: String,
    ): DeliveryRouteReadiness {
        val firstReason = blockedReasons(
            channel = channel,
            contact = contact,
            channelBlackoutJson = channelBlackoutJson,
            senderEmail = senderEmail,
            senderEmailPassword = senderEmailPassword,
        ).firstOrNull()
        return if (firstReason == null) {
            DeliveryRouteReadiness.Ready
        } else {
            DeliveryRouteReadiness.Blocked(firstReason)
        }
    }

    fun blockedReasons(
        channel: MessageChannel,
        contact: ContactDeliveryRouteProfile?,
        channelBlackoutJson: String,
        senderEmail: String,
        senderEmailPassword: String,
    ): Set<DeliveryRouteBlockReason> {
        if (contact == null) {
            return setOf(DeliveryRouteBlockReason.CONTACT_MISSING)
        }
        if (AutomationSchedulePolicy.isChannelBlocked(channel, channelBlackoutJson)) {
            return setOf(DeliveryRouteBlockReason.CHANNEL_DISABLED)
        }

        return when (channel) {
            MessageChannel.SMS,
            MessageChannel.WHATSAPP -> if (contact.hasPrimaryPhone) {
                emptySet()
            } else {
                setOf(DeliveryRouteBlockReason.MISSING_PHONE)
            }
            MessageChannel.EMAIL -> emailBlockReasons(
                contact = contact,
                senderEmail = senderEmail,
                senderEmailPassword = senderEmailPassword,
            )
            MessageChannel.UNKNOWN -> setOf(DeliveryRouteBlockReason.UNSUPPORTED_CHANNEL)
        }
    }

    fun evaluate(
        channel: MessageChannel,
        contact: ContactMessageContext?,
        channelBlackoutJson: String,
        senderEmail: String,
        senderEmailPassword: String,
    ): DeliveryRouteReadiness {
        return evaluate(
            channel = channel,
            contact = contact?.toDeliveryRouteProfile(channel),
            channelBlackoutJson = channelBlackoutJson,
            senderEmail = senderEmail,
            senderEmailPassword = senderEmailPassword,
        )
    }

    private fun emailBlockReasons(
        contact: ContactDeliveryRouteProfile,
        senderEmail: String,
        senderEmailPassword: String,
    ): Set<DeliveryRouteBlockReason> {
        val reasons = linkedSetOf<DeliveryRouteBlockReason>()
        if (!contact.hasPrimaryEmail) {
            reasons += DeliveryRouteBlockReason.MISSING_EMAIL
        }
        if (senderEmail.isBlank() || senderEmailPassword.isBlank()) {
            reasons += DeliveryRouteBlockReason.EMAIL_SENDER_NOT_CONFIGURED
        } else if (!EmailAddressSyntaxPolicy.isUsableAddress(senderEmail)) {
            reasons += DeliveryRouteBlockReason.EMAIL_SENDER_INVALID
        }
        return reasons
    }

    private fun ContactMessageContext.toDeliveryRouteProfile(channel: MessageChannel): ContactDeliveryRouteProfile {
        return ContactDeliveryRouteProfile(
            preferredChannel = channel,
            hasPrimaryPhone = !primaryPhone.isNullOrBlank(),
            hasPrimaryEmail = EmailAddressSyntaxPolicy.isUsableAddress(primaryEmail),
        )
    }
}

sealed interface DeliveryRouteReadiness {
    data object Ready : DeliveryRouteReadiness
    data class Blocked(val reason: DeliveryRouteBlockReason) : DeliveryRouteReadiness
}

enum class DeliveryRouteBlockReason {
    CONTACT_MISSING,
    CHANNEL_DISABLED,
    MISSING_PHONE,
    MISSING_EMAIL,
    EMAIL_SENDER_NOT_CONFIGURED,
    EMAIL_SENDER_INVALID,
    UNSUPPORTED_CHANNEL,
}
