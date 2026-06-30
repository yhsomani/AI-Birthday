package com.example.core.automation.sender

import com.example.domain.model.occasion.OccasionType

object EmailSubjectBuilder {
    fun build(
        contactName: String,
        eventType: String?,
        eventLabel: String? = null,
    ): String {
        val name = contactName.trim().ifBlank { "there" }
        val label = eventLabel?.trim()?.takeIf { it.isNotBlank() }

        return when (OccasionType.fromRaw(eventType)) {
            OccasionType.BIRTHDAY -> "Happy birthday, $name!"
            OccasionType.ANNIVERSARY -> "Happy anniversary, $name!"
            OccasionType.WORK_ANNIVERSARY -> "Congratulations on your work anniversary, $name!"
            OccasionType.GRADUATION -> "Congratulations, $name!"
            OccasionType.HOLIDAY -> label?.let { "$it wishes for $name" } ?: "Holiday wishes for $name"
            OccasionType.REVIVAL -> "Checking in, $name"
            OccasionType.FOLLOW_UP -> "Following up, $name"
            OccasionType.CUSTOM -> label?.let { "$it for $name" } ?: "A note for $name"
            else -> label?.let { "$it for $name" } ?: "A note for $name"
        }
    }
}
