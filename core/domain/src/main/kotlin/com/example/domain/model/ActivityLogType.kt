package com.example.domain.model

enum class ActivityLogType(val raw: String) {
    MESSAGE("MESSAGE"),
    EVENT("EVENT"),
    AI("AI"),
    ANALYTICS("ANALYTICS"),
    BACKUP("BACKUP"),
    SYNC("SYNC"),
    SETTINGS("SETTINGS"),
    DISPATCH("DISPATCH"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun fromRaw(value: String?): ActivityLogType {
            val normalized = value?.trim()?.uppercase().orEmpty()
            return entries.firstOrNull { it.raw == normalized } ?: UNKNOWN
        }
    }
}
