package com.example.core.automation.sender

internal object DeliveryChannelResolver {
    private val supportedChannels = setOf("SMS", "WHATSAPP", "EMAIL")

    fun resolveRoutes(
        preferredChannel: String,
        primaryPhone: String?,
        primaryEmail: String?,
        senderEmail: String,
        senderEmailPassword: String,
        blockedChannels: Set<String>,
    ): List<String> {
        val normalizedBlockedChannels = blockedChannels.map { it.trim().uppercase() }.toSet()
        return candidateOrder(preferredChannel)
            .filterNot { it in normalizedBlockedChannels }
            .filter {
                when (it) {
                    "SMS", "WHATSAPP" -> !primaryPhone.isNullOrBlank()
                    "EMAIL" -> !primaryEmail.isNullOrBlank() &&
                        senderEmail.isNotBlank() &&
                        senderEmailPassword.isNotBlank()
                    else -> false
                }
            }
    }

    private fun candidateOrder(preferredChannel: String): List<String> {
        return when (preferredChannel.trim().uppercase()) {
            "WHATSAPP" -> listOf("WHATSAPP", "SMS", "EMAIL")
            "EMAIL" -> listOf("EMAIL", "SMS", "WHATSAPP")
            "SMS" -> listOf("SMS", "WHATSAPP", "EMAIL")
            else -> listOf("SMS", "WHATSAPP", "EMAIL")
        }.filter { it in supportedChannels }
    }
}
