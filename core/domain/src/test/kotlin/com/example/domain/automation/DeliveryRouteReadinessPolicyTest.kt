package com.example.domain.automation

import com.example.domain.model.MessageChannel
import com.example.domain.model.common.ContactId
import com.example.domain.model.contact.ContactDeliveryRouteProfile
import com.example.domain.model.contact.ContactMessageContext
import org.junit.Assert.assertEquals
import org.junit.Test

class DeliveryRouteReadinessPolicyTest {

    @Test
    fun `sms route is ready when phone exists and channel is enabled`() {
        val result = DeliveryRouteReadinessPolicy.evaluate(
            channel = MessageChannel.SMS,
            contact = contact(hasPrimaryPhone = true),
            channelBlackoutJson = "[]",
            senderEmail = "",
            senderEmailPassword = "",
        )

        assertEquals(DeliveryRouteReadiness.Ready, result)
    }

    @Test
    fun `channel blackout blocks route before contact prerequisites`() {
        val result = DeliveryRouteReadinessPolicy.evaluate(
            channel = MessageChannel.WHATSAPP,
            contact = contact(hasPrimaryPhone = false),
            channelBlackoutJson = "[\"WHATSAPP\"]",
            senderEmail = "",
            senderEmailPassword = "",
        )

        assertEquals(
            DeliveryRouteReadiness.Blocked(DeliveryRouteBlockReason.CHANNEL_DISABLED),
            result,
        )
    }

    @Test
    fun `phone routes require a primary phone`() {
        val result = DeliveryRouteReadinessPolicy.evaluate(
            channel = MessageChannel.WHATSAPP,
            contact = contact(hasPrimaryPhone = false),
            channelBlackoutJson = "[]",
            senderEmail = "",
            senderEmailPassword = "",
        )

        assertEquals(
            DeliveryRouteReadiness.Blocked(DeliveryRouteBlockReason.MISSING_PHONE),
            result,
        )
    }

    @Test
    fun `email route requires usable contact email and configured sender`() {
        val missingContactEmail = DeliveryRouteReadinessPolicy.evaluate(
            channel = MessageChannel.EMAIL,
            contact = contact(hasPrimaryEmail = false),
            channelBlackoutJson = "[]",
            senderEmail = "sender@example.com",
            senderEmailPassword = "app-password",
        )
        val missingSender = DeliveryRouteReadinessPolicy.evaluate(
            channel = MessageChannel.EMAIL,
            contact = contact(hasPrimaryEmail = true),
            channelBlackoutJson = "[]",
            senderEmail = "",
            senderEmailPassword = "",
        )
        val invalidSender = DeliveryRouteReadinessPolicy.evaluate(
            channel = MessageChannel.EMAIL,
            contact = contact(hasPrimaryEmail = true),
            channelBlackoutJson = "[]",
            senderEmail = "not-an-email",
            senderEmailPassword = "app-password",
        )
        val ready = DeliveryRouteReadinessPolicy.evaluate(
            channel = MessageChannel.EMAIL,
            contact = contact(hasPrimaryEmail = true),
            channelBlackoutJson = "[]",
            senderEmail = "sender@example.com",
            senderEmailPassword = "app-password",
        )

        assertEquals(
            DeliveryRouteReadiness.Blocked(DeliveryRouteBlockReason.MISSING_EMAIL),
            missingContactEmail,
        )
        assertEquals(
            DeliveryRouteReadiness.Blocked(DeliveryRouteBlockReason.EMAIL_SENDER_NOT_CONFIGURED),
            missingSender,
        )
        assertEquals(
            DeliveryRouteReadiness.Blocked(DeliveryRouteBlockReason.EMAIL_SENDER_INVALID),
            invalidSender,
        )
        assertEquals(DeliveryRouteReadiness.Ready, ready)
    }

    @Test
    fun `email route exposes every blocking reason for diagnostics`() {
        val result = DeliveryRouteReadinessPolicy.blockedReasons(
            channel = MessageChannel.EMAIL,
            contact = contact(hasPrimaryEmail = false),
            channelBlackoutJson = "[]",
            senderEmail = "",
            senderEmailPassword = "",
        )

        assertEquals(
            setOf(
                DeliveryRouteBlockReason.MISSING_EMAIL,
                DeliveryRouteBlockReason.EMAIL_SENDER_NOT_CONFIGURED,
            ),
            result,
        )
    }

    @Test
    fun `message context overload treats invalid contact email as missing email`() {
        val result = DeliveryRouteReadinessPolicy.evaluate(
            channel = MessageChannel.EMAIL,
            contact = ContactMessageContext(
                id = ContactId("c1"),
                displayName = "Asha",
                avatarUrl = null,
                primaryPhone = null,
                primaryEmail = "asha at example",
            ),
            channelBlackoutJson = "[]",
            senderEmail = "sender@example.com",
            senderEmailPassword = "app-password",
        )

        assertEquals(
            DeliveryRouteReadiness.Blocked(DeliveryRouteBlockReason.MISSING_EMAIL),
            result,
        )
    }

    @Test
    fun `missing contact and unsupported channel are explicit blocks`() {
        val missingContact = DeliveryRouteReadinessPolicy.evaluate(
            channel = MessageChannel.SMS,
            contact = null as ContactDeliveryRouteProfile?,
            channelBlackoutJson = "[]",
            senderEmail = "",
            senderEmailPassword = "",
        )
        val unsupported = DeliveryRouteReadinessPolicy.evaluate(
            channel = MessageChannel.UNKNOWN,
            contact = contact(hasPrimaryPhone = true),
            channelBlackoutJson = "[]",
            senderEmail = "",
            senderEmailPassword = "",
        )

        assertEquals(
            DeliveryRouteReadiness.Blocked(DeliveryRouteBlockReason.CONTACT_MISSING),
            missingContact,
        )
        assertEquals(
            DeliveryRouteReadiness.Blocked(DeliveryRouteBlockReason.UNSUPPORTED_CHANNEL),
            unsupported,
        )
    }

    private fun contact(
        preferredChannel: MessageChannel = MessageChannel.SMS,
        hasPrimaryPhone: Boolean = false,
        hasPrimaryEmail: Boolean = false,
    ): ContactDeliveryRouteProfile {
        return ContactDeliveryRouteProfile(
            preferredChannel = preferredChannel,
            hasPrimaryPhone = hasPrimaryPhone,
            hasPrimaryEmail = hasPrimaryEmail,
        )
    }
}
