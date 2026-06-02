package com.example.automation.sender

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager

class SmsSender(private val context: Context) {
    fun send(phoneNumber: String, message: String, eventId: String) {
        val smsManager = context.getSystemService(SmsManager::class.java)
        val parts = smsManager.divideMessage(message)

        val sentPIs = parts.map { _ ->
            val intent = Intent("SMS_SENT_$eventId")
            PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        val deliveredPIs = parts.map { _ ->
            val intent = Intent("SMS_DELIVERED_$eventId")
            PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        smsManager.sendMultipartTextMessage(
            phoneNumber, null, parts as ArrayList<String>,
            sentPIs as ArrayList<PendingIntent>, deliveredPIs as ArrayList<PendingIntent>
        )
    }
}
