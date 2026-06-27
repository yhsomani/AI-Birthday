package com.example.core.automation.sender

import com.example.domain.model.MessageChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveryChannelResolverTest {

    @Test
    fun `parseBlockedChannels maps persisted quoted channel tokens`() {
        val channels = DeliveryChannelResolver.parseBlockedChannels("[\"WHATSAPP\", \"sms\"]")

        assertEquals(setOf(MessageChannel.WHATSAPP, MessageChannel.SMS), channels)
    }

    @Test
    fun `parseBlockedChannels ignores unknown and duplicate tokens`() {
        val channels = DeliveryChannelResolver.parseBlockedChannels(
            "[\"SMS\", \"TELEGRAM\", \"sms\", \"UNKNOWN\"]"
        )

        assertEquals(setOf(MessageChannel.SMS), channels)
    }

    @Test
    fun `parseBlockedChannels returns empty set for malformed or empty input`() {
        assertTrue(DeliveryChannelResolver.parseBlockedChannels("[]").isEmpty())
        assertTrue(DeliveryChannelResolver.parseBlockedChannels("SMS,EMAIL").isEmpty())
        assertTrue(DeliveryChannelResolver.parseBlockedChannels("").isEmpty())
    }

    @Test
    fun `resolveRoutes keeps preferred email first when email is configured`() {
        val routes = DeliveryChannelResolver.resolveRoutes(
            preferredChannel = MessageChannel.EMAIL,
            primaryPhone = "+15551234567",
            primaryEmail = "alex@example.com",
            senderEmail = "me@example.com",
            senderEmailPassword = "app-password",
            blockedChannels = emptySet(),
        )

        assertEquals(listOf(MessageChannel.EMAIL, MessageChannel.SMS, MessageChannel.WHATSAPP), routes)
    }

    @Test
    fun `resolveRoutes falls back from unconfigured email to automatic phone channels`() {
        val routes = DeliveryChannelResolver.resolveRoutes(
            preferredChannel = MessageChannel.EMAIL,
            primaryPhone = "+15551234567",
            primaryEmail = "alex@example.com",
            senderEmail = "",
            senderEmailPassword = "",
            blockedChannels = emptySet(),
        )

        assertEquals(listOf(MessageChannel.SMS, MessageChannel.WHATSAPP), routes)
    }

    @Test
    fun `resolveRoutes respects disabled channels`() {
        val routes = DeliveryChannelResolver.resolveRoutes(
            preferredChannel = MessageChannel.WHATSAPP,
            primaryPhone = "+15551234567",
            primaryEmail = "alex@example.com",
            senderEmail = "me@example.com",
            senderEmailPassword = "app-password",
            blockedChannels = setOf(MessageChannel.WHATSAPP, MessageChannel.SMS),
        )

        assertEquals(listOf(MessageChannel.EMAIL), routes)
    }

    @Test
    fun `resolveRoutes defaults unknown preference to phone-first automation`() {
        val routes = DeliveryChannelResolver.resolveRoutes(
            preferredChannel = MessageChannel.UNKNOWN,
            primaryPhone = "+15551234567",
            primaryEmail = "alex@example.com",
            senderEmail = "me@example.com",
            senderEmailPassword = "app-password",
            blockedChannels = emptySet(),
        )

        assertEquals(listOf(MessageChannel.SMS, MessageChannel.WHATSAPP, MessageChannel.EMAIL), routes)
    }

    @Test
    fun `resolveRoutes returns empty when no automatic route is available`() {
        val routes = DeliveryChannelResolver.resolveRoutes(
            preferredChannel = MessageChannel.SMS,
            primaryPhone = null,
            primaryEmail = "alex@example.com",
            senderEmail = "",
            senderEmailPassword = "",
            blockedChannels = emptySet(),
        )

        assertTrue(routes.isEmpty())
    }
}
