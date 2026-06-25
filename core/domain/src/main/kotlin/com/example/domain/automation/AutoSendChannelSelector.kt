package com.example.domain.automation

import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.SentMessageEntity
import java.util.Locale

object AutoSendChannelSelector {
    private val supportedChannels = setOf("SMS", "WHATSAPP", "EMAIL")
    private val deliverySuccessStatuses = setOf("SENT", "DELIVERED", "PENDING_DELIVERY")

    sealed class ChannelSelection {
        abstract val channel: String
        abstract val hasAvailableRoute: Boolean

        data class Selected(
            override val channel: String,
            val availableChannels: Set<String>,
        ) : ChannelSelection() {
            override val hasAvailableRoute: Boolean = true
        }

        data class NoAvailableRoute(
            override val channel: String,
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
        NO_SUPPORTED_CONTACT_CHANNEL,
    }

    fun select(
        contact: ContactEntity,
        previousMessages: List<SentMessageEntity>,
        channelBlackoutJson: String,
        senderEmail: String,
        senderEmailPassword: String,
    ): String {
        return selectRoute(
            contact = contact,
            previousMessages = previousMessages,
            channelBlackoutJson = channelBlackoutJson,
            senderEmail = senderEmail,
            senderEmailPassword = senderEmailPassword,
        ).channel
    }

    fun selectRoute(
        contact: ContactEntity,
        previousMessages: List<SentMessageEntity>,
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

        val preferred = contact.preferredChannel.normalizedChannel()
        val bestHistorical = previousMessages
            .asSequence()
            .filter { it.deliveryStatus.normalizedStatus() in deliverySuccessStatuses }
            .map { it.channel.normalizedChannel() }
            .filter { it in availableChannels }
            .groupingBy { it }
            .eachCount()
            .maxWithOrNull(
                compareBy<Map.Entry<String, Int>> { it.value }
                    .thenByDescending { preferredTieBreakRank(it.key, preferred) }
            )
            ?.key

        val selectedChannel = when {
            bestHistorical != null -> bestHistorical
            preferred in availableChannels -> preferred
            else -> defaultOrder.first { it in availableChannels }
        }
        return ChannelSelection.Selected(
            channel = selectedChannel,
            availableChannels = availableChannels,
        )
    }

    private fun availableChannels(
        contact: ContactEntity,
        channelBlackoutJson: String,
        senderEmail: String,
        senderEmailPassword: String,
    ): Set<String> {
        val blocked = channelBlackoutJson.toChannelSet()
        return defaultOrder.filterNot { it in blocked }
            .filter {
                when (it) {
                    "SMS", "WHATSAPP" -> !contact.primaryPhone.isNullOrBlank()
                    "EMAIL" -> !contact.primaryEmail.isNullOrBlank() &&
                        senderEmail.isNotBlank() &&
                        senderEmailPassword.isNotBlank()
                    else -> false
                }
            }
            .toSet()
    }

    private fun noRouteReasons(
        contact: ContactEntity,
        channelBlackoutJson: String,
        senderEmail: String,
        senderEmailPassword: String,
    ): Set<NoRouteReason> {
        val blocked = channelBlackoutJson.toChannelSet()
        val reasons = mutableSetOf<NoRouteReason>()

        defaultOrder.forEach { channel ->
            if (channel in blocked) {
                reasons += NoRouteReason.CHANNEL_BLACKED_OUT
                return@forEach
            }
            when (channel) {
                "SMS", "WHATSAPP" -> {
                    if (contact.primaryPhone.isNullOrBlank()) {
                        reasons += NoRouteReason.MISSING_PHONE
                    }
                }
                "EMAIL" -> {
                    if (contact.primaryEmail.isNullOrBlank()) {
                        reasons += NoRouteReason.MISSING_EMAIL
                    }
                    if (senderEmail.isBlank() || senderEmailPassword.isBlank()) {
                        reasons += NoRouteReason.EMAIL_SENDER_NOT_CONFIGURED
                    }
                }
            }
        }

        return reasons.ifEmpty { setOf(NoRouteReason.NO_SUPPORTED_CONTACT_CHANNEL) }
    }

    private fun fallbackChannel(contact: ContactEntity): String {
        return contact.preferredChannel.normalizedChannel().takeIf { it in supportedChannels } ?: "SMS"
    }

    private fun preferredTieBreakRank(channel: String, preferred: String): Int {
        if (channel == preferred) return 100
        val index = defaultOrder.indexOf(channel).takeIf { it >= 0 } ?: defaultOrder.size
        return defaultOrder.size - index
    }

    private fun String.normalizedChannel(): String = trim().uppercase(Locale.US)

    private fun String.normalizedStatus(): String = trim().uppercase(Locale.US)

    private fun String.toChannelSet(): Set<String> {
        return CHANNEL_PATTERN.findAll(this)
            .map { it.groupValues[1].uppercase(Locale.US) }
            .filter { it in supportedChannels }
            .toSet()
    }

    private val defaultOrder = listOf("SMS", "WHATSAPP", "EMAIL")
    private val CHANNEL_PATTERN = Regex("\"([A-Za-z_]+)\"")
}
