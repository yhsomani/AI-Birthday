package com.example.core.db.entities

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Calendar

@Entity(
    tableName = "events",
    indices = [Index(value = ["nextOccurrenceMs"], name = "idx_events_nextOccurrenceMs"), Index(value = ["contactId"], name = "idx_events_contactId")]
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
    val daysUntil: Int = 0,          // Pre-computed for dashboard sort
    val isActive: Boolean = true,
    val notifyDaysBefore: Int = 1,    // Can be 0 (day-of) or 1,2,3,7
    val source: String = "CONTACTS",  // CONTACTS, CALENDAR, MANUAL, AI_INFERRED
    val confidenceScore: Int = 100,   // 0-100, events found in multiple sources get higher confidence
    val isVerified: Boolean = true    // Low-confidence events shown with "Verify?" badge
) {
    @get:Ignore
    val ageTurning: Int?
        get() = if (year != null) {
            Calendar.getInstance().get(Calendar.YEAR) - year!!
        } else null
}
