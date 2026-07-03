package com.example.core.automation.sender

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.core.db.dao.DispatchAttemptDao
import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.dao.SentMessageDao
import com.example.core.resilience.StructuredLogger
import com.example.domain.dispatch.SmsCallbackKind
import com.example.domain.dispatch.SmsCallbackOutcome
import com.example.domain.dispatch.SmsCallbackOutcomePolicy
import com.example.domain.model.MessageChannel
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
                val callbackKind = action.toSmsCallbackKind()
                if (callbackKind != null) {
                    val outcome = SmsCallbackOutcomePolicy.evaluate(
                        kind = callbackKind,
                        succeeded = code == android.app.Activity.RESULT_OK,
                    )
                    when (callbackKind) {
                        SmsCallbackKind.SENT -> {
                            val status = outcome.deliveryStatus.raw
                            sentMessageDao.updateSmsCallbackDeliveryStatus(sentMessageId, status)
                            pendingMessageDao.saveSmsCallbackPendingStatus(
                                pendingMessageId = pendingMessageId,
                                outcome = outcome,
                            )
                            dispatchAttemptDao.saveSmsCallbackOutcome(
                                dispatchAttemptId = dispatchAttemptId,
                                sentMessageId = sentMessageId,
                                outcome = outcome,
                                resultCode = code,
                            )
                            StructuredLogger.i(TAG, "SMS sent status updated to $status for message $sentMessageId")
                        }
                        SmsCallbackKind.DELIVERED -> {
                            val status = outcome.deliveryStatus.raw
                            sentMessageDao.updateSmsCallbackDeliveryStatus(sentMessageId, status)
                            pendingMessageDao.saveSmsCallbackPendingStatus(
                                pendingMessageId = pendingMessageId,
                                outcome = outcome,
                            )
                            dispatchAttemptDao.saveSmsCallbackOutcome(
                                dispatchAttemptId = dispatchAttemptId,
                                sentMessageId = sentMessageId,
                                outcome = outcome,
                                resultCode = code,
                            )
                            StructuredLogger.i(TAG, "SMS delivery status updated to $status for message $sentMessageId")
                        }
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

private fun String.toSmsCallbackKind(): SmsCallbackKind? {
    return when (this) {
        "com.example.SMS_SENT" -> SmsCallbackKind.SENT
        "com.example.SMS_DELIVERED" -> SmsCallbackKind.DELIVERED
        else -> null
    }
}

private suspend fun PendingMessageDao.saveSmsCallbackPendingStatus(
    pendingMessageId: String?,
    outcome: SmsCallbackOutcome,
) {
    val id = pendingMessageId.takeUnless { it.isNullOrBlank() } ?: return
    if (outcome.shouldMarkPendingFailed) {
        markSmsCallbackFailed(id)
    }
}

private suspend fun DispatchAttemptDao.saveSmsCallbackOutcome(
    dispatchAttemptId: String?,
    sentMessageId: String,
    outcome: SmsCallbackOutcome,
    resultCode: Int,
) {
    val attemptId = dispatchAttemptId.takeUnless { it.isNullOrBlank() } ?: return
    val nowMs = System.currentTimeMillis()
    updateSmsCallbackOutcome(
        id = attemptId,
        resolvedAtMs = nowMs,
        result = outcome.dispatchAttemptResult.raw,
        channel = MessageChannel.SMS.raw,
        deliveryStatus = outcome.deliveryStatus.raw,
        providerMessageId = sentMessageId,
        errorType = outcome.failureType,
        errorCode = resultCode.toString().takeIf { outcome.failureType != null },
        redactedErrorMessage = outcome.failureMessage,
        deadLetteredAtMs = nowMs.takeIf { outcome.deadLetter },
    )
}
