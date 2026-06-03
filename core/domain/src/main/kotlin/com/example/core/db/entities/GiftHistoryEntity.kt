package com.example.core.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gift_history", indices = [androidx.room.Index(value = ["contactId"])])
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
