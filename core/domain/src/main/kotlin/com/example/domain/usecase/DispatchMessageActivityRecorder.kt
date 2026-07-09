package com.example.domain.usecase

import com.example.domain.automation.DispatchBlockReason
import com.example.domain.model.ActivityLogSeverity
import com.example.domain.model.ActivityLogStatus
import com.example.domain.model.ActivityLogType
import com.example.domain.model.DispatchActivityDecision
import com.example.domain.model.activity.ActivityLogRecord
import com.example.domain.model.common.JsonTextCodec
import com.example.domain.model.message.MessageDispatchState
import com.example.domain.repository.ActivityLogRepository
import java.util.UUID

internal suspend fun ActivityLogRepository.recordDispatchActivity(
    pending: MessageDispatchState,
    title: String,
    detail: String,
    severity: ActivityLogSeverity,
    status: ActivityLogStatus,
    decision: DispatchActivityDecision,
    reason: String? = null,
    scheduledForMs: Long? = null,
) {
    runCatching {
        record(
            ActivityLogRecord(
                id = UUID.randomUUID().toString(),
                type = ActivityLogType.MESSAGE.raw,
                title = title,
                detail = detail,
                contactId = pending.contactId.value,
                eventId = pending.occasionId.value,
                messageId = pending.id.value,
                severity = severity.raw,
                status = status.raw,
                metadataJson = dispatchMetadataJson(
                    pending = pending,
                    decision = decision,
                    reason = reason,
                    scheduledForMs = scheduledForMs,
                ),
            )
        )
    }
}

internal fun blockedDispatchDetail(reason: DispatchBlockReason): String {
    return when (reason) {
        DispatchBlockReason.ALREADY_HANDLED -> "Message was already handled."
        DispatchBlockReason.REJECTED -> "Message was rejected before dispatch."
        DispatchBlockReason.EXPIRED -> "Message already expired."
        DispatchBlockReason.FAILED -> "Message is marked failed and needs recovery."
        DispatchBlockReason.UNSUPPORTED_STATE -> "Message is in an unsupported dispatch state."
    }
}

internal fun blockedDispatchSeverity(reason: DispatchBlockReason): ActivityLogSeverity {
    return when (reason) {
        DispatchBlockReason.ALREADY_HANDLED -> ActivityLogSeverity.INFO
        DispatchBlockReason.REJECTED,
        DispatchBlockReason.EXPIRED -> ActivityLogSeverity.WARNING
        DispatchBlockReason.FAILED,
        DispatchBlockReason.UNSUPPORTED_STATE -> ActivityLogSeverity.ERROR
    }
}

private fun dispatchMetadataJson(
    pending: MessageDispatchState,
    decision: DispatchActivityDecision,
    reason: String?,
    scheduledForMs: Long?,
): String {
    val fields = buildList {
        add("decision" to decision.raw)
        add("messageId" to pending.id.value)
        add("eventId" to pending.occasionId.value)
        add("contactId" to pending.contactId.value)
        add("channel" to pending.channel.raw)
        add("approvalMode" to pending.draft.approvalMode.raw)
        add("status" to pending.status.raw)
        reason?.let { add("reason" to it) }
        scheduledForMs?.let { add("scheduledForMs" to it.toString()) }
    }
    return JsonTextCodec.encodeObject(fields)
}
