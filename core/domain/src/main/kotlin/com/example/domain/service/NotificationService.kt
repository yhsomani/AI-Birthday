package com.example.domain.service

import com.example.domain.model.notification.ApprovalNotificationRequest

interface NotificationService {
    fun showApprovalNotification(
        request: ApprovalNotificationRequest,
        variants: MessageVariantsResult,
    )

    fun showAiFallbackAlert()
}
