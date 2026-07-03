package com.example.data.repository

import com.example.core.db.entities.SentMessageEntity
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.common.SentMessageId
import com.example.domain.model.message.SentMessageRecord

internal fun SentMessageEntity.toSentMessageRecord(): SentMessageRecord {
    return SentMessageRecord(
        id = SentMessageId(id),
        contactId = contactId?.let(::ContactId),
        eventType = eventType,
        occasionId = eventId?.let(::OccasionId),
        occasionType = occasionType,
        occasionLabel = occasionLabel,
        eventYear = eventYear,
        messageText = messageText,
        channel = MessageChannel.fromRaw(channel),
        sentAtMs = sentAtMs,
        deliveryStatus = MessageDeliveryStatus.fromRaw(deliveryStatus),
        aiGenerated = aiGenerated,
        geminiModel = geminiModel,
        variantUsed = variantUsed,
        replyReceived = replyReceived,
        replyAtMs = replyAtMs,
        isContactDeleted = isContactDeleted,
    )
}

internal fun Iterable<SentMessageEntity>.toSentMessageRecords(): List<SentMessageRecord> {
    return map { it.toSentMessageRecord() }
}

internal fun SentMessageRecord.toSentMessageEntity(): SentMessageEntity {
    return SentMessageEntity(
        id = id.value,
        contactId = contactId?.value,
        eventType = eventType,
        eventId = occasionId?.value,
        occasionType = occasionType,
        occasionLabel = occasionLabel,
        eventYear = eventYear,
        messageText = messageText,
        channel = channel.raw,
        sentAtMs = sentAtMs,
        deliveryStatus = deliveryStatus.raw,
        aiGenerated = aiGenerated,
        geminiModel = geminiModel,
        variantUsed = variantUsed,
        replyReceived = replyReceived,
        replyAtMs = replyAtMs,
        isContactDeleted = isContactDeleted,
    )
}
