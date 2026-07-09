package com.example.core.automation.notifications

import android.content.Context
import com.example.core.resilience.StructuredLogger
import com.example.domain.model.notification.ApprovalNotificationRequest
import com.example.domain.model.notification.SystemAlertNotificationReason
import com.example.domain.model.notification.SystemAlertNotificationRequest
import com.example.domain.service.MessageVariantsResult
import com.example.domain.service.NotificationService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationServiceImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : NotificationService {

    override fun showApprovalNotification(
        request: ApprovalNotificationRequest,
        variants: MessageVariantsResult,
    ) {
        context.showApprovalNotification(request, variants.toApprovalMessageVariants())
    }

    override fun showAiFallbackAlert() {
        try {
            context.showSystemAlert(
                SystemAlertNotificationRequest(
                    reason = SystemAlertNotificationReason.AI_FALLBACK_USED,
                )
            )
        } catch (e: Exception) {
            StructuredLogger.e(TAG, "Failed to show AI fallback alert", e)
        }
    }

    private companion object {
        const val TAG = "NotificationServiceImpl"
    }
}
