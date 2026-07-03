package com.example.domain.automation

import com.example.domain.model.MessageChannel
import com.example.domain.model.contact.ContactDeliveryRouteProfile
import com.example.domain.model.message.DeliveryRouteHistoryRecord

object AutoSendChannelSelector {
    sealed class ChannelSelection {
        abstract val channel: MessageChannel
        abstract val hasAvailableRoute: Boolean

        data class Selected(
            override val channel: MessageChannel,
            val availableChannels: Set<MessageChannel>,
        ) : ChannelSelection() {
            override val hasAvailableRoute: Boolean = true
        }

        data class NoAvailableRoute(
            override val channel: MessageChannel,
            val reasons: Set<NoRouteReason>,
        ) : ChannelSelection() {
            override val hasAvailableRoute: Boolean = false
        }
    }

    enum class NoRouteReason {
        CHANNEL_BLACKED_OUT,
        MISSING_PHONE,
        MISSING_EMAIL,
        EMAIL_SENDER_NOT_CONFIGURED,
        EMAIL_SENDER_INVALID,
        NO_SUPPORTED_CONTACT_CHANNEL,
    }

    fun select(
        contact: ContactDeliveryRouteProfile,
        routeHistory: List<DeliveryRouteHistoryRecord>,
        channelBlackoutJson: String,
        senderEmail: String,
        senderEmailPassword: String,
    ): MessageChannel {
        return selectRoute(
            contact = contact,
            routeHistory = routeHistory,
            channelBlackoutJson = channelBlackoutJson,
            senderEmail = senderEmail,
            senderEmailPassword = senderEmailPassword,
        ).channel
    }

    fun selectRoute(
        contact: ContactDeliveryRouteProfile,
        routeHistory: List<DeliveryRouteHistoryRecord>,
        channelBlackoutJson: String,
        senderEmail: String,
        senderEmailPassword: String,
    ): ChannelSelection {
        val availableChannels = availableChannels(
            contact = contact,
            channelBlackoutJson = channelBlackoutJson,
            senderEmail = senderEmail,
            senderEmailPassword = senderEmailPassword,
        )
        if (availableChannels.isEmpty()) {
            return ChannelSelection.NoAvailableRoute(
                channel = fallbackChannel(contact),
                reasons = noRouteReasons(
                    contact = contact,
                    channelBlackoutJson = channelBlackoutJson,
                    senderEmail = senderEmail,
                    senderEmailPassword = senderEmailPassword,
                ),
            )
        }

        val selectedChannel = orderedAvailableChannels(
            preferred = contact.preferredChannel,
            routeHistory = routeHistory,
            availableChannels = availableChannels,
        ).first()
        return ChannelSelection.Selected(
            channel = selectedChannel,
            availableChannels = availableChannels,
        )
    }

    fun orderedRoutes(
        contact: ContactDeliveryRouteProfile,
        routeHistory: List<DeliveryRouteHistoryRecord>,
        channelBlackoutJson: String,
        senderEmail: String,
        senderEmailPassword: String,
    ): List<MessageChannel> {
        val availableChannels = availableChannels(
            contact = contact,
            channelBlackoutJson = channelBlackoutJson,
            senderEmail = senderEmail,
            senderEmailPassword = senderEmailPassword,
        )
        return orderedAvailableChannels(
            preferred = contact.preferredChannel,
            routeHistory = routeHistory,
            availableChannels = availableChannels,
        )
    }

    private fun availableChannels(
        contact: ContactDeliveryRouteProfile,
        channelBlackoutJson: String,
        senderEmail: String,
        senderEmailPassword: String,
    ): Set<MessageChannel> {
        return defaultOrder.filter {
            DeliveryRouteReadinessPolicy.evaluate(
                channel = it,
                contact = contact,
                channelBlackoutJson = channelBlackoutJson,
                senderEmail = senderEmail,
                senderEmailPassword = senderEmailPassword,
            ) is DeliveryRouteReadiness.Ready
        }
            .toSet()
    }

