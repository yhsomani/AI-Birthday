package com.example.domain.automation

import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.SentMessageEntity
import org.junit.Assert.assertEquals
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
