package com.example.domain.model

enum class EventType(val raw: String) {
    BIRTHDAY("BIRTHDAY"),
    ANNIVERSARY("ANNIVERSARY"),
    WORK_ANNIVERSARY("WORK_ANNIVERSARY"),
    GRADUATION("GRADUATION"),
    HOLIDAY("HOLIDAY"),
    FOLLOW_UP("FOLLOW_UP"),
    CUSTOM("CUSTOM"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun fromRaw(value: String?): EventType {
            val normalized = value?.trim()?.uppercase().orEmpty()
            return entries.firstOrNull { it.raw == normalized } ?: UNKNOWN
        }
    }
}
