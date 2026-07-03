package com.example.domain.automation

import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.contact.ContactAutomationReadinessProfile

enum class FullAutomationReadinessReason {
    MODE_DISABLED,
    CONTACT_OVERRIDES,
    READY,
}

enum class AutomatableEventsReadinessReason {
    NO_CONTACTS,
    READY,
    MISSING_EVENTS,
}

enum class AutomaticDeliveryRoutesReadinessReason {
    NO_EVENT_CONTACTS,
    READY,
    MISSING_ROUTES,
}

data class FullAutomationReadiness(
    val reason: FullAutomationReadinessReason,
    val status: SetupReadinessStatus,
    val group: SetupReadinessGroup = SetupReadinessGroup.REQUIRED,
    val globalAutomationMode: ApprovalMode,
    val reviewFirstOverrideCount: Int = 0,
)

data class AutomatableEventsReadiness(
    val reason: AutomatableEventsReadinessReason,
    val status: SetupReadinessStatus,
    val group: SetupReadinessGroup = SetupReadinessGroup.REQUIRED,
    val eventReadyCount: Int = 0,
    val totalContactCount: Int = 0,
)

data class AutomaticDeliveryRoutesReadiness(
    val reason: AutomaticDeliveryRoutesReadinessReason,
    val status: SetupReadinessStatus,
    val group: SetupReadinessGroup = SetupReadinessGroup.REQUIRED,
    val routableContactCount: Int = 0,
    val eventContactCount: Int = 0,
)

object SetupAutomationReadinessPolicy {
    private val routeOrder = listOf(
        MessageChannel.SMS,
        MessageChannel.WHATSAPP,
        MessageChannel.EMAIL,
    )

    fun evaluateFullAutomation(
        globalAutomationMode: ApprovalMode,
        contacts: List<ContactAutomationReadinessProfile>,
    ): FullAutomationReadiness {
        val reviewFirstOverrideCount = contacts.count { it.hasReviewFirstAutomationOverride }
        return when {
            globalAutomationMode != ApprovalMode.FULLY_AUTO -> FullAutomationReadiness(
                reason = FullAutomationReadinessReason.MODE_DISABLED,
                status = SetupReadinessStatus.ACTION_REQUIRED,
                globalAutomationMode = globalAutomationMode,
                reviewFirstOverrideCount = reviewFirstOverrideCount,
            )
            reviewFirstOverrideCount > 0 -> FullAutomationReadiness(
                reason = FullAutomationReadinessReason.CONTACT_OVERRIDES,
                status = SetupReadinessStatus.WARNING,
                globalAutomationMode = globalAutomationMode,
                reviewFirstOverrideCount = reviewFirstOverrideCount,
            )
            else -> FullAutomationReadiness(
                reason = FullAutomationReadinessReason.READY,
                status = SetupReadinessStatus.OK,
                globalAutomationMode = globalAutomationMode,
            )
        }
    }

    fun evaluateAutomatableEvents(
        contacts: List<ContactAutomationReadinessProfile>,
    ): AutomatableEventsReadiness {
        if (contacts.isEmpty()) {
            return AutomatableEventsReadiness(
                reason = AutomatableEventsReadinessReason.NO_CONTACTS,
                status = SetupReadinessStatus.WARNING,
            )
        }

        val eventReadyCount = contacts.count { it.hasAutomatableOccasion }
        return AutomatableEventsReadiness(
            reason = when {
                eventReadyCount == contacts.size -> AutomatableEventsReadinessReason.READY
                else -> AutomatableEventsReadinessReason.MISSING_EVENTS
            },
            status = when {
                eventReadyCount == contacts.size -> SetupReadinessStatus.OK
                eventReadyCount == 0 -> SetupReadinessStatus.ACTION_REQUIRED
                else -> SetupReadinessStatus.WARNING
            },
            eventReadyCount = eventReadyCount,
            totalContactCount = contacts.size,
        )
    }

    fun evaluateDeliveryRoutes(
        contacts: List<ContactAutomationReadinessProfile>,
        senderEmailReady: Boolean,
        blockedChannels: Set<MessageChannel>,
    ): AutomaticDeliveryRoutesReadiness {
        val eventContacts = contacts.filter { it.hasAutomatableOccasion }
        if (eventContacts.isEmpty()) {
            return AutomaticDeliveryRoutesReadiness(
                reason = AutomaticDeliveryRoutesReadinessReason.NO_EVENT_CONTACTS,
                status = SetupReadinessStatus.WARNING,
            )
        }

        val routableCount = eventContacts.count {
            it.hasAutomaticDeliveryRoute(
                senderEmailReady = senderEmailReady,
                blockedChannels = blockedChannels,
            )
        }
        return AutomaticDeliveryRoutesReadiness(
            reason = if (routableCount == eventContacts.size) {
                AutomaticDeliveryRoutesReadinessReason.READY
            } else {
                AutomaticDeliveryRoutesReadinessReason.MISSING_ROUTES
            },
            status = if (routableCount == eventContacts.size) {
                SetupReadinessStatus.OK
            } else {
                SetupReadinessStatus.ACTION_REQUIRED
            },
            routableContactCount = routableCount,
            eventContactCount = eventContacts.size,
        )
    }

    fun selectedAutomaticChannelCounts(
        contacts: List<ContactAutomationReadinessProfile>,
        senderEmailReady: Boolean,
        blockedChannels: Set<MessageChannel>,
    ): Map<MessageChannel, Int> {
        return contacts.asSequence()
            .filter { it.hasAutomatableOccasion }
            .mapNotNull {
                it.selectedAutomaticChannel(
                    senderEmailReady = senderEmailReady,
                    blockedChannels = blockedChannels,
                )
            }
            .groupingBy { it }
            .eachCount()
    }

    private fun ContactAutomationReadinessProfile.selectedAutomaticChannel(
        senderEmailReady: Boolean,
        blockedChannels: Set<MessageChannel>,
    ): MessageChannel? {
        val availableChannels = routeOrder
            .filterNot { it in blockedChannels }
            .filter { channel ->
                hasChannelPrerequisite(
                    channel = channel,
                    senderEmailReady = senderEmailReady,
                )
            }
        if (availableChannels.isEmpty()) return null
        return preferredChannel.takeIf { it in availableChannels } ?: availableChannels.first()
    }

    private fun ContactAutomationReadinessProfile.hasAutomaticDeliveryRoute(
        senderEmailReady: Boolean,
        blockedChannels: Set<MessageChannel>,
    ): Boolean {
        return routeOrder.any { channel ->
            channel !in blockedChannels &&
                hasChannelPrerequisite(
                    channel = channel,
                    senderEmailReady = senderEmailReady,
                )
        }
    }

    private fun ContactAutomationReadinessProfile.hasChannelPrerequisite(
        channel: MessageChannel,
        senderEmailReady: Boolean,
    ): Boolean {
        return when (channel) {
            MessageChannel.SMS,
            MessageChannel.WHATSAPP -> hasPrimaryPhone
            MessageChannel.EMAIL -> hasPrimaryEmail && senderEmailReady
            MessageChannel.UNKNOWN -> false
        }
    }
}
