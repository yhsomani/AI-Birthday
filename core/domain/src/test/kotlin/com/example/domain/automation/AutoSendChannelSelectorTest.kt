package com.example.domain.automation

import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.SentMessageEntity
import com.example.domain.automation.AutoSendChannelSelector.NoRouteReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSendChannelSelectorTest {

    @Test
    fun `select keeps preferred channel when it is available and no history is stronger`() {
        val contact = contact(preferredChannel = "WHATSAPP", primaryPhone = "+15551234567")

        val result = AutoSendChannelSelector.select(
            contact = contact,
            previousMessages = emptyList(),
            channelBlackoutJson = "[]",
            senderEmail = "",
            senderEmailPassword = "",
        )

        assertEquals("WHATSAPP", result)
    }

    @Test
    fun `select falls back when preferred channel is disabled`() {
        val contact = contact(
            preferredChannel = "WHATSAPP",
            primaryPhone = "+15551234567",
            primaryEmail = "alex@example.com",
        )

        val result = AutoSendChannelSelector.select(
            contact = contact,
            previousMessages = emptyList(),
            channelBlackoutJson = "[\"WHATSAPP\"]",
            senderEmail = "me@example.com",
            senderEmailPassword = "app-password",
        )

        assertEquals("SMS", result)
    }

    @Test
    fun `select uses strongest successful historical channel when available`() {
        val contact = contact(
            preferredChannel = "SMS",
            primaryPhone = "+15551234567",
            primaryEmail = "alex@example.com",
        )
        val history = listOf(
            sent("EMAIL", "DELIVERED"),
            sent("EMAIL", "SENT"),
            sent("SMS", "FAILED"),
            sent("SMS", "SENT"),
        )

        val result = AutoSendChannelSelector.select(
            contact = contact,
            previousMessages = history,
            channelBlackoutJson = "[]",
            senderEmail = "me@example.com",
            senderEmailPassword = "app-password",
        )

        assertEquals("EMAIL", result)
    }

    @Test
    fun `select ignores historical channels that are unavailable now`() {
        val contact = contact(
            preferredChannel = "SMS",
            primaryPhone = "+15551234567",
            primaryEmail = "alex@example.com",
        )
        val history = listOf(
            sent("EMAIL", "DELIVERED"),
            sent("EMAIL", "SENT"),
        )

        val result = AutoSendChannelSelector.select(
            contact = contact,
            previousMessages = history,
            channelBlackoutJson = "[]",
            senderEmail = "",
            senderEmailPassword = "",
        )

        assertEquals("SMS", result)
    }

    @Test
    fun `select returns normalized preferred channel when no automatic channel is available`() {
        val contact = contact(preferredChannel = "email")

        val result = AutoSendChannelSelector.select(
            contact = contact,
            previousMessages = emptyList(),
            channelBlackoutJson = "[]",
            senderEmail = "",
            senderEmailPassword = "",
        )

        assertEquals("EMAIL", result)
    }

    @Test
    fun `selectRoute returns no available route with reasons when every channel is unsendable`() {
        val contact = contact(preferredChannel = "email")

        val result = AutoSendChannelSelector.selectRoute(
            contact = contact,
            previousMessages = emptyList(),
            channelBlackoutJson = "[]",
            senderEmail = "",
            senderEmailPassword = "",
        )

        assertTrue(result is AutoSendChannelSelector.ChannelSelection.NoAvailableRoute)
        val noRoute = result as AutoSendChannelSelector.ChannelSelection.NoAvailableRoute
        assertFalse(noRoute.hasAvailableRoute)
        assertEquals("EMAIL", noRoute.channel)
        assertEquals(
            setOf(
                NoRouteReason.MISSING_PHONE,
                NoRouteReason.MISSING_EMAIL,
                NoRouteReason.EMAIL_SENDER_NOT_CONFIGURED,
            ),
            noRoute.reasons,
        )
    }

    @Test
    fun `selectRoute returns no available route when all usable channels are blacked out`() {
        val contact = contact(
            preferredChannel = "SMS",
            primaryPhone = "+15551234567",
            primaryEmail = "alex@example.com",
        )

        val result = AutoSendChannelSelector.selectRoute(
            contact = contact,
            previousMessages = emptyList(),
            channelBlackoutJson = "[\"SMS\", \"WHATSAPP\", \"EMAIL\"]",
            senderEmail = "me@example.com",
            senderEmailPassword = "app-password",
        )

        assertTrue(result is AutoSendChannelSelector.ChannelSelection.NoAvailableRoute)
        val noRoute = result as AutoSendChannelSelector.ChannelSelection.NoAvailableRoute
        assertEquals("SMS", noRoute.channel)
        assertEquals(setOf(NoRouteReason.CHANNEL_BLACKED_OUT), noRoute.reasons)
    }

    private fun contact(
        preferredChannel: String,
        primaryPhone: String? = null,
        primaryEmail: String? = null,
    ): ContactEntity {
        return ContactEntity(
            id = "c1",
            name = "Alex",
            preferredChannel = preferredChannel,
            primaryPhone = primaryPhone,
            primaryEmail = primaryEmail,
        )
    }

    private fun sent(channel: String, status: String): SentMessageEntity {
        return SentMessageEntity(
            id = "$channel-$status",
            contactId = "c1",
            eventType = "event1",
            eventYear = 2026,
            messageText = "Hi",
            channel = channel,
            sentAtMs = 1000L,
            deliveryStatus = status,
        )
    }
}
