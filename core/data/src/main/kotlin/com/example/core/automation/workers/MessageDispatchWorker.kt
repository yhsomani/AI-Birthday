package com.example.core.automation.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.core.automation.sender.MessageDispatcher
import com.example.core.db.dao.ContactDao
import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.dao.SentMessageDao
import com.example.core.resilience.StructuredLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import androidx.hilt.work.HiltWorker

@HiltWorker
class MessageDispatchWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val pendingMessageDao: PendingMessageDao,
    private val sentMessageDao: SentMessageDao,
    private val contactDao: ContactDao
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

        val scheduledForMs = pendingMsg.scheduledForMs
        val approvalWindowMs = 2 * 60 * 60 * 1000L // 2 hours
        val approvalDeadlineMs = scheduledForMs + approvalWindowMs
        val now = System.currentTimeMillis()

        var shouldSend = false

        when {
            pendingMsg.status == "APPROVED" -> {
                shouldSend = true
            }
            pendingMsg.status == "SENT" || pendingMsg.status == "DISPATCHING" -> {
                StructuredLogger.w(TAG, "Message ${pendingMsg.id} is already in state ${pendingMsg.status}; aborting to prevent double-send")
                com.example.core.automation.notifications.NotificationHelper.showSetupNotification(
                    context,
                    "Double-Send Prevented",
                    "A potential duplicate send for ${contact.name} was blocked."
                )
                return Result.success()
            }
            pendingMsg.status == "REJECTED" -> {
                StructuredLogger.i(TAG, "Message ${pendingMsg.id} was rejected; skipping")
                return Result.success()
            }
            pendingMsg.status == "PENDING" -> {
                when {
                    pendingMsg.approvalMode == "FULLY_AUTO" -> {
                        shouldSend = true
                    }
                    pendingMsg.approvalMode == "SMART_APPROVE" -> {
                        if (now >= scheduledForMs) {
                            StructuredLogger.i(TAG, "SMART_APPROVE window passed for ${pendingMsg.id}; auto-sending")
                            shouldSend = true
                        } else {
                            StructuredLogger.i(TAG, "SMART_APPROVE window still active; deferring dispatch")
                            return Result.success()
                        }
                    }
                    pendingMsg.approvalMode == "VIP_APPROVE" -> {
                        if (now >= approvalDeadlineMs) {
                            StructuredLogger.i(TAG, "VIP_APPROVE deadline passed for ${pendingMsg.id} without user action; expiring")
                            pendingMessageDao.updateStatus(pendingMsg.id, "EXPIRED")
                            com.example.core.automation.notifications.NotificationHelper.showSetupNotification(
                                context,
                                "Message Expired",
                                "Birthday message for ${contact.name} expired without approval."
                            )
                            return Result.success()
                        } else {
                            StructuredLogger.i(TAG, "VIP_APPROVE message still pending user action; waiting")
                            return Result.success()
                        }
                    }
                    else -> {
                        StructuredLogger.i(TAG, "Message still pending approval; waiting")
                        return Result.success()
                    }
                }
            }
        }

        if (shouldSend) {
            // Idempotency: mark status as DISPATCHING immediately
            pendingMessageDao.updateStatus(pendingMsg.id, "DISPATCHING")

            try {
                val dispatcher = MessageDispatcher(context, pendingMessageDao, sentMessageDao, contactDao)
                dispatcher.dispatch(pendingMsg, contact)
            } catch (e: Exception) {
                StructuredLogger.e(TAG, "Dispatch failed unexpectedly for message ${pendingMsg.id}", e)
                runCatching {
                    pendingMessageDao.updateStatus(pendingMsg.id, "FAILED")
                }.onFailure { statusError ->
                    StructuredLogger.e(TAG, "Failed to mark message ${pendingMsg.id} as FAILED after dispatch exception", statusError)
                }
                return Result.failure()
            }
        }

        return Result.success()
    }

    companion object {
        private const val TAG = "MessageDispatchWorker"
    }
}
