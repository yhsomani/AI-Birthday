package com.example.core.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dispatch_attempts",
    foreignKeys = [
        ForeignKey(
            entity = PendingMessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageDraftId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ContactEntity::class,
            parentColumns = ["id"],
            childColumns = ["contactId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["occasionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(
            value = ["messageDraftId", "requestedAtMs"],
            name = "idx_dispatch_attempts_messageDraftId_requestedAtMs",
        ),
        Index(
            value = ["result", "nextRetryAtMs"],
            name = "idx_dispatch_attempts_result_nextRetryAtMs",
        ),
        Index(value = ["deadLetteredAtMs"], name = "idx_dispatch_attempts_deadLetteredAtMs"),
        Index(
            value = ["contactId", "requestedAtMs"],
            name = "idx_dispatch_attempts_contactId_requestedAtMs",
        ),
        Index(value = ["occasionId"], name = "idx_dispatch_attempts_occasionId"),
    ],
)
data class DispatchAttemptEntity(
    @PrimaryKey val id: String,
    val messageDraftId: String,
    val contactId: String? = null,
    val occasionId: String? = null,
    val channel: String,
    @ColumnInfo(defaultValue = "0") val routeRank: Int = 0,
    val eligibilityDecision: String,
    val blockOrDeferReason: String? = null,
    val requestedAtMs: Long,
    val attemptedAtMs: Long? = null,
    val resolvedAtMs: Long? = null,
    val result: String,
    val deliveryStatus: String,
    val providerMessageId: String? = null,
    val errorType: String? = null,
    val errorCode: String? = null,
    val redactedErrorMessage: String? = null,
    @ColumnInfo(defaultValue = "0") val retryCount: Int = 0,
    val nextRetryAtMs: Long? = null,
    val deadLetteredAtMs: Long? = null,
    val createdBy: String,
    @ColumnInfo(defaultValue = "'{}'") val metadataJson: String = "{}",
)
