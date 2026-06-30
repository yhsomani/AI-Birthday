package com.example.core.automation.sender

import android.content.Context
import com.example.core.accessibility.WhatsAppAccessibilityService
import com.example.core.accessibility.WhatsAppSendFailureReason
import com.example.core.accessibility.WhatsAppSendResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class WhatsAppSender(
    private val context: Context,
    private val serviceProvider: () -> WhatsAppAccessibilityService? = { WhatsAppAccessibilityService.instance },
    private val callbackTimeoutMs: Long = DEFAULT_CALLBACK_TIMEOUT_MS,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
) {
    suspend fun send(phoneNumber: String, message: String, eventId: String): Boolean {
        return sendWithResult(phoneNumber, message, eventId) is WhatsAppSendResult.Sent
    }

    suspend fun sendWithResult(
        phoneNumber: String,
        message: String,
        eventId: String,
    ): WhatsAppSendResult {
        val sanitizedPhoneNumber = phoneNumber.replace(Regex("[^0-9]"), "")
        if (sanitizedPhoneNumber.isBlank()) {
            return WhatsAppSendResult.Failed(WhatsAppSendFailureReason.INVALID_PHONE_NUMBER)
        }

        val service = serviceProvider()
            ?: return WhatsAppSendResult.Failed(WhatsAppSendFailureReason.SERVICE_DISABLED)

        return withContext(mainDispatcher) {
            val deferred = CompletableDeferred<WhatsAppSendResult>()
            val job = WhatsAppAccessibilityService.WhatsAppSendJob(
                phoneNumber = sanitizedPhoneNumber,
                message = message,
                eventId = eventId,
                onComplete = { result ->
                    deferred.complete(result)
                }
            )
            service.enqueueSend(job)
            withTimeoutOrNull(callbackTimeoutMs) {
                deferred.await()
            } ?: WhatsAppSendResult.Failed(WhatsAppSendFailureReason.SENDER_CALLBACK_TIMEOUT).also {
                service.cancelSend(eventId, WhatsAppSendFailureReason.SENDER_CALLBACK_TIMEOUT)
            }
        }
    }

    private companion object {
        const val DEFAULT_CALLBACK_TIMEOUT_MS = 25_000L
    }
}
