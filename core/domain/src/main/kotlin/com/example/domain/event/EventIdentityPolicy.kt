package com.example.domain.event

import com.example.domain.model.occasion.Occasion
import com.example.domain.model.occasion.OccasionType
import java.util.Locale

object EventIdentityPolicy {
    fun canonicalId(contactId: String, eventType: String): String? {
        return when (OccasionType.fromRaw(eventType)) {
            OccasionType.BIRTHDAY -> "${contactId}_birthday"
            OccasionType.ANNIVERSARY -> "${contactId}_anniversary"
            OccasionType.WORK_ANNIVERSARY -> "${contactId}_work_anniversary"
            else -> null
        }
    }

    fun hasMatchingActiveOccasion(
        occasions: List<Occasion>,
        contactId: String,
        occasionType: String,
        month: Int,
        dayOfMonth: Int,
        label: String? = null,
    ): Boolean = findMatchingActiveOccasion(
        occasions = occasions,
        contactId = contactId,
        occasionType = occasionType,
        month = month,
        dayOfMonth = dayOfMonth,
        label = label,
    ) != null

    fun findMatchingActiveOccasion(
        occasions: List<Occasion>,
        contactId: String,
        occasionType: String,
        month: Int,
        dayOfMonth: Int,
        label: String? = null,
    ): Occasion? {
        val normalizedType = occasionType.normalizedOccasionType()
        return occasions.firstOrNull { occasion ->
            occasion.isActive &&
                occasion.contactId.value == contactId &&
                occasion.type.raw == normalizedType &&
                occasion.date.month == month &&
                occasion.date.dayOfMonth == dayOfMonth &&
                labelsAreCompatibleForDuplicate(normalizedType, occasion.label, label)
        }
    }

    fun findConflictingActiveOccasion(
        occasions: List<Occasion>,
        contactId: String,
        occasionType: String,
        month: Int,
        dayOfMonth: Int,
        label: String? = null,
    ): Occasion? {
        val normalizedType = occasionType.normalizedOccasionType()
        return occasions.firstOrNull { occasion ->
            occasion.isActive &&
                occasion.contactId.value == contactId &&
                occasion.type.raw == normalizedType &&
                (occasion.date.month != month || occasion.date.dayOfMonth != dayOfMonth) &&
                labelsAreCompatibleForDuplicate(normalizedType, occasion.label, label)
        }
    }

    private fun labelsAreCompatibleForDuplicate(
        eventType: String,
        existingLabel: String?,
        newLabel: String?,
    ): Boolean {
        if (OccasionType.fromRaw(eventType) != OccasionType.CUSTOM) return true
        val normalizedExisting = existingLabel.orEmpty().trim().lowercase(Locale.US)
        val normalizedNew = newLabel.orEmpty().trim().lowercase(Locale.US)
        return normalizedExisting.isBlank() ||
            normalizedNew.isBlank() ||
            normalizedExisting == normalizedNew
    }

    private fun String.normalizedOccasionType(): String {
        val normalized = trim().uppercase(Locale.US)
        return OccasionType.fromRaw(normalized).takeIf { it != OccasionType.UNKNOWN }?.raw ?: normalized
    }
}
