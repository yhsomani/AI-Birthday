package com.example.core.automation.sender

import com.example.core.prefs.SecurePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

class EmailSender(private val prefs: SecurePrefs) {
    suspend fun send(
        toEmail: String,
        contactName: String,
        messageText: String,
        eventType: String? = null,
        eventLabel: String? = null,
        subjectOverride: String? = null,
    ) = withContext(Dispatchers.IO) {
        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.host", "smtp.gmail.com")
            put("mail.smtp.port", "587")
        }

        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() = PasswordAuthentication(
                prefs.getSenderEmail(),
                prefs.getSenderEmailPassword()
            )
        })

        val mimeMessage = MimeMessage(session).apply {
            setFrom(InternetAddress(prefs.getSenderEmail()))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail))
            subject = subjectOverride ?: EmailSubjectBuilder.build(contactName, eventType, eventLabel)
            setText(messageText)
        }

        Transport.send(mimeMessage)
    }
}
