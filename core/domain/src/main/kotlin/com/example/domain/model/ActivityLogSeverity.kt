package com.example.domain.model

enum class ActivityLogSeverity(val raw: String) {
    INFO("INFO"),
    WARNING("WARNING"),
    ERROR("ERROR"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun fromRaw(value: String?): ActivityLogSeverity {
            val normalized = value?.trim()?.uppercase().orEmpty()
            return entries.firstOrNull { it.raw == normalized } ?: UNKNOWN
        }
    }
}
