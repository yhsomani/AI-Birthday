package com.example.core.accessibility

sealed class WhatsAppSendResult {
    object Sent : WhatsAppSendResult()

    data class Failed(
        val reason: WhatsAppSendFailureReason,
    ) : WhatsAppSendResult()
}

enum class WhatsAppSendFailureReason {
    SERVICE_DISABLED,
    INVALID_PHONE_NUMBER,
    DEVICE_LOCKED,
    APP_NOT_FOUND,
    CHAT_OPEN_TIMEOUT,
    COMPOSE_FIELD_NOT_FOUND,
    TEXT_VERIFICATION_FAILED,
    SEND_BUTTON_NOT_FOUND,
    SEND_CONFIRMATION_TIMEOUT,
    SENDER_CALLBACK_TIMEOUT,
}
