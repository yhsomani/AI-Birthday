package com.example.core.automation.notifications

import android.content.Context
import com.example.core.data.R
import com.example.core.gemini.MessageVariants
import com.example.domain.model.notification.ApprovalNotificationRequest
import com.example.domain.service.MessageVariantsResult

internal fun Context.showApprovalNotification(
    request: ApprovalNotificationRequest,
    variants: MessageVariants,
) {
    NotificationHelper.showApprovalNotification(this, request, variants)
}

internal fun MessageVariantsResult.toApprovalMessageVariants(): MessageVariants {
    return MessageVariants(
        short = short,
        standard = standard,
        long = long,
        formal = formal,
        funny = funny,
        emotional = emotional,
        recommended = recommended,
        isUsingFallback = isUsingFallback,
    )
}

internal fun ApprovalNotificationRequest.toApprovalNotificationCopy(
    context: Context,
    variants: MessageVariants,
): ApprovalNotificationCopy {
    return ApprovalNotificationCopy(
        title = context.getString(R.string.notification_approval_title, contactDisplayName),
        message = variants.standard,
    )
}

internal data class ApprovalNotificationCopy(
    val title: String,
    val message: String,
)
