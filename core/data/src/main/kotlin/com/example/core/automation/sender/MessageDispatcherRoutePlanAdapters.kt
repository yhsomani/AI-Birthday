package com.example.core.automation.sender

import com.example.domain.model.MessageChannel

internal data class MessageDispatchRoutePlan(
    val initialFinalChannel: MessageChannel,
    val blockedChannels: Set<MessageChannel>,
    val deliveryRoutes: List<MessageChannel>,
) {
    val noDeliveryRoute: Boolean
        get() = deliveryRoutes.isEmpty()
}

internal fun messageDispatchRoutePlan(
    preferredChannel: MessageChannel,
    primaryPhone: String?,
    primaryEmail: String?,
    senderEmail: String,
    senderEmailPassword: String,
    channelBlackoutJson: String,
): MessageDispatchRoutePlan {
    val blockedChannels = DeliveryChannelResolver.parseBlockedChannels(channelBlackoutJson)
    return MessageDispatchRoutePlan(
        initialFinalChannel = preferredChannel
            .takeIf { it != MessageChannel.UNKNOWN }
            ?: MessageChannel.SMS,
        blockedChannels = blockedChannels,
        deliveryRoutes = DeliveryChannelResolver.resolveRoutes(
            preferredChannel = preferredChannel,
            primaryPhone = primaryPhone,
            primaryEmail = primaryEmail,
            senderEmail = senderEmail,
            senderEmailPassword = senderEmailPassword,
            blockedChannels = blockedChannels,
        ),
    )
}
