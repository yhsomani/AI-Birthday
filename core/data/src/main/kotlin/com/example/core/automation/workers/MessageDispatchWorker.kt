package com.example.core.automation.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.core.automation.sender.MessageDispatcher
import com.example.core.automation.scheduler.DailyScheduler
import com.example.core.data.R
import com.example.core.db.dao.ContactDao
import com.example.core.db.dao.EventDao
import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.dao.SentMessageDao
import com.example.core.prefs.SecurePrefs
import com.example.core.resilience.StructuredLogger
import com.example.domain.automation.AutomationSchedulePolicy
import com.example.domain.automation.DispatchBlockReason
import com.example.domain.automation.DispatchDecision
import com.example.domain.automation.DispatchEligibilityPolicy
import com.example.domain.model.MessageStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import androidx.hilt.work.HiltWorker

@HiltWorker
class MessageDispatchWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val pendingMessageDao: PendingMessageDao,
    private val sentMessageDao: SentMessageDao,
    private val contactDao: ContactDao,
    private val eventDao: EventDao,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val pendingMessageId = inputData.getString(MessageDispatchWorkRequests.KEY_PENDING_MESSAGE_ID)
        val eventId = inputData.getString(MessageDispatchWorkRequests.KEY_EVENT_ID)
        if (pendingMessageId.isNullOrBlank() && eventId.isNullOrBlank()) {
            StructuredLogger.e(TAG, "Missing pending_message_id and event_id in input data")
            return Result.failure()
        }

        StructuredLogger.i(TAG, "Dispatching message", mapOf(
            "pendingMessageId" to (pendingMessageId ?: ""),
            "eventId" to (eventId ?: ""),
        ))

        val pendingMsg = if (!pendingMessageId.isNullOrBlank()) {
            pendingMessageDao.getById(pendingMessageId)
        } else {
            pendingMessageDao.getByEventId(eventId.orEmpty())
        } ?: run {
            StructuredLogger.w(TAG, "No pending message found", extras = mapOf(
                "pendingMessageId" to (pendingMessageId ?: ""),
                "eventId" to (eventId ?: ""),
            ))
            return Result.failure()
        }

        val contact = contactDao.getById(pendingMsg.contactId) ?: run {
            StructuredLogger.w(TAG, "Contact not found for ${pendingMsg.contactId}")
            return Result.failure()
        }

        val now = System.currentTimeMillis()
        when (val decision = DispatchEligibilityPolicy.evaluate(pendingMsg, nowMs = now)) {
            DispatchDecision.SendNow -> Unit
            is DispatchDecision.DeferUntil -> {
                StructuredLogger.i(TAG, "Deferring dispatch until scheduled time", mapOf(
                    "pendingMessageId" to pendingMsg.id,
                    "scheduledForMs" to decision.epochMs.toString(),
                    "reason" to decision.reason.name,
                ))
                if (decision.epochMs != pendingMsg.scheduledForMs) {
                    pendingMessageDao.insert(pendingMsg.copy(scheduledForMs = decision.epochMs))
                }
                DailyScheduler.scheduleExactSend(context, pendingMsg.id)
                return Result.success()
            }
            is DispatchDecision.NeedsApproval -> {
                StructuredLogger.i(TAG, "Message still pending approval; waiting", mapOf(
                    "pendingMessageId" to pendingMsg.id,
                    "approvalMode" to decision.approvalMode.raw,
                ))
                return Result.success()
            }
            is DispatchDecision.Expire -> {
                StructuredLogger.i(TAG, "Approval deadline passed without user action; expiring", mapOf(
                    "pendingMessageId" to pendingMsg.id,
                    "reason" to decision.reason.name,
                ))
                pendingMessageDao.updateStatus(pendingMsg.id, MessageStatus.EXPIRED.raw)
                com.example.core.automation.notifications.NotificationHelper.showSetupNotification(
                    context,
                    context.getString(R.string.notification_setup_message_expired_title),
                    context.getString(R.string.notification_setup_message_expired_message, contact.name),
                )
                return Result.success()
            }
            is DispatchDecision.Blocked -> {
                StructuredLogger.i(TAG, "Dispatch blocked by message state", mapOf(
                    "pendingMessageId" to pendingMsg.id,
                    "status" to pendingMsg.status,
                    "reason" to decision.reason.name,
                ))
                if (decision.reason == DispatchBlockReason.ALREADY_HANDLED) {
                    com.example.core.automation.notifications.NotificationHelper.showSetupNotification(
                        context,
                        context.getString(R.string.notification_setup_double_send_title),
                        context.getString(R.string.notification_setup_double_send_message, contact.name),
                    )
                }
                return Result.success()
            }
        }

        val prefs = SecurePrefs(context)
        val nextAllowedSendMs = AutomationSchedulePolicy.nextAllowedSendMs(
            candidateMs = now,
            quietHoursStart = prefs.getQuietHoursStart(),
            quietHoursEnd = prefs.getQuietHoursEnd(),
            blackoutDatesJson = prefs.getBlackoutDates(),
            nowMs = now,
        )
        if (nextAllowedSendMs > now) {
            StructuredLogger.i(TAG, "Deferring dispatch due to quiet hours or blackout date", mapOf(
                "pendingMessageId" to pendingMsg.id,
                "nextAllowedSendMs" to nextAllowedSendMs.toString(),
            ))
            pendingMessageDao.insert(pendingMsg.copy(scheduledForMs = nextAllowedSendMs))
            DailyScheduler.scheduleExactSend(context, pendingMsg.id)
            return Result.success()
        }

        // Idempotency: mark status as DISPATCHING immediately
        pendingMessageDao.updateStatus(pendingMsg.id, MessageStatus.DISPATCHING.raw)

        try {
            val dispatcher = MessageDispatcher(context, pendingMessageDao, sentMessageDao, contactDao, eventDao)
            dispatcher.dispatch(pendingMsg, contact)
        } catch (e: Exception) {
            StructuredLogger.e(TAG, "Dispatch failed unexpectedly for message ${pendingMsg.id}", e)
            runCatching {
                pendingMessageDao.updateStatus(pendingMsg.id, MessageStatus.FAILED.raw)
            }.onFailure { statusError ->
                StructuredLogger.e(TAG, "Failed to mark message ${pendingMsg.id} as FAILED after dispatch exception", statusError)
            }
            return Result.failure()
        }

        return Result.success()
    }

    companion object {
        private const val TAG = "MessageDispatchWorker"
    }
}
