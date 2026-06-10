package com.example.core.automation.sender

import com.example.core.prefs.SecurePrefs
import com.example.domain.service.TestSendService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TestSendServiceImpl @Inject constructor(
    private val securePrefs: SecurePrefs,
) : TestSendService {
    override suspend fun sendEmailToSelf(messageText: String) {
        val senderEmail = securePrefs.getSenderEmail()
        EmailSender(securePrefs).send(
            toEmail = senderEmail,
            contactName = "RelateAI Test",
            messageText = messageText,
        )
    }
}
