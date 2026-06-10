package com.example.domain.model

enum class MessageStatus(val raw: String) {
    PENDING("PENDING"),
    APPROVED("APPROVED"),
    DISPATCHING("DISPATCHING"),
    SENT("SENT"),
    REJECTED("REJECTED"),
    FAILED("FAILED"),
    EXPIRED("EXPIRED"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun fromRaw(value: String?): MessageStatus {
            val normalized = value?.trim()?.uppercase().orEmpty()
            return entries.firstOrNull { it.raw == normalized } ?: UNKNOWN
        }
    }
}
