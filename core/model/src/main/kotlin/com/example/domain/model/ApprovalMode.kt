package com.example.domain.model

enum class ApprovalMode(val raw: String) {
    DEFAULT("DEFAULT"),
    FULLY_AUTO("FULLY_AUTO"),
    SMART_APPROVE("SMART_APPROVE"),
    VIP_APPROVE("VIP_APPROVE"),
    ALWAYS_ASK("ALWAYS_ASK"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun fromRaw(value: String?): ApprovalMode {
            val normalized = value?.trim()?.uppercase().orEmpty()
            return entries.firstOrNull { it.raw == normalized } ?: UNKNOWN
        }
    }
}
