package com.example.core.automation.sender

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.core.db.dao.SentMessageDao
import com.example.core.resilience.StructuredLogger
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
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val sentMessageId = intent.getStringExtra("sent_message_id") ?: return
        val code = resultCode

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            SmsStatusReceiverEntryPoint::class.java
        )
        val sentMessageDao = entryPoint.sentMessageDao()

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    "com.example.SMS_SENT" -> {
                        val status = if (code == android.app.Activity.RESULT_OK) "SENT" else "FAILED"
                        sentMessageDao.updateDeliveryStatus(sentMessageId, status)
                        StructuredLogger.i(TAG, "SMS sent status updated to $status for message $sentMessageId")
                    }
                    "com.example.SMS_DELIVERED" -> {
                        val status = if (code == android.app.Activity.RESULT_OK) "DELIVERED" else "FAILED"
                        sentMessageDao.updateDeliveryStatus(sentMessageId, status)
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
