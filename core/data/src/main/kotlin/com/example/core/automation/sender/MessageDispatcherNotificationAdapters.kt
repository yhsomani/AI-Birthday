package com.example.core.automation.sender

import android.content.Context
import com.example.core.automation.notifications.NotificationHelper
import com.example.core.data.R
import com.example.domain.model.notification.SmsPermissionSetupNotificationRequest

internal fun smsPermissionSetupNotificationRequest(
    contactDisplayName: String,
): SmsPermissionSetupNotificationRequest {
    return SmsPermissionSetupNotificationRequest(
        contactDisplayName = contactDisplayName,
    )
}

internal fun Context.showSmsPermissionSetupNotification(request: SmsPermissionSetupNotificationRequest) {
    NotificationHelper.showSetupNotification(
        this,
        getString(R.string.notification_setup_sms_permission_title),
        getString(R.string.notification_setup_sms_permission_message, request.contactDisplayName),
    )
}
