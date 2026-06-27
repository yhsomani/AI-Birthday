package com.example.domain.message

import com.example.core.db.entities.SentMessageEntity
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.SentMessageId
import com.example.domain.model.message.DeliveryRouteHistoryRecord
import com.example.domain.model.message.MessageAnalyticsRecord
import com.example.domain.model.message.MessageGenerationHistory
import com.example.domain.model.message.SentMessageListItem

fun SentMessageEntity.toMessageAnalyticsRecord(): MessageAnalyticsRecord {
    return MessageAnalyticsRecord(
        sentAtMs = sentAtMs,
        deliveryStatus = MessageDeliveryStatus.fromRaw(deliveryStatus),
        replyReceived = replyReceived,
    )
}

fun SentMessageEntity.toSentMessageListItem(): SentMessageListItem {
    return SentMessageListItem(
        id = SentMessageId(id),
        contactId = contactId?.let(::ContactId),
        occasionType = occasionType.ifBlank { eventType },
        messageText = messageText,
        channel = MessageChannel.fromRaw(channel),
        sentAtMs = sentAtMs,
        deliveryStatus = MessageDeliveryStatus.fromRaw(deliveryStatus),
    )
}

fun SentMessageEntity.toDeliveryRouteHistoryRecord(): DeliveryRouteHistoryRecord {
    return DeliveryRouteHistoryRecord(
        channel = MessageChannel.fromRaw(channel),
        deliveryStatus = MessageDeliveryStatus.fromRaw(deliveryStatus),
    )
}

fun Iterable<SentMessageEntity>.toSentMessageListItems(): List<SentMessageListItem> {
    return map { it.toSentMessageListItem() }
}

fun Iterable<SentMessageEntity>.toDeliveryRouteHistoryRecords(): List<DeliveryRouteHistoryRecord> {
    return map { it.toDeliveryRouteHistoryRecord() }
}

fun Iterable<SentMessageEntity>.toMessageGenerationHistory(): MessageGenerationHistory {
    val messages = toList()
    return MessageGenerationHistory(
        previousWishes = messages.map { it.messageText },
        routeHistory = messages.toDeliveryRouteHistoryRecords(),
    )
}
