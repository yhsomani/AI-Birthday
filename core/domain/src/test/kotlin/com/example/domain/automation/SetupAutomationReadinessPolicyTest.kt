package com.example.domain.automation

import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.common.ContactId
import com.example.domain.model.contact.ContactAutomationReadinessProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class SetupAutomationReadinessPolicyTest {

    @Test
    fun `evaluateFullAutomation requires fully auto global mode`() {
        val readiness = SetupAutomationReadinessPolicy.evaluateFullAutomation(
            globalAutomationMode = ApprovalMode.ALWAYS_ASK,
            contacts = listOf(contact(id = "ready")),
        )

        assertEquals(FullAutomationReadinessReason.MODE_DISABLED, readiness.reason)
        assertEquals(SetupReadinessStatus.ACTION_REQUIRED, readiness.status)
        assertEquals(ApprovalMode.ALWAYS_ASK, readiness.globalAutomationMode)
        assertEquals(SetupReadinessGroup.REQUIRED, readiness.group)
    }

    @Test
    fun `evaluateFullAutomation warns when contacts keep review-first overrides`() {
        val readiness = SetupAutomationReadinessPolicy.evaluateFullAutomation(
            globalAutomationMode = ApprovalMode.FULLY_AUTO,
            contacts = listOf(
                contact(id = "ask", automationMode = ApprovalMode.ALWAYS_ASK),
                contact(id = "skip", skipAutoWish = true),
                contact(id = "ready"),
            ),
        )

        assertEquals(FullAutomationReadinessReason.CONTACT_OVERRIDES, readiness.reason)
        assertEquals(SetupReadinessStatus.WARNING, readiness.status)
        assertEquals(2, readiness.reviewFirstOverrideCount)
    }

    @Test
    fun `evaluateFullAutomation passes when mode and contacts allow automation`() {
        val readiness = SetupAutomationReadinessPolicy.evaluateFullAutomation(
            globalAutomationMode = ApprovalMode.FULLY_AUTO,
            contacts = listOf(contact(id = "ready")),
        )

        assertEquals(FullAutomationReadinessReason.READY, readiness.reason)
        assertEquals(SetupReadinessStatus.OK, readiness.status)
    }

    @Test
    fun `evaluateAutomatableEvents warns when contacts are empty`() {
        val readiness = SetupAutomationReadinessPolicy.evaluateAutomatableEvents(emptyList())

        assertEquals(AutomatableEventsReadinessReason.NO_CONTACTS, readiness.reason)
        assertEquals(SetupReadinessStatus.WARNING, readiness.status)
    }

    @Test
    fun `evaluateAutomatableEvents requires action when no contact has an occasion`() {
        val readiness = SetupAutomationReadinessPolicy.evaluateAutomatableEvents(
            listOf(contact(id = "one"), contact(id = "two")),
        )

        assertEquals(AutomatableEventsReadinessReason.MISSING_EVENTS, readiness.reason)
        assertEquals(SetupReadinessStatus.ACTION_REQUIRED, readiness.status)
        assertEquals(0, readiness.eventReadyCount)
        assertEquals(2, readiness.totalContactCount)
    }

    @Test
    fun `evaluateAutomatableEvents warns when some contacts are missing occasions`() {
        val readiness = SetupAutomationReadinessPolicy.evaluateAutomatableEvents(
            listOf(
                contact(id = "ready", hasAutomatableOccasion = true),
                contact(id = "missing"),
            ),
        )

        assertEquals(AutomatableEventsReadinessReason.MISSING_EVENTS, readiness.reason)
        assertEquals(SetupReadinessStatus.WARNING, readiness.status)
        assertEquals(1, readiness.eventReadyCount)
        assertEquals(2, readiness.totalContactCount)
    }

    @Test
    fun `evaluateAutomatableEvents passes when all contacts have occasions`() {
        val readiness = SetupAutomationReadinessPolicy.evaluateAutomatableEvents(
            listOf(
                contact(id = "one", hasAutomatableOccasion = true),
                contact(id = "two", hasAutomatableOccasion = true),
            ),
        )

        assertEquals(AutomatableEventsReadinessReason.READY, readiness.reason)
        assertEquals(SetupReadinessStatus.OK, readiness.status)
        assertEquals(2, readiness.eventReadyCount)
    }

    @Test
    fun `evaluateDeliveryRoutes warns when there are no event contacts`() {
        val readiness = SetupAutomationReadinessPolicy.evaluateDeliveryRoutes(
            contacts = listOf(contact(id = "missing")),
            senderEmailReady = false,
            blockedChannels = emptySet(),
        )

        assertEquals(AutomaticDeliveryRoutesReadinessReason.NO_EVENT_CONTACTS, readiness.reason)
        assertEquals(SetupReadinessStatus.WARNING, readiness.status)
    }

    @Test
    fun `evaluateDeliveryRoutes requires action when event contacts lack routes`() {
        val readiness = SetupAutomationReadinessPolicy.evaluateDeliveryRoutes(
            contacts = listOf(
                contact(
                    id = "routable",
                    hasAutomatableOccasion = true,
                    hasPrimaryPhone = true,
                ),
                contact(
                    id = "missing",
                    hasAutomatableOccasion = true,
                ),
                contact(
                    id = "email_without_sender",
                    preferredChannel = MessageChannel.EMAIL,
                    hasAutomatableOccasion = true,
                    hasPrimaryEmail = true,
                ),
            ),
            senderEmailReady = false,
            blockedChannels = emptySet(),
        )

        assertEquals(AutomaticDeliveryRoutesReadinessReason.MISSING_ROUTES, readiness.reason)
        assertEquals(SetupReadinessStatus.ACTION_REQUIRED, readiness.status)
        assertEquals(1, readiness.routableContactCount)
        assertEquals(3, readiness.eventContactCount)
    }

    @Test
    fun `evaluateDeliveryRoutes passes when all event contacts are routable`() {
        val readiness = SetupAutomationReadinessPolicy.evaluateDeliveryRoutes(
            contacts = listOf(
                contact(
                    id = "sms",
                    hasAutomatableOccasion = true,
                    hasPrimaryPhone = true,
                ),
                contact(
                    id = "email",
                    preferredChannel = MessageChannel.EMAIL,
                    hasAutomatableOccasion = true,
                    hasPrimaryEmail = true,
                ),
            ),
            senderEmailReady = true,
            blockedChannels = emptySet(),
        )

        assertEquals(AutomaticDeliveryRoutesReadinessReason.READY, readiness.reason)
        assertEquals(SetupReadinessStatus.OK, readiness.status)
        assertEquals(2, readiness.routableContactCount)
    }

    @Test
    fun `selectedAutomaticChannelCounts honors preferred channel and fallback order`() {
        val counts = SetupAutomationReadinessPolicy.selectedAutomaticChannelCounts(
            contacts = listOf(
                contact(
                    id = "preferred_email",
                    preferredChannel = MessageChannel.EMAIL,
                    hasAutomatableOccasion = true,
                    hasPrimaryPhone = true,
                    hasPrimaryEmail = true,
                ),
                contact(
                    id = "fallback_sms",
                    preferredChannel = MessageChannel.EMAIL,
                    hasAutomatableOccasion = true,
                    hasPrimaryPhone = true,
                ),
                contact(
                    id = "blocked_sms_uses_whatsapp",
                    preferredChannel = MessageChannel.SMS,
                    hasAutomatableOccasion = true,
                    hasPrimaryPhone = true,
                ),
            ),
            senderEmailReady = true,
            blockedChannels = setOf(MessageChannel.SMS),
        )

        assertEquals(1, counts[MessageChannel.EMAIL])
        assertEquals(2, counts[MessageChannel.WHATSAPP])
        assertEquals(null, counts[MessageChannel.SMS])
    }

    private fun contact(
        id: String,
        preferredChannel: MessageChannel = MessageChannel.SMS,
        automationMode: ApprovalMode = ApprovalMode.DEFAULT,
        skipAutoWish: Boolean = false,
        hasPrimaryPhone: Boolean = false,
        hasPrimaryEmail: Boolean = false,
        hasAutomatableOccasion: Boolean = false,
    ): ContactAutomationReadinessProfile {
        return ContactAutomationReadinessProfile(
            id = ContactId(id),
            preferredChannel = preferredChannel,
            automationMode = automationMode,
            skipAutoWish = skipAutoWish,
            hasPrimaryPhone = hasPrimaryPhone,
            hasPrimaryEmail = hasPrimaryEmail,
            hasAutomatableOccasion = hasAutomatableOccasion,
            nickname = null,
            notesText = "",
            interestsJson = "[]",
            sharedHistoryJson = "[]",
            classificationConfidence = 0.0,
        )
    }
}
