package com.example.domain.automation

import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.SentMessageEntity
import java.util.Locale

object AutoSendChannelSelector {
    private val supportedChannels = setOf("SMS", "WHATSAPP", "EMAIL")
    private val deliverySuccessStatuses = setOf("SENT", "DELIVERED", "PENDING_DELIVERY")

    fun select(
        contact: ContactEntity,
        previousMessages: List<SentMessageEntity>,
        channelBlackoutJson: String,
        senderEmail: String,
        senderEmailPassword: String,
    ): String {
        val availableChannels = availableChannels(
            contact = contact,
            channelBlackoutJson = channelBlackoutJson,
            senderEmail = senderEmail,
            senderEmailPassword = senderEmailPassword,
        )
        if (availableChannels.isEmpty()) {
            return contact.preferredChannel.normalizedChannel().takeIf { it in supportedChannels } ?: "SMS"
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

        return when {
            bestHistorical != null -> bestHistorical
            preferred in availableChannels -> preferred
            else -> defaultOrder.first { it in availableChannels }
        }
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
