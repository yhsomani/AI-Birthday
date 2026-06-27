package com.example.core.automation.sender

import android.content.Context
import com.example.core.accessibility.WhatsAppAccessibilityService
import com.example.core.accessibility.WhatsAppSendFailureReason
import com.example.core.accessibility.WhatsAppSendResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WhatsAppSender(private val context: Context) {
    suspend fun send(phoneNumber: String, message: String, eventId: String): Boolean {
        return sendWithResult(phoneNumber, message, eventId) is WhatsAppSendResult.Sent
    }

    suspend fun sendWithResult(
        phoneNumber: String,
        message: String,
        eventId: String,
    ): WhatsAppSendResult {
        val service = WhatsAppAccessibilityService.instance
            ?: return WhatsAppSendResult.Failed(WhatsAppSendFailureReason.SERVICE_DISABLED)

        return withContext(Dispatchers.Main) {
            val deferred = CompletableDeferred<WhatsAppSendResult>()
            val job = WhatsAppAccessibilityService.WhatsAppSendJob(
                phoneNumber = phoneNumber.replace(Regex("[^0-9]"), ""),
                message = message,
                eventId = eventId,
                onComplete = { result ->
                    deferred.complete(result)
                }
            )
            service.enqueueSend(job)
            deferred.await()
        }
    }
}
