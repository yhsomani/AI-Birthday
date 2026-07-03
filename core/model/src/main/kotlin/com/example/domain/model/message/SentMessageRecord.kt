package com.example.domain.model.message

import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.common.SentMessageId

data class SentMessageRecord(
    val id: SentMessageId,
    val contactId: ContactId?,
    val eventType: String,
    val occasionId: OccasionId? = null,
    val occasionType: String = eventType,
    val occasionLabel: String? = null,
    val eventYear: Int,
    val messageText: String,
    val channel: MessageChannel,
    val sentAtMs: Long,
    val deliveryStatus: MessageDeliveryStatus,
    val aiGenerated: Boolean = true,
    val geminiModel: String = "flash",
    val variantUsed: String = "standard",
    val replyReceived: Boolean = false,
    val replyAtMs: Long? = null,
    val isContactDeleted: Boolean = false,
)
