package com.example.domain.usecase

import com.example.core.db.entities.ActivityLogEntity
import com.example.core.db.entities.PendingMessageEntity
import com.example.domain.automation.DispatchBlockReason
import com.example.domain.automation.DispatchDecision
import com.example.domain.automation.DispatchEligibilityPolicy
import com.example.domain.repository.ActivityLogRepository
import com.example.domain.model.ActivityLogSeverity
import com.example.domain.model.ActivityLogStatus
import com.example.domain.model.ActivityLogType
import com.example.domain.model.ApprovalMode
import com.example.domain.model.DispatchActivityDecision
import com.example.domain.model.MessageStatus
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.MessageRepository
import com.example.domain.service.MessageDispatcherService
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dispatches a pending message via SMS, WhatsApp, or Email.
 * - Looks up pending message by id first, then eventId for legacy callers
 * - Uses the shared dispatch eligibility policy before sending
 * - No-ops if contact/pending message is missing
 * - Updates contact's last-wished timestamp and consecutive-years-wished count
 */
@Singleton
class DispatchMessageUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
    private val contactRepository: ContactRepository,
    private val messageDispatcherService: MessageDispatcherService,
    private val activityLogRepository: ActivityLogRepository,
) {
    suspend operator fun invoke(messageRef: String): DispatchOutcome {
        val pending = messageRepository.getPendingById(messageRef)
            ?: messageRepository.getPendingByEventId(messageRef)
            ?: return DispatchOutcome.PendingNotFound

        when (val decision = DispatchEligibilityPolicy.evaluate(
            pending = pending,
            approvalMode = ApprovalMode.fromRaw(pending.approvalMode),
        )) {
            DispatchDecision.SendNow -> Unit
            is DispatchDecision.DeferUntil -> {
                recordDispatchActivity(
                    pending = pending,
                    title = "Dispatch deferred",
                    detail = "Message is scheduled for later.",
                    severity = ActivityLogSeverity.INFO,
                    status = ActivityLogStatus.OPEN,
                    decision = DispatchActivityDecision.DEFERRED,
                    reason = decision.reason.name,
                    scheduledForMs = decision.epochMs,
                )
                return DispatchOutcome.Deferred(pending.id, decision.epochMs)
            }
            is DispatchDecision.NeedsApproval -> {
                recordDispatchActivity(
                    pending = pending,
                    title = "Dispatch waiting for approval",
                    detail = "Message still needs approval before it can be sent.",
                    severity = ActivityLogSeverity.INFO,
                    status = ActivityLogStatus.OPEN,
                    decision = DispatchActivityDecision.NEEDS_APPROVAL,
                    reason = decision.approvalMode.raw,
                )
                return DispatchOutcome.NotApproved(pending.status)
            }
            is DispatchDecision.Expire -> {
                messageRepository.updatePendingStatus(pending.id, MessageStatus.EXPIRED.raw)
                recordDispatchActivity(
                    pending = pending.copy(status = MessageStatus.EXPIRED.raw),
                    title = "Dispatch expired",
                    detail = "Message approval window expired before sending.",
                    severity = ActivityLogSeverity.WARNING,
                    status = ActivityLogStatus.RESOLVED,
                    decision = DispatchActivityDecision.EXPIRED,
                    reason = decision.reason.name,
                )
                return DispatchOutcome.Expired(pending.id)
            }
            is DispatchDecision.Blocked -> {
                recordDispatchActivity(
                    pending = pending,
                    title = "Dispatch blocked",
                    detail = blockedDetail(decision.reason),
                    severity = blockedSeverity(decision.reason),
                    status = ActivityLogStatus.OPEN,
                    decision = DispatchActivityDecision.BLOCKED,
                    reason = decision.reason.name,
                )
                return DispatchOutcome.NotApproved(pending.status)
            }
        }

        val contact = contactRepository.getById(pending.contactId) ?: run {
            recordDispatchActivity(
                pending = pending,
                title = "Dispatch blocked",
                detail = "Message contact could not be found.",
                severity = ActivityLogSeverity.ERROR,
                status = ActivityLogStatus.OPEN,
                decision = DispatchActivityDecision.BLOCKED,
                reason = "CONTACT_NOT_FOUND",
            )
            return DispatchOutcome.ContactNotFound
        }

        messageDispatcherService.dispatch(pending, contact)
        recordDispatchActivity(
            pending = pending,
            title = "Dispatch sent",
            detail = "Message dispatched through ${pending.channel}.",
            severity = ActivityLogSeverity.INFO,
            status = ActivityLogStatus.RESOLVED,
            decision = DispatchActivityDecision.SENT,
        )

        return DispatchOutcome.Sent(pending.id, pending.channel)
    }

    sealed class DispatchOutcome {
        data object PendingNotFound : DispatchOutcome()
        data object ContactNotFound : DispatchOutcome()
        data class NotApproved(val status: String) : DispatchOutcome()
        data class Deferred(val pendingId: String, val scheduledForMs: Long) : DispatchOutcome()
        data class Expired(val pendingId: String) : DispatchOutcome()
        data class Sent(val pendingId: String, val channel: String) : DispatchOutcome()
    }

    private suspend fun recordDispatchActivity(
        pending: PendingMessageEntity,
        title: String,
        detail: String,
        severity: ActivityLogSeverity,
        status: ActivityLogStatus,
        decision: DispatchActivityDecision,
        reason: String? = null,
        scheduledForMs: Long? = null,
    ) {
        runCatching {
            activityLogRepository.record(
                ActivityLogEntity(
                    id = UUID.randomUUID().toString(),
                    type = ActivityLogType.MESSAGE.raw,
                    title = title,
                    detail = detail,
                    contactId = pending.contactId,
                    eventId = pending.eventId,
                    messageId = pending.id,
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

    private fun dispatchMetadataJson(
        pending: PendingMessageEntity,
        decision: DispatchActivityDecision,
        reason: String?,
        scheduledForMs: Long?,
    ): String {
        val fields = buildList {
            add("decision" to decision.raw)
            add("messageId" to pending.id)
            add("eventId" to pending.eventId)
            add("contactId" to pending.contactId)
            add("channel" to pending.channel)
            add("approvalMode" to pending.approvalMode)
            add("status" to pending.status)
            reason?.let { add("reason" to it) }
            scheduledForMs?.let { add("scheduledForMs" to it.toString()) }
        }
        return fields.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "\"${key.jsonEscaped()}\":\"${value.jsonEscaped()}\""
        }
    }

    private fun blockedDetail(reason: DispatchBlockReason): String {
        return when (reason) {
            DispatchBlockReason.ALREADY_HANDLED -> "Message was already handled."
            DispatchBlockReason.REJECTED -> "Message was rejected before dispatch."
            DispatchBlockReason.EXPIRED -> "Message already expired."
            DispatchBlockReason.FAILED -> "Message is marked failed and needs recovery."
            DispatchBlockReason.UNSUPPORTED_STATE -> "Message is in an unsupported dispatch state."
        }
    }

    private fun blockedSeverity(reason: DispatchBlockReason): ActivityLogSeverity {
        return when (reason) {
            DispatchBlockReason.ALREADY_HANDLED -> ActivityLogSeverity.INFO
            DispatchBlockReason.REJECTED,
            DispatchBlockReason.EXPIRED -> ActivityLogSeverity.WARNING
            DispatchBlockReason.FAILED,
            DispatchBlockReason.UNSUPPORTED_STATE -> ActivityLogSeverity.ERROR
        }
    }

    private fun String.jsonEscaped(): String {
        return buildString {
            for (char in this@jsonEscaped) {
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> {
                        if (char.code < 0x20) {
                            append("\\u")
                            append(char.code.toString(16).padStart(4, '0'))
                        } else {
                            append(char)
                        }
                    }
                }
            }
        }
    }
}
