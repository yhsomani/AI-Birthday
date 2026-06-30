package com.example.core.automation.sender

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.core.db.dao.DispatchAttemptDao
import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.dao.SentMessageDao
import com.example.core.resilience.StructuredLogger
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.dispatch.DispatchAttemptResult
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsStatusReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SmsStatusReceiverEntryPoint {
        fun sentMessageDao(): SentMessageDao
        fun dispatchAttemptDao(): DispatchAttemptDao
        fun pendingMessageDao(): PendingMessageDao
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val sentMessageId = intent.getStringExtra("sent_message_id") ?: return
        val dispatchAttemptId = intent.getStringExtra("dispatch_attempt_id")
        val pendingMessageId = intent.getStringExtra("pending_message_id")
        val code = resultCode

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            SmsStatusReceiverEntryPoint::class.java
        )
        val sentMessageDao = entryPoint.sentMessageDao()
        val dispatchAttemptDao = entryPoint.dispatchAttemptDao()
        val pendingMessageDao = entryPoint.pendingMessageDao()

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    "com.example.SMS_SENT" -> {
                        val status = if (code == android.app.Activity.RESULT_OK) {
                            MessageDeliveryStatus.SENT.raw
                        } else {
                            MessageDeliveryStatus.FAILED.raw
                        }
                        sentMessageDao.updateSmsCallbackDeliveryStatus(sentMessageId, status)
                        pendingMessageDao.saveSmsCallbackPendingStatus(
                            pendingMessageId = pendingMessageId,
                            deliveryStatus = MessageDeliveryStatus.fromRaw(status),
                        )
                        dispatchAttemptDao.saveSmsCallbackOutcome(
                            dispatchAttemptId = dispatchAttemptId,
                            sentMessageId = sentMessageId,
                            deliveryStatus = MessageDeliveryStatus.fromRaw(status),
                            resultCode = code,
                            failureType = "SMS_SENT_CALLBACK_FAILED",
                            failureMessage = "Android SMS sent callback reported failure after send handoff.",
                        )
                        StructuredLogger.i(TAG, "SMS sent status updated to $status for message $sentMessageId")
                    }
                    "com.example.SMS_DELIVERED" -> {
                        val status = if (code == android.app.Activity.RESULT_OK) {
                            MessageDeliveryStatus.DELIVERED.raw
                        } else {
                            MessageDeliveryStatus.FAILED.raw
                        }
                        sentMessageDao.updateSmsCallbackDeliveryStatus(sentMessageId, status)
                        pendingMessageDao.saveSmsCallbackPendingStatus(
                            pendingMessageId = pendingMessageId,
                            deliveryStatus = MessageDeliveryStatus.fromRaw(status),
                        )
                        dispatchAttemptDao.saveSmsCallbackOutcome(
                            dispatchAttemptId = dispatchAttemptId,
                            sentMessageId = sentMessageId,
                            deliveryStatus = MessageDeliveryStatus.fromRaw(status),
                            resultCode = code,
                            failureType = "SMS_DELIVERY_CALLBACK_FAILED",
                            failureMessage = "Android SMS delivery callback reported failure after send handoff.",
                        )
                        StructuredLogger.i(TAG, "SMS delivery status updated to $status for message $sentMessageId")
                    }
                }
            } catch (e: Exception) {
                StructuredLogger.e(TAG, "Failed to update SMS status for message $sentMessageId", e)
            } finally {
                pendingResult?.finish()
            }
        }
    }

    companion object {
        private const val TAG = "SmsStatusReceiver"
    }
}

private suspend fun PendingMessageDao.saveSmsCallbackPendingStatus(
    pendingMessageId: String?,
    deliveryStatus: MessageDeliveryStatus,
) {
    val id = pendingMessageId.takeUnless { it.isNullOrBlank() } ?: return
    if (deliveryStatus == MessageDeliveryStatus.FAILED) {
        markSmsCallbackFailed(id)
    }
}

private suspend fun DispatchAttemptDao.saveSmsCallbackOutcome(
    dispatchAttemptId: String?,
    sentMessageId: String,
    deliveryStatus: MessageDeliveryStatus,
    resultCode: Int,
    failureType: String,
    failureMessage: String,
) {
    val attemptId = dispatchAttemptId.takeUnless { it.isNullOrBlank() } ?: return
    val nowMs = System.currentTimeMillis()
    val failed = deliveryStatus == MessageDeliveryStatus.FAILED
    updateSmsCallbackOutcome(
        id = attemptId,
        resolvedAtMs = nowMs,
        result = when (deliveryStatus) {
            MessageDeliveryStatus.DELIVERED -> DispatchAttemptResult.DELIVERED
            MessageDeliveryStatus.SENT -> DispatchAttemptResult.SENT
            MessageDeliveryStatus.FAILED -> DispatchAttemptResult.FAILED_FINAL
            MessageDeliveryStatus.PENDING_DELIVERY -> DispatchAttemptResult.PENDING_DELIVERY
            MessageDeliveryStatus.UNKNOWN -> DispatchAttemptResult.UNKNOWN
        }.raw,
        channel = MessageChannel.SMS.raw,
        deliveryStatus = deliveryStatus.raw,
        providerMessageId = sentMessageId,
        errorType = failureType.takeIf { failed },
        errorCode = resultCode.toString().takeIf { failed },
        redactedErrorMessage = failureMessage.takeIf { failed },
        deadLetteredAtMs = nowMs.takeIf { failed },
    )
}
