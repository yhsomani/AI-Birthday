package com.example.domain.automation

import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.SentMessageEntity
import com.example.domain.automation.AutoSendChannelSelector.NoRouteReason
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageDeliveryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSendChannelSelectorTest {

    @Test
    fun `select keeps preferred channel when it is available and no history is stronger`() {
        val contact = contact(preferredChannel = MessageChannel.WHATSAPP, primaryPhone = "+15551234567")

        val result = AutoSendChannelSelector.select(
            contact = contact,
            previousMessages = emptyList(),
            channelBlackoutJson = blackoutJson(),
            senderEmail = "",
            senderEmailPassword = "",
        )

        assertEquals(MessageChannel.WHATSAPP, result)
    }

    @Test
    fun `select falls back when preferred channel is disabled`() {
        val contact = contact(
            preferredChannel = MessageChannel.WHATSAPP,
            primaryPhone = "+15551234567",
            primaryEmail = "alex@example.com",
        )

        val result = AutoSendChannelSelector.select(
            contact = contact,
            previousMessages = emptyList(),
            channelBlackoutJson = blackoutJson(MessageChannel.WHATSAPP),
            senderEmail = "me@example.com",
            senderEmailPassword = "app-password",
        )

        assertEquals(MessageChannel.SMS, result)
    }

    @Test
    fun `select uses strongest successful historical channel when available`() {
        val contact = contact(
            preferredChannel = MessageChannel.SMS,
            primaryPhone = "+15551234567",
            primaryEmail = "alex@example.com",
        )
        val history = listOf(
            sent(MessageChannel.EMAIL, MessageDeliveryStatus.DELIVERED),
            sent(MessageChannel.EMAIL, MessageDeliveryStatus.SENT),
            sent(MessageChannel.SMS, MessageDeliveryStatus.FAILED),
            sent(MessageChannel.SMS, MessageDeliveryStatus.SENT),
        )

        val result = AutoSendChannelSelector.select(
            contact = contact,
            previousMessages = history,
            channelBlackoutJson = blackoutJson(),
            senderEmail = "me@example.com",
            senderEmailPassword = "app-password",
        )

        assertEquals(MessageChannel.EMAIL, result)
    }

    @Test
    fun `select ignores historical channels that are unavailable now`() {
        val contact = contact(
            preferredChannel = MessageChannel.SMS,
            primaryPhone = "+15551234567",
            primaryEmail = "alex@example.com",
        )
        val history = listOf(
            sent(MessageChannel.EMAIL, MessageDeliveryStatus.DELIVERED),
            sent(MessageChannel.EMAIL, MessageDeliveryStatus.SENT),
        )

        val result = AutoSendChannelSelector.select(
            contact = contact,
            previousMessages = history,
            channelBlackoutJson = blackoutJson(),
            senderEmail = "",
            senderEmailPassword = "",
        )

        assertEquals(MessageChannel.SMS, result)
    }

    @Test
    fun `select returns normalized preferred channel when no automatic channel is available`() {
        val contact = legacyContact(preferredChannel = MessageChannel.EMAIL.raw.lowercase())

        val result = AutoSendChannelSelector.select(
            contact = contact,
            previousMessages = emptyList(),
            channelBlackoutJson = blackoutJson(),
            senderEmail = "",
            senderEmailPassword = "",
        )

        assertEquals(MessageChannel.EMAIL, result)
    }

    @Test
    fun `selectRoute returns no available route with reasons when every channel is unsendable`() {
        val contact = legacyContact(preferredChannel = MessageChannel.EMAIL.raw.lowercase())

        val result = AutoSendChannelSelector.selectRoute(
            contact = contact,
            previousMessages = emptyList(),
            channelBlackoutJson = blackoutJson(),
            senderEmail = "",
            senderEmailPassword = "",
        )

        assertTrue(result is AutoSendChannelSelector.ChannelSelection.NoAvailableRoute)
        val noRoute = result as AutoSendChannelSelector.ChannelSelection.NoAvailableRoute
        assertFalse(noRoute.hasAvailableRoute)
        assertEquals(MessageChannel.EMAIL, noRoute.channel)
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
            preferredChannel = MessageChannel.SMS,
            primaryPhone = "+15551234567",
            primaryEmail = "alex@example.com",
        )

        val result = AutoSendChannelSelector.selectRoute(
            contact = contact,
            previousMessages = emptyList(),
            channelBlackoutJson = blackoutJson(
                MessageChannel.SMS,
                MessageChannel.WHATSAPP,
                MessageChannel.EMAIL,
            ),
            senderEmail = "me@example.com",
            senderEmailPassword = "app-password",
        )

        assertTrue(result is AutoSendChannelSelector.ChannelSelection.NoAvailableRoute)
        val noRoute = result as AutoSendChannelSelector.ChannelSelection.NoAvailableRoute
        assertEquals(MessageChannel.SMS, noRoute.channel)
        assertEquals(setOf(NoRouteReason.CHANNEL_BLACKED_OUT), noRoute.reasons)
    }

    private fun contact(
        preferredChannel: MessageChannel,
        primaryPhone: String? = null,
        primaryEmail: String? = null,
    ): ContactEntity {
        return legacyContact(
            preferredChannel = preferredChannel.raw,
            primaryPhone = primaryPhone,
            primaryEmail = primaryEmail,
        )
    }

    private fun legacyContact(
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

    private fun sent(channel: MessageChannel, status: MessageDeliveryStatus): SentMessageEntity {
        return SentMessageEntity(
            id = "${channel.raw}-${status.raw}",
            contactId = "c1",
            eventType = "event1",
            eventYear = 2026,
            messageText = "Hi",
            channel = channel.raw,
            sentAtMs = 1000L,
            deliveryStatus = status.raw,
        )
    }

    private fun blackoutJson(vararg channels: MessageChannel): String {
        return channels.joinToString(prefix = "[", postfix = "]") { "\"${it.raw}\"" }
    }
}
