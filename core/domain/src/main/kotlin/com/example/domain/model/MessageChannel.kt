package com.example.domain.model

enum class MessageChannel(val raw: String) {
    SMS("SMS"),
    WHATSAPP("WHATSAPP"),
    EMAIL("EMAIL"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun fromRaw(value: String?): MessageChannel {
            val normalized = value?.trim()?.uppercase().orEmpty()
            return entries.firstOrNull { it.raw == normalized } ?: UNKNOWN
        }
    }
}
