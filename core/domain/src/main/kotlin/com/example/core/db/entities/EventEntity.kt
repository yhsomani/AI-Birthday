package com.example.core.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Calendar

@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = ContactEntity::class,
            parentColumns = ["id"],
            childColumns = ["contactId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["nextOccurrenceMs"], name = "idx_events_nextOccurrenceMs"),
        Index(value = ["contactId"], name = "idx_events_contactId"),
        Index(value = ["isActive", "nextOccurrenceMs"], name = "idx_events_active")
    ]
)
data class EventEntity(
    @PrimaryKey val id: String,
    val contactId: String,
    val type: String,                // BIRTHDAY, ANNIVERSARY, WORK_ANNIVERSARY, GRADUATION, CUSTOM
    val label: String? = null,       // For CUSTOM events
    val dayOfMonth: Int,
    val month: Int,
    val year: Int? = null,
    val nextOccurrenceMs: Long,
    val isActive: Boolean = true,
    val notifyDaysBefore: Int = 1,    // Can be 0 (day-of) or 1,2,3,7
    val source: String = "CONTACTS",  // CONTACTS, CALENDAR, MANUAL, AI_INFERRED
    val confidenceScore: Int = 100,   // 0-100, events found in multiple sources get higher confidence
    val isVerified: Boolean = true    // Low-confidence events shown with "Verify?" badge
) {
    @get:Ignore
    val daysUntil: Int
        get() = ((nextOccurrenceMs - System.currentTimeMillis()).coerceAtLeast(0) / 86400000L).toInt()

    @get:Ignore
    val ageTurning: Int?
        get() = if (year != null) {
            Calendar.getInstance().get(Calendar.YEAR) - year
        } else null
}
