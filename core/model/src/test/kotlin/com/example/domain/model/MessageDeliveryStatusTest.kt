package com.example.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageDeliveryStatusTest {
    @Test
    fun `fromRaw normalizes stored delivery statuses`() {
        assertEquals(MessageDeliveryStatus.PENDING_DELIVERY, MessageDeliveryStatus.fromRaw(" pending_delivery "))
        assertEquals(MessageDeliveryStatus.SENT, MessageDeliveryStatus.fromRaw("sent"))
        assertEquals(MessageDeliveryStatus.DELIVERED, MessageDeliveryStatus.fromRaw("DELIVERED"))
        assertEquals(MessageDeliveryStatus.FAILED, MessageDeliveryStatus.fromRaw(" failed "))
        assertEquals(MessageDeliveryStatus.UNKNOWN, MessageDeliveryStatus.fromRaw("BOUNCED"))
    }

    @Test
    fun `successful routing statuses include pending sent and delivered`() {
        assertTrue(MessageDeliveryStatus.PENDING_DELIVERY.isSuccessfulForRouting)
        assertTrue(MessageDeliveryStatus.SENT.isSuccessfulForRouting)
        assertTrue(MessageDeliveryStatus.DELIVERED.isSuccessfulForRouting)
        assertFalse(MessageDeliveryStatus.FAILED.isSuccessfulForRouting)
        assertFalse(MessageDeliveryStatus.UNKNOWN.isSuccessfulForRouting)
    }
}
