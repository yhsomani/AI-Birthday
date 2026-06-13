package com.example.core.automation.sender

import android.content.Context
import com.example.core.accessibility.WhatsAppAccessibilityService
import com.example.core.prefs.SecurePrefs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WhatsAppSender(private val context: Context) {
    suspend fun send(phoneNumber: String, message: String, eventId: String): Boolean = withContext(Dispatchers.Main) {
        val deferred = CompletableDeferred<Boolean>()
        val job = WhatsAppAccessibilityService.WhatsAppSendJob(
            phoneNumber = phoneNumber.replace(NON_DIGIT_PATTERN, ""),
            message = message,
            eventId = eventId,
            onComplete = { result ->
                deferred.complete(result)
            }
        )
        // Ensure service is running
        if (WhatsAppAccessibilityService.instance != null) {
            WhatsAppAccessibilityService.instance?.enqueueSend(job)
            return@withContext deferred.await()
        } else {
            return@withContext false
        }
    }

    companion object {
        private val NON_DIGIT_PATTERN = Regex("[^0-9]")
    }
}
