package com.example.core.automation.sender

import com.example.domain.model.EventType

object EmailSubjectBuilder {
    fun build(
        contactName: String,
        eventType: String?,
        eventLabel: String? = null,
    ): String {
        val name = contactName.trim().ifBlank { "there" }
        val label = eventLabel?.trim()?.takeIf { it.isNotBlank() }

        return when (EventType.fromRaw(eventType)) {
            EventType.BIRTHDAY -> "Happy birthday, $name!"
            EventType.ANNIVERSARY -> "Happy anniversary, $name!"
            EventType.WORK_ANNIVERSARY -> "Congratulations on your work anniversary, $name!"
            EventType.GRADUATION -> "Congratulations, $name!"
            EventType.CUSTOM -> label?.let { "$it for $name" } ?: "A note for $name"
            else -> label?.let { "$it for $name" } ?: "A note for $name"
        }
    }
}
