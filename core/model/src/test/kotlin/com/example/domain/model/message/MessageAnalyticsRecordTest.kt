package com.example.domain.model.message

import com.example.domain.model.MessageDeliveryStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageAnalyticsRecordTest {
    @Test
    fun `countsAsNonFailedDelivery preserves analytics reliability semantics`() {
        assertTrue(record(MessageDeliveryStatus.PENDING_DELIVERY).countsAsNonFailedDelivery)
        assertTrue(record(MessageDeliveryStatus.SENT).countsAsNonFailedDelivery)
        assertTrue(record(MessageDeliveryStatus.DELIVERED).countsAsNonFailedDelivery)
        assertTrue(record(MessageDeliveryStatus.UNKNOWN).countsAsNonFailedDelivery)
        assertFalse(record(MessageDeliveryStatus.FAILED).countsAsNonFailedDelivery)
    }

    private fun record(deliveryStatus: MessageDeliveryStatus): MessageAnalyticsRecord {
        return MessageAnalyticsRecord(
            sentAtMs = 1_700_000_000_000L,
            deliveryStatus = deliveryStatus,
            replyReceived = false,
        )
    }
}
