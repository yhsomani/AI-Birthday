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

internal const val DEFAULT_SMTP_TIMEOUT_MS: Int = 25_000

class EmailAddressValidationException(
    val field: EmailAddressField,
    cause: Throwable? = null,
) : IllegalArgumentException("Invalid ${field.raw} email address.", cause)

enum class EmailAddressField(val raw: String) {
    SENDER("sender"),
    RECIPIENT("recipient"),
}

class EmailSender(
    private val prefs: SecurePrefs,
    private val smtpTimeoutMs: Int = DEFAULT_SMTP_TIMEOUT_MS,
) {
    suspend fun send(
        toEmail: String,
        contactName: String,
        messageText: String,
        eventType: String? = null,
        eventLabel: String? = null,
        subjectOverride: String? = null,
    ) = withContext(Dispatchers.IO) {
        val props = gmailSmtpProperties(smtpTimeoutMs)
        val senderAddress = validatedEmailAddress(
            value = prefs.getSenderEmail(),
            field = EmailAddressField.SENDER,
        )
        val recipientAddress = validatedEmailAddress(
            value = toEmail,
            field = EmailAddressField.RECIPIENT,
        )

        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() = PasswordAuthentication(
                senderAddress.address,
                prefs.getSenderEmailPassword()
            )
        })

        val mimeMessage = MimeMessage(session).apply {
            setFrom(senderAddress)
            setRecipient(Message.RecipientType.TO, recipientAddress)
            subject = subjectOverride ?: EmailSubjectBuilder.build(contactName, eventType, eventLabel)
            setText(messageText)
        }

        Transport.send(mimeMessage)
    }
}

internal fun validatedEmailAddress(
    value: String?,
    field: EmailAddressField,
): InternetAddress {
    val trimmed = value?.trim().orEmpty()
    if (trimmed.isBlank()) {
        throw EmailAddressValidationException(field)
    }
    return runCatching {
        InternetAddress(trimmed, true).also { it.validate() }
    }.getOrElse { cause ->
        throw EmailAddressValidationException(field, cause)
    }
}

internal fun gmailSmtpProperties(timeoutMs: Int = DEFAULT_SMTP_TIMEOUT_MS): Properties {
    val boundedTimeoutMs = timeoutMs.coerceAtLeast(1).toString()
    return Properties().apply {
        put("mail.smtp.auth", "true")
        put("mail.smtp.starttls.enable", "true")
        put("mail.smtp.host", "smtp.gmail.com")
        put("mail.smtp.port", "587")
        put("mail.smtp.connectiontimeout", boundedTimeoutMs)
        put("mail.smtp.timeout", boundedTimeoutMs)
        put("mail.smtp.writetimeout", boundedTimeoutMs)
    }
}
