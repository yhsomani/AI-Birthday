package com.example.core.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pending_messages",
    indices = [Index(value = ["scheduledForMs"], name = "idx_pending_messages_scheduledForMs")]
)
data class PendingMessageEntity(
    @PrimaryKey val id: String,
    val contactId: String,
    val eventId: String,
    val shortVariant: String,
    val standardVariant: String,
    val longVariant: String,
    val formalVariant: String,           // NEW v2
    val funnyVariant: String,            // NEW v2
    val emotionalVariant: String,        // NEW v2
    val selectedVariant: String = "standard",
    val selectedVariantText: String = "",
    val channel: String,
    val scheduledForMs: Long,
    val approvalMode: String,
    val status: String = "PENDING",
    val aiModel: String = "flash",
    val generatedAtMs: Long = System.currentTimeMillis(),
    val editedByUser: Boolean = false,
    val userEditedText: String? = null,
    val qualityScore: Int = 0,           // 0-100
    val tone: String = "WARM",           // WARM, FUNNY, NOSTALGIC, MOTIVATIONAL, PROFESSIONAL
    val length: String = "STANDARD",     // ULTRA_SHORT, STANDARD, LONG
    val includeEmoji: Boolean = true
)
