package com.example.domain.model.message

import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.common.SentMessageId
import com.example.domain.model.occasion.OccasionType

data class SentMessageDispatchRecord(
    val id: SentMessageId,
    val contactId: ContactId,
    val occasionId: OccasionId?,
    val occasionType: OccasionType,
    val occasionLabel: String?,
    val eventYear: Int,
    val messageText: String,
    val channel: MessageChannel,
    val sentAtMs: Long,
    val deliveryStatus: MessageDeliveryStatus,
    val aiGenerated: Boolean = true,
)

data class SentMessageDeliveryStatusUpdate(
    val id: SentMessageId,
    val deliveryStatus: MessageDeliveryStatus,
)
