package com.example.domain.model

enum class DispatchActivityDecision(val raw: String) {
    DEFERRED("DEFERRED"),
    NEEDS_APPROVAL("NEEDS_APPROVAL"),
    EXPIRED("EXPIRED"),
    BLOCKED("BLOCKED"),
    SENT("SENT"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun fromRaw(value: String?): DispatchActivityDecision {
            val normalized = value?.trim()?.uppercase().orEmpty()
            return entries.firstOrNull { it.raw == normalized } ?: UNKNOWN
        }
    }
}
