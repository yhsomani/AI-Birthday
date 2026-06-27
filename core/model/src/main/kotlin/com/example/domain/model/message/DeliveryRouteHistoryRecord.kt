package com.example.domain.model.message

import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageDeliveryStatus

data class DeliveryRouteHistoryRecord(
    val channel: MessageChannel,
    val deliveryStatus: MessageDeliveryStatus,
)
