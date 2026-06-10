package com.example.core.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "message_feedback",
    foreignKeys = [
        ForeignKey(
            entity = PendingMessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pendingMessageId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ContactEntity::class,
            parentColumns = ["id"],
            childColumns = ["contactId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["pendingMessageId"], name = "idx_message_feedback_pendingMessageId"),
        Index(value = ["contactId"], name = "idx_message_feedback_contactId"),
        Index(value = ["createdAtMs"], name = "idx_message_feedback_createdAtMs"),
    ],
)
data class MessageFeedbackEntity(
    @PrimaryKey val id: String,
    val pendingMessageId: String,
    val contactId: String,
    val eventId: String,
    val reasonKey: String,
    val instruction: String,
    val draftText: String,
    val appliedToRegeneration: Boolean = false,
    val createdAtMs: Long = System.currentTimeMillis(),
)
