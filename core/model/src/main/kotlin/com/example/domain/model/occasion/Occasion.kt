package com.example.domain.model.occasion

import com.example.domain.model.common.ContactId
import com.example.domain.model.common.OccasionId

data class Occasion(
    val id: OccasionId,
    val contactId: ContactId,
    val type: OccasionType,
    val label: String?,
    val date: OccasionDate,
    val nextOccurrenceMs: Long,
    val isActive: Boolean,
    val notifyDaysBefore: Int,
    val source: String,
    val confidenceScore: Int,
    val isVerified: Boolean,
)

data class OccasionDate(
    val dayOfMonth: Int,
    val month: Int,
    val year: Int? = null,
)

enum class OccasionType(val raw: String) {
    BIRTHDAY("BIRTHDAY"),
    ANNIVERSARY("ANNIVERSARY"),
    WORK_ANNIVERSARY("WORK_ANNIVERSARY"),
    GRADUATION("GRADUATION"),
    HOLIDAY("HOLIDAY"),
    REVIVAL("REVIVAL"),
    FOLLOW_UP("FOLLOW_UP"),
    CUSTOM("CUSTOM"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun fromRaw(value: String?): OccasionType {
            val normalized = value?.trim()?.uppercase().orEmpty()
            return entries.firstOrNull { it.raw == normalized } ?: UNKNOWN
        }
    }
}

enum class OccasionConflictKind {
    NONE,
    DUPLICATE,
    DATE_CONFLICT,
}

