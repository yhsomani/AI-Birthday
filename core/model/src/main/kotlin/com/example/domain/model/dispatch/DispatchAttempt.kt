package com.example.domain.model.dispatch

import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.DispatchAttemptId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId

data class DispatchAttempt(
    val id: DispatchAttemptId,
    val messageDraftId: MessageDraftId,
    val contactId: ContactId?,
    val occasionId: OccasionId?,
    val channel: MessageChannel,
    val routeRank: Int,
    val eligibilityDecision: DispatchEligibilityRecord,
    val blockOrDeferReason: String?,
    val requestedAtMs: Long,
    val attemptedAtMs: Long?,
    val resolvedAtMs: Long?,
    val result: DispatchAttemptResult,
    val deliveryStatus: MessageDeliveryStatus,
    val providerMessageId: String?,
    val errorType: String?,
    val errorCode: String?,
    val redactedErrorMessage: String?,
    val retryCount: Int,
    val nextRetryAtMs: Long?,
    val deadLetteredAtMs: Long?,
    val createdBy: DispatchAttemptCreator,
    val metadataJson: String = "{}",
)

data class DispatchAttemptOutcomeUpdate(
    val id: DispatchAttemptId,
    val attemptedAtMs: Long?,
    val resolvedAtMs: Long?,
    val result: DispatchAttemptResult,
    val channel: MessageChannel?,
    val deliveryStatus: MessageDeliveryStatus,
    val providerMessageId: String?,
    val errorType: String?,
    val errorCode: String?,
    val redactedErrorMessage: String?,
    val retryCount: Int,
    val nextRetryAtMs: Long?,
    val deadLetteredAtMs: Long?,
)

enum class DispatchEligibilityRecord(val raw: String) {
    SEND_NOW("SEND_NOW"),
    DEFERRED("DEFERRED"),
    NEEDS_APPROVAL("NEEDS_APPROVAL"),
    BLOCKED("BLOCKED"),
    EXPIRED("EXPIRED"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun fromRaw(value: String?): DispatchEligibilityRecord {
            val normalized = value?.trim()?.uppercase().orEmpty()
            return entries.firstOrNull { it.raw == normalized } ?: UNKNOWN
        }
    }
}

enum class DispatchAttemptResult(val raw: String) {
    QUEUED("QUEUED"),
    SENT("SENT"),
    PENDING_DELIVERY("PENDING_DELIVERY"),
    DELIVERED("DELIVERED"),
    RETRY_QUEUED("RETRY_QUEUED"),
    DEFERRED("DEFERRED"),
    NEEDS_APPROVAL("NEEDS_APPROVAL"),
    BLOCKED("BLOCKED"),
    EXPIRED("EXPIRED"),
    FAILED_RETRYABLE("FAILED_RETRYABLE"),
    FAILED_FINAL("FAILED_FINAL"),
    CANCELLED("CANCELLED"),
    UNKNOWN("UNKNOWN");

    val isTerminal: Boolean
        get() = this == SENT ||
            this == DELIVERED ||
            this == BLOCKED ||
            this == EXPIRED ||
            this == FAILED_FINAL ||
            this == CANCELLED

    companion object {
        fun fromRaw(value: String?): DispatchAttemptResult {
            val normalized = value?.trim()?.uppercase().orEmpty()
            return entries.firstOrNull { it.raw == normalized } ?: UNKNOWN
        }
    }
}

enum class DispatchAttemptCreator(val raw: String) {
    USER("USER"),
    WORKER("WORKER"),
    RECEIVER("RECEIVER"),
    SYSTEM("SYSTEM"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun fromRaw(value: String?): DispatchAttemptCreator {
            val normalized = value?.trim()?.uppercase().orEmpty()
            return entries.firstOrNull { it.raw == normalized } ?: UNKNOWN
        }
    }
}
