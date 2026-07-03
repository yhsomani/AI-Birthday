package com.example.domain.automation

import com.example.domain.model.MessageDeliveryStatus

data class SmsDeliveryStatusRecoveryDecision(
    val cutoffMs: Long,
    val recoveredStatus: MessageDeliveryStatus,
)

object SmsDeliveryStatusRecoveryPolicy {
    const val DEFAULT_STALE_PENDING_DELIVERY_MS: Long = 24L * 60 * 60 * 1000

    fun stalePendingDeliveryDecision(
        nowMs: Long,
        stalePendingDeliveryMs: Long = DEFAULT_STALE_PENDING_DELIVERY_MS,
    ): SmsDeliveryStatusRecoveryDecision {
        return SmsDeliveryStatusRecoveryDecision(
            cutoffMs = nowMs - stalePendingDeliveryMs.coerceAtLeast(0L),
            recoveredStatus = MessageDeliveryStatus.UNKNOWN,
        )
    }
}
