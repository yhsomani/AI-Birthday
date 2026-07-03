package com.example.core.automation.sender

import com.example.domain.automation.AutoSendChannelSelector
import com.example.domain.automation.EmailAddressSyntaxPolicy
import com.example.domain.model.MessageChannel
import com.example.domain.model.contact.ContactDeliveryRouteProfile
import com.example.domain.model.message.DeliveryRouteHistoryRecord

internal object DeliveryChannelResolver {
    private val channelTokenPattern = Regex("\"([A-Za-z_]+)\"")

    fun parseBlockedChannels(channelBlackoutJson: String): Set<MessageChannel> {
        return channelTokenPattern.findAll(channelBlackoutJson)
            .map { MessageChannel.fromRaw(it.groupValues[1]) }
            .filter { it != MessageChannel.UNKNOWN }
            .toSet()
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
        return joinToString(prefix = "[", postfix = "]") { "\"${it.raw}\"" }
    }
}
