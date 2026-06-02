package com.example.core.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sent_messages",
    indices = [
        Index(
            value = ["contactId", "sentAtMs"],
            orders = [Index.Order.ASC, Index.Order.DESC],
            name = "idx_sent_messages_contactId_sentAtMs"
        )
    ]
)
data class SentMessageEntity(
    @PrimaryKey val id: String,
    val contactId: String,
    val eventType: String,
    val eventYear: Int,
    val messageText: String,
    val channel: String,
    val sentAtMs: Long,
    val deliveryStatus: String,          // SENT, DELIVERED, FAILED
    val aiGenerated: Boolean = true,
    val geminiModel: String = "flash",
    val variantUsed: String = "standard",
    val replyReceived: Boolean = false,
    val replyAtMs: Long? = null
)
