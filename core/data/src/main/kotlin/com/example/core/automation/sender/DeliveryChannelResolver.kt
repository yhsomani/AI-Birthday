package com.example.core.automation.sender

import com.example.domain.automation.EmailAddressSyntaxPolicy
import com.example.domain.model.MessageChannel

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
    ): List<MessageChannel> {
        return candidateOrder(preferredChannel)
            .filterNot { it in blockedChannels }
            .filter {
                when (it) {
                    MessageChannel.SMS,
                    MessageChannel.WHATSAPP -> !primaryPhone.isNullOrBlank()
                    MessageChannel.EMAIL -> EmailAddressSyntaxPolicy.isUsableAddress(primaryEmail) &&
                        EmailAddressSyntaxPolicy.isConfiguredSender(senderEmail, senderEmailPassword)
                    MessageChannel.UNKNOWN -> false
                }
            }
    }

    private fun candidateOrder(preferredChannel: MessageChannel): List<MessageChannel> {
        return when (preferredChannel) {
            MessageChannel.WHATSAPP -> listOf(MessageChannel.WHATSAPP, MessageChannel.SMS, MessageChannel.EMAIL)
            MessageChannel.EMAIL -> listOf(MessageChannel.EMAIL, MessageChannel.SMS, MessageChannel.WHATSAPP)
            MessageChannel.SMS,
            MessageChannel.UNKNOWN -> listOf(MessageChannel.SMS, MessageChannel.WHATSAPP, MessageChannel.EMAIL)
        }
    }
}
