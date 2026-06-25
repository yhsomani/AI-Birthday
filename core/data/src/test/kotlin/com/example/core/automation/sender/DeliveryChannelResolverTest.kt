package com.example.core.automation.sender

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveryChannelResolverTest {

    @Test
    fun `resolveRoutes keeps preferred email first when email is configured`() {
        val routes = DeliveryChannelResolver.resolveRoutes(
            preferredChannel = "EMAIL",
            primaryPhone = "+15551234567",
            primaryEmail = "alex@example.com",
            senderEmail = "me@example.com",
            senderEmailPassword = "app-password",
            blockedChannels = emptySet(),
        )

        assertEquals(listOf("EMAIL", "SMS", "WHATSAPP"), routes)
    }

    @Test
    fun `resolveRoutes falls back from unconfigured email to automatic phone channels`() {
        val routes = DeliveryChannelResolver.resolveRoutes(
            preferredChannel = "EMAIL",
            primaryPhone = "+15551234567",
            primaryEmail = "alex@example.com",
            senderEmail = "",
            senderEmailPassword = "",
            blockedChannels = emptySet(),
        )

        assertEquals(listOf("SMS", "WHATSAPP"), routes)
    }

    @Test
    fun `resolveRoutes respects disabled channels`() {
        val routes = DeliveryChannelResolver.resolveRoutes(
            preferredChannel = "WHATSAPP",
            primaryPhone = "+15551234567",
            primaryEmail = "alex@example.com",
            senderEmail = "me@example.com",
            senderEmailPassword = "app-password",
            blockedChannels = setOf("whatsapp", "sms"),
        )

        assertEquals(listOf("EMAIL"), routes)
    }

    @Test
    fun `resolveRoutes defaults unknown preference to phone-first automation`() {
        val routes = DeliveryChannelResolver.resolveRoutes(
            preferredChannel = "FAX",
            primaryPhone = "+15551234567",
            primaryEmail = "alex@example.com",
            senderEmail = "me@example.com",
            senderEmailPassword = "app-password",
            blockedChannels = emptySet(),
        )

        assertEquals(listOf("SMS", "WHATSAPP", "EMAIL"), routes)
    }

    @Test
    fun `resolveRoutes returns empty when no automatic route is available`() {
        val routes = DeliveryChannelResolver.resolveRoutes(
            preferredChannel = "SMS",
            primaryPhone = null,
            primaryEmail = "alex@example.com",
            senderEmail = "",
            senderEmailPassword = "",
            blockedChannels = emptySet(),
        )

        assertTrue(routes.isEmpty())
    }
}
