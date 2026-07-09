package com.example.core.automation.sender

import com.example.domain.automation.AutoSendChannelSelector
import com.example.domain.automation.EmailAddressSyntaxPolicy
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageChannelSetCodec
import com.example.domain.model.contact.ContactDeliveryRouteProfile
import com.example.domain.model.message.DeliveryRouteHistoryRecord

internal object DeliveryChannelResolver {
    fun parseBlockedChannels(channelBlackoutJson: String): Set<MessageChannel> {
        return MessageChannelSetCodec.parse(channelBlackoutJson)
    }

    fun resolveRoutes(
        preferredChannel: MessageChannel,
        primaryPhone: String?,
        primaryEmail: String?,
        senderEmail: String,
        senderEmailPassword: String,
        blockedChannels: Set<MessageChannel>,
        routeHistory: List<DeliveryRouteHistoryRecord> = emptyList(),
    ): List<MessageChannel> {
        return AutoSendChannelSelector.orderedRoutes(
            contact = ContactDeliveryRouteProfile(
                preferredChannel = preferredChannel,
                hasPrimaryPhone = !primaryPhone.isNullOrBlank(),
                hasPrimaryEmail = EmailAddressSyntaxPolicy.isUsableAddress(primaryEmail),
            ),
            routeHistory = routeHistory,
            channelBlackoutJson = blockedChannels.toChannelBlackoutJson(),
            senderEmail = senderEmail,
            senderEmailPassword = senderEmailPassword,
        )
    }

    private fun Set<MessageChannel>.toChannelBlackoutJson(): String {
        return MessageChannelSetCodec.toJsonArray(this)
    }
}
