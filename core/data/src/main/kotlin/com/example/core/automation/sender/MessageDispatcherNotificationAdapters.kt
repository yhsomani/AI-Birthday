package com.example.core.automation.sender

import android.content.Context
import com.example.core.automation.notifications.NotificationHelper
import com.example.domain.model.notification.SetupNotificationReason
import com.example.domain.model.notification.SetupNotificationRequest
import com.example.domain.model.notification.SmsPermissionSetupNotificationRequest

internal fun setupNotificationRequest(
    reason: SetupNotificationReason,
    contactDisplayName: String? = null,
): SetupNotificationRequest {
    return SetupNotificationRequest(
        reason = reason,
        contactDisplayName = contactDisplayName,
    )
}

internal fun smsPermissionSetupNotificationRequest(
    contactDisplayName: String,
): SmsPermissionSetupNotificationRequest {
    return SmsPermissionSetupNotificationRequest(
        contactDisplayName = contactDisplayName,
    )
}

internal fun Context.showSetupNotification(request: SetupNotificationRequest) {
    NotificationHelper.showSetupNotification(this, request)
}

internal fun Context.showSmsPermissionSetupNotification(request: SmsPermissionSetupNotificationRequest) {
    showSetupNotification(
        SetupNotificationRequest(
            reason = SetupNotificationReason.SMS_PERMISSION_MISSING,
            contactDisplayName = request.contactDisplayName,
        )
    )
}
