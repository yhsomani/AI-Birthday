package com.example.domain.model

enum class MessageDeliveryStatus(val raw: String) {
    PENDING_DELIVERY("PENDING_DELIVERY"),
    SENT("SENT"),
    DELIVERED("DELIVERED"),
    FAILED("FAILED"),
    UNKNOWN("UNKNOWN");

    val isSuccessfulForRouting: Boolean
        get() = this == PENDING_DELIVERY || this == SENT || this == DELIVERED

    companion object {
        fun fromRaw(value: String?): MessageDeliveryStatus {
            val normalized = value?.trim()?.uppercase().orEmpty()
            return entries.firstOrNull { it.raw == normalized } ?: UNKNOWN
        }
    }
}
