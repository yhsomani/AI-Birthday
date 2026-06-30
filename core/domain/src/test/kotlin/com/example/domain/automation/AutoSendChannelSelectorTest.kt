package com.example.domain.automation

import com.example.domain.automation.AutoSendChannelSelector.NoRouteReason
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.contact.ContactDeliveryRouteProfile
import com.example.domain.model.message.DeliveryRouteHistoryRecord
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
            routeHistory = emptyList(),
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
            routeHistory = emptyList(),
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
            routeHistory = history,
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
            routeHistory = history,
            channelBlackoutJson = blackoutJson(),
            senderEmail = "",
            senderEmailPassword = "",
        )

        assertEquals(MessageChannel.SMS, result)
    }

    @Test
    fun `select returns preferred channel when no automatic channel is available`() {
        val contact = contact(preferredChannel = MessageChannel.EMAIL)

        val result = AutoSendChannelSelector.select(
            contact = contact,
            routeHistory = emptyList(),
            channelBlackoutJson = blackoutJson(),
            senderEmail = "",
            senderEmailPassword = "",
        )

        assertEquals(MessageChannel.EMAIL, result)
    }

    @Test
    fun `selectRoute returns no available route with reasons when every channel is unsendable`() {
        val contact = contact(preferredChannel = MessageChannel.EMAIL)

        val result = AutoSendChannelSelector.selectRoute(
            contact = contact,
            routeHistory = emptyList(),
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
            routeHistory = emptyList(),
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

    @Test
    fun `selectRoute treats invalid configured sender email as unavailable`() {
        val contact = contact(
            preferredChannel = MessageChannel.EMAIL,
            primaryEmail = "alex@example.com",
        )

        val result = AutoSendChannelSelector.selectRoute(
            contact = contact,
            routeHistory = emptyList(),
            channelBlackoutJson = blackoutJson(),
            senderEmail = "not an email",
            senderEmailPassword = "app-password",
        )

        assertTrue(result is AutoSendChannelSelector.ChannelSelection.NoAvailableRoute)
        val noRoute = result as AutoSendChannelSelector.ChannelSelection.NoAvailableRoute
        assertEquals(MessageChannel.EMAIL, noRoute.channel)
        assertEquals(
            setOf(
                NoRouteReason.MISSING_PHONE,
                NoRouteReason.EMAIL_SENDER_INVALID,
            ),
            noRoute.reasons,
        )
    }

    private fun contact(
        preferredChannel: MessageChannel,
        primaryPhone: String? = null,
        primaryEmail: String? = null,
    ): ContactDeliveryRouteProfile {
        return ContactDeliveryRouteProfile(
            preferredChannel = preferredChannel,
            hasPrimaryPhone = !primaryPhone.isNullOrBlank(),
            hasPrimaryEmail = EmailAddressSyntaxPolicy.isUsableAddress(primaryEmail),
        )
    }

    private fun sent(channel: MessageChannel, status: MessageDeliveryStatus): DeliveryRouteHistoryRecord {
        return DeliveryRouteHistoryRecord(
            channel = channel,
            deliveryStatus = status,
        )
    }

    private fun blackoutJson(vararg channels: MessageChannel): String {
        return channels.joinToString(prefix = "[", postfix = "]") { "\"${it.raw}\"" }
    }
}
