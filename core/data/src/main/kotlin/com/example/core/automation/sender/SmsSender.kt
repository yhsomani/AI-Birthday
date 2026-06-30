package com.example.core.automation.sender

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager

class SmsSender(private val context: Context) {
    fun send(phoneNumber: String, message: String, sentMessageId: String) {
        send(
            phoneNumber = phoneNumber,
            message = message,
            sentMessageId = sentMessageId,
            dispatchAttemptId = null,
            pendingMessageId = null,
        )
    }

    fun send(
        phoneNumber: String,
        message: String,
        sentMessageId: String,
        dispatchAttemptId: String?,
        pendingMessageId: String?,
    ) {
        val smsManager = context.getSystemService(SmsManager::class.java)
        val parts = smsManager.divideMessage(message)

        val sentPIs = ArrayList(parts.map { _ ->
            val intent = Intent("com.example.SMS_SENT").apply {
                putExtra("sent_message_id", sentMessageId)
                dispatchAttemptId?.takeUnless(String::isBlank)?.let {
                    putExtra("dispatch_attempt_id", it)
                }
                pendingMessageId?.takeUnless(String::isBlank)?.let {
                    putExtra("pending_message_id", it)
                }
                setPackage(context.packageName)
            }
            PendingIntent.getBroadcast(context, sentMessageId.hashCode(), intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        })

        val deliveredPIs = ArrayList(parts.map { _ ->
            val intent = Intent("com.example.SMS_DELIVERED").apply {
                putExtra("sent_message_id", sentMessageId)
                dispatchAttemptId?.takeUnless(String::isBlank)?.let {
                    putExtra("dispatch_attempt_id", it)
                }
                pendingMessageId?.takeUnless(String::isBlank)?.let {
                    putExtra("pending_message_id", it)
                }
                setPackage(context.packageName)
            }
            PendingIntent.getBroadcast(context, sentMessageId.hashCode(), intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        })

        smsManager.sendMultipartTextMessage(
            phoneNumber, null, ArrayList(parts), sentPIs, deliveredPIs
        )
    }
}