    private fun noRouteReasons(
        contact: ContactDeliveryRouteProfile,
        channelBlackoutJson: String,
        senderEmail: String,
        senderEmailPassword: String,
    ): Set<NoRouteReason> {
        val reasons = mutableSetOf<NoRouteReason>()

        defaultOrder.forEach { channel ->
            DeliveryRouteReadinessPolicy.blockedReasons(
                channel = channel,
                contact = contact,
                channelBlackoutJson = channelBlackoutJson,
                senderEmail = senderEmail,
                senderEmailPassword = senderEmailPassword,
            ).mapTo(reasons) { it.toNoRouteReason() }
        }

        return reasons.ifEmpty { setOf(NoRouteReason.NO_SUPPORTED_CONTACT_CHANNEL) }
    }

    private fun DeliveryRouteBlockReason.toNoRouteReason(): NoRouteReason {
        return when (this) {
            DeliveryRouteBlockReason.CHANNEL_DISABLED -> NoRouteReason.CHANNEL_BLACKED_OUT
            DeliveryRouteBlockReason.MISSING_PHONE -> NoRouteReason.MISSING_PHONE
            DeliveryRouteBlockReason.MISSING_EMAIL -> NoRouteReason.MISSING_EMAIL
            DeliveryRouteBlockReason.EMAIL_SENDER_NOT_CONFIGURED -> NoRouteReason.EMAIL_SENDER_NOT_CONFIGURED
            DeliveryRouteBlockReason.EMAIL_SENDER_INVALID -> NoRouteReason.EMAIL_SENDER_INVALID
            DeliveryRouteBlockReason.CONTACT_MISSING,
            DeliveryRouteBlockReason.UNSUPPORTED_CHANNEL -> NoRouteReason.NO_SUPPORTED_CONTACT_CHANNEL
        }
    }

    private fun fallbackChannel(contact: ContactDeliveryRouteProfile): MessageChannel {
        return contact.preferredChannel
            .takeIf { it != MessageChannel.UNKNOWN }
            ?: MessageChannel.SMS
    }

    private fun preferredTieBreakRank(channel: MessageChannel, preferred: MessageChannel): Int {
        if (channel == preferred) return 100
        val index = defaultOrder.indexOf(channel).takeIf { it >= 0 } ?: defaultOrder.size
        return defaultOrder.size - index
    }

    private fun orderedAvailableChannels(
        preferred: MessageChannel,
        routeHistory: List<DeliveryRouteHistoryRecord>,
        availableChannels: Set<MessageChannel>,
    ): List<MessageChannel> {
        if (availableChannels.isEmpty()) return emptyList()
        val bestHistorical = routeHistory
            .asSequence()
            .filter { it.deliveryStatus.isSuccessfulForRouting }
            .map { it.channel }
            .filter { it in availableChannels }
            .groupingBy { it }
            .eachCount()
            .maxWithOrNull(
                compareBy<Map.Entry<MessageChannel, Int>> { it.value }
                    .thenByDescending { preferredTieBreakRank(it.key, preferred) }
            )
            ?.key

        return routeFallbackOrder(preferred)
            .let { fallbackOrder ->
                if (bestHistorical == null) {
                    fallbackOrder
                } else {
                    listOf(bestHistorical) + fallbackOrder.filterNot { it == bestHistorical }
                }
            }
            .filter { it in availableChannels }
    }

    private fun routeFallbackOrder(preferred: MessageChannel): List<MessageChannel> {
        val knownPreferred = preferred.takeIf { it != MessageChannel.UNKNOWN }
        return if (knownPreferred == null) {
            defaultOrder
        } else {
            listOf(knownPreferred) + defaultOrder.filterNot { it == knownPreferred }
        }
    }

    private val defaultOrder = listOf(MessageChannel.SMS, MessageChannel.WHATSAPP, MessageChannel.EMAIL)
}
