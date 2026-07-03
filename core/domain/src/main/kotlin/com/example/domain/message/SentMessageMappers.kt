package com.example.domain.message

import com.example.domain.model.message.ChatHistoryMessageItem
import com.example.domain.model.message.DeliveryRouteHistoryRecord
import com.example.domain.model.message.MessageAnalyticsRecord
import com.example.domain.model.message.MessageGenerationHistory
import com.example.domain.model.message.SentMessageRecord
import com.example.domain.model.message.SentMessageListItem

fun SentMessageRecord.toMessageAnalyticsRecord(): MessageAnalyticsRecord {
    return MessageAnalyticsRecord(
        sentAtMs = sentAtMs,
        deliveryStatus = deliveryStatus,
        replyReceived = replyReceived,
    )
}

fun SentMessageRecord.toSentMessageListItem(): SentMessageListItem {
    return SentMessageListItem(
        id = id,
        contactId = contactId,
        occasionType = occasionType.ifBlank { eventType },
        messageText = messageText,
        channel = channel,
        sentAtMs = sentAtMs,
        deliveryStatus = deliveryStatus,
    )
}

fun SentMessageRecord.toChatHistoryMessageItem(): ChatHistoryMessageItem {
    return ChatHistoryMessageItem(
        id = id,
        messageText = messageText,
        channel = channel,
        sentAtMs = sentAtMs,
    )
}

fun SentMessageRecord.toDeliveryRouteHistoryRecord(): DeliveryRouteHistoryRecord {
    return DeliveryRouteHistoryRecord(
        channel = channel,
        deliveryStatus = deliveryStatus,
    )
}

fun Iterable<SentMessageRecord>.toSentMessageListItems(): List<SentMessageListItem> {
    return map { it.toSentMessageListItem() }
}

fun Iterable<SentMessageRecord>.toChatHistoryMessageItems(): List<ChatHistoryMessageItem> {
    return map { it.toChatHistoryMessageItem() }
}

fun Iterable<SentMessageRecord>.toDeliveryRouteHistoryRecords(): List<DeliveryRouteHistoryRecord> {
    return map { it.toDeliveryRouteHistoryRecord() }
}

fun Iterable<SentMessageRecord>.toMessageGenerationHistory(): MessageGenerationHistory {
    val messages = toList()
    return MessageGenerationHistory(
        previousWishes = messages.map { it.messageText },
        routeHistory = messages.toDeliveryRouteHistoryRecords(),
    )
}
