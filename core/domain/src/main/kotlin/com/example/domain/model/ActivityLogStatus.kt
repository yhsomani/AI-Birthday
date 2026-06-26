package com.example.domain.model

enum class ActivityLogStatus(val raw: String) {
    OPEN("OPEN"),
    RESOLVED("RESOLVED"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun fromRaw(value: String?): ActivityLogStatus {
            val normalized = value?.trim()?.uppercase().orEmpty()
            return entries.firstOrNull { it.raw == normalized } ?: UNKNOWN
        }
    }
}
