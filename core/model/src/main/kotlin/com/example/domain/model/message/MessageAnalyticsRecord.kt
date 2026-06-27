package com.example.domain.model.message

import com.example.domain.model.MessageDeliveryStatus

data class MessageAnalyticsRecord(
    val sentAtMs: Long,
    val deliveryStatus: MessageDeliveryStatus,
    val replyReceived: Boolean,
) {
    val countsAsNonFailedDelivery: Boolean
        get() = deliveryStatus != MessageDeliveryStatus.FAILED
}
