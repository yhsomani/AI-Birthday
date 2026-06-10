package com.example.core.automation.sender

object EmailSubjectBuilder {
    fun build(
        contactName: String,
        eventType: String?,
        eventLabel: String? = null,
    ): String {
        val name = contactName.trim().ifBlank { "there" }
        val label = eventLabel?.trim()?.takeIf { it.isNotBlank() }

        return when (eventType?.trim()?.uppercase()) {
            "BIRTHDAY" -> "Happy birthday, $name!"
            "ANNIVERSARY" -> "Happy anniversary, $name!"
            "WORK_ANNIVERSARY" -> "Congratulations on your work anniversary, $name!"
            "GRADUATION" -> "Congratulations, $name!"
            "CUSTOM" -> label?.let { "$it for $name" } ?: "A note for $name"
            else -> label?.let { "$it for $name" } ?: "A note for $name"
        }
    }
}
