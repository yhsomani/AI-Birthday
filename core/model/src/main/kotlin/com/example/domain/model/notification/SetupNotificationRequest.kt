package com.example.domain.model.notification

enum class SetupNotificationReason {
    SMS_PERMISSION_MISSING,
    MESSAGE_EXPIRED,
    DOUBLE_SEND_GUARD,
    AI_PROVIDER_MISSING,
    REVIVAL_AI_PROVIDER_MISSING,
    EXACT_ALARM_PERMISSION_MISSING,
}

data class SetupNotificationRequest(
    val reason: SetupNotificationReason,
    val contactDisplayName: String? = null,
)

data class SmsPermissionSetupNotificationRequest(
    val contactDisplayName: String,
)
