package com.example.core.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "gift_history",
    foreignKeys = [
        ForeignKey(
            entity = ContactEntity::class,
            parentColumns = ["id"],
            childColumns = ["contactId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["contactId"], name = "idx_gift_history_contactId")]
)
data class GiftHistoryEntity(
    @PrimaryKey val id: String,
    val contactId: String,
    val giftName: String,
    val giftCategory: String,
    val occasionType: String,
    val year: Int,
    val approxCostInr: Int,
    val receivedWell: Boolean? = null,   // null = unknown, true = liked, false = not
    val notes: String = ""
)
