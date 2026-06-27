package com.example.core.automation.sender

import com.example.domain.model.MessageChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageDispatcherRoutePlanAdaptersTest {

    @Test
    fun messageDispatchRoutePlan_resolvesRoutesFromPrefsAndContactReachability() {
        val plan = messageDispatchRoutePlan(
            preferredChannel = MessageChannel.UNKNOWN,
            primaryPhone = "+15551234567",
            primaryEmail = "alex@example.com",
            senderEmail = "me@example.com",
            senderEmailPassword = "app-password",
            channelBlackoutJson = "[\"WHATSAPP\"]",
        )

        assertEquals(MessageChannel.SMS, plan.initialFinalChannel)
        assertEquals(setOf(MessageChannel.WHATSAPP), plan.blockedChannels)
        assertEquals(listOf(MessageChannel.SMS, MessageChannel.EMAIL), plan.deliveryRoutes)
        assertFalse(plan.noDeliveryRoute)
    }

    @Test
    fun messageDispatchRoutePlan_preservesKnownPreferredChannelWhenNoRouteIsAvailable() {
        val plan = messageDispatchRoutePlan(
            preferredChannel = MessageChannel.EMAIL,
            primaryPhone = null,
            primaryEmail = "alex@example.com",
            senderEmail = "",
            senderEmailPassword = "",
            channelBlackoutJson = "[]",
        )

        assertEquals(MessageChannel.EMAIL, plan.initialFinalChannel)
        assertTrue(plan.blockedChannels.isEmpty())
        assertTrue(plan.deliveryRoutes.isEmpty())
        assertTrue(plan.noDeliveryRoute)
    }
}
