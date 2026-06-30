package com.example.core.automation.sender

import com.example.core.accessibility.WhatsAppSendFailureReason
import com.example.domain.model.dispatch.DispatchAttemptResult
import java.net.SocketTimeoutException
import javax.mail.AuthenticationFailedException
import javax.mail.MessagingException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DispatchProviderRetryPolicyTest {

    @Test
    fun `sms permission failure is final`() {
        val failure = DispatchProviderRetryPolicy.smsPermissionDenied()

        assertEquals(DispatchAttemptResult.FAILED_FINAL, failure.result)
        assertEquals(DispatchProviderRetryPolicy.ERROR_SMS_PERMISSION_DENIED, failure.errorType)
        assertEquals("ANDROID_SEND_SMS_PERMISSION", failure.errorCode)
        assertNull(failure.nextRetryDelayMs)
    }

    @Test
    fun `sms provider exception is retryable`() {
        val failure = DispatchProviderRetryPolicy.smsProviderException(RuntimeException("radio unavailable"))

        assertEquals(DispatchAttemptResult.FAILED_RETRYABLE, failure.result)
        assertEquals(DispatchProviderRetryPolicy.ERROR_SMS_TRANSIENT_PROVIDER_FAILURE, failure.errorType)
        assertEquals("RuntimeException", failure.errorCode)
        assertEquals(DispatchProviderRetryPolicy.DEFAULT_RETRY_DELAY_MS, failure.nextRetryDelayMs)
    }

    @Test
    fun `whatsapp disabled accessibility service failure is final`() {
        val failure = DispatchProviderRetryPolicy.whatsAppAutomationUnavailable()

        assertEquals(DispatchAttemptResult.FAILED_FINAL, failure.result)
        assertEquals(DispatchProviderRetryPolicy.ERROR_WHATSAPP_AUTOMATION_UNAVAILABLE, failure.errorType)
        assertEquals("ACCESSIBILITY_AUTOMATION_UNAVAILABLE", failure.errorCode)
        assertNull(failure.nextRetryDelayMs)
    }

    @Test
    fun `whatsapp missing consent failure is final`() {
        val failure = DispatchProviderRetryPolicy.whatsAppConsentRequired()

        assertEquals(DispatchAttemptResult.FAILED_FINAL, failure.result)
        assertEquals(DispatchProviderRetryPolicy.ERROR_WHATSAPP_CONSENT_REQUIRED, failure.errorType)
        assertEquals("APP_CONSENT_NOT_GRANTED", failure.errorCode)
        assertNull(failure.nextRetryDelayMs)
    }

    @Test
    fun `whatsapp invalid phone number failure is final with setup code`() {
        val failure = DispatchProviderRetryPolicy.whatsAppAutomationFailure(
            WhatsAppSendFailureReason.INVALID_PHONE_NUMBER
        )

        assertEquals(DispatchAttemptResult.FAILED_FINAL, failure.result)
        assertEquals(DispatchProviderRetryPolicy.ERROR_WHATSAPP_AUTOMATION_UNAVAILABLE, failure.errorType)
        assertEquals("WHATSAPP_INVALID_PHONE_NUMBER", failure.errorCode)
        assertEquals(
            "Contact phone number is not usable for WhatsApp automation.",
            failure.redactedErrorMessage,
        )
        assertNull(failure.nextRetryDelayMs)
    }

    @Test
    fun `whatsapp automation reason failure is final with specific code`() {
        val failure = DispatchProviderRetryPolicy.whatsAppAutomationFailure(
            WhatsAppSendFailureReason.COMPOSE_FIELD_NOT_FOUND
        )

        assertEquals(DispatchAttemptResult.FAILED_FINAL, failure.result)
        assertEquals(DispatchProviderRetryPolicy.ERROR_WHATSAPP_AUTOMATION_FAILURE, failure.errorType)
        assertEquals("COMPOSE_FIELD_NOT_FOUND", failure.errorCode)
        assertNull(failure.nextRetryDelayMs)
    }

    @Test
    fun `whatsapp sender watchdog timeout is final with specific code`() {
        val failure = DispatchProviderRetryPolicy.whatsAppAutomationFailure(
            WhatsAppSendFailureReason.SENDER_CALLBACK_TIMEOUT
        )

        assertEquals(DispatchAttemptResult.FAILED_FINAL, failure.result)
        assertEquals(DispatchProviderRetryPolicy.ERROR_WHATSAPP_AUTOMATION_FAILURE, failure.errorType)
        assertEquals("SENDER_CALLBACK_TIMEOUT", failure.errorCode)
        assertEquals(
            "WhatsApp automation did not complete before the sender watchdog timeout.",
            failure.redactedErrorMessage,
        )
        assertNull(failure.nextRetryDelayMs)
    }

    @Test
    fun `email authentication failure is final`() {
        val failure = DispatchProviderRetryPolicy.emailProviderException(
            AuthenticationFailedException("bad credentials"),
        )

        assertEquals(DispatchAttemptResult.FAILED_FINAL, failure.result)
        assertEquals(DispatchProviderRetryPolicy.ERROR_EMAIL_AUTHENTICATION_FAILED, failure.errorType)
        assertEquals("SMTP_AUTHENTICATION_FAILED", failure.errorCode)
        assertNull(failure.nextRetryDelayMs)
    }

    @Test
    fun `email invalid sender address failure is final`() {
        val failure = DispatchProviderRetryPolicy.emailProviderException(
            EmailAddressValidationException(EmailAddressField.SENDER),
        )

        assertEquals(DispatchAttemptResult.FAILED_FINAL, failure.result)
        assertEquals(DispatchProviderRetryPolicy.ERROR_EMAIL_INVALID_ADDRESS, failure.errorType)
        assertEquals("EMAIL_INVALID_SENDER_ADDRESS", failure.errorCode)
        assertEquals(
            "Configured sender email address is invalid; setup must be reviewed.",
            failure.redactedErrorMessage,
        )
        assertNull(failure.nextRetryDelayMs)
    }

    @Test
    fun `email invalid recipient address failure is final`() {
        val failure = DispatchProviderRetryPolicy.emailProviderException(
            EmailAddressValidationException(EmailAddressField.RECIPIENT),
        )

        assertEquals(DispatchAttemptResult.FAILED_FINAL, failure.result)
        assertEquals(DispatchProviderRetryPolicy.ERROR_EMAIL_INVALID_ADDRESS, failure.errorType)
        assertEquals("EMAIL_INVALID_RECIPIENT_ADDRESS", failure.errorCode)
        assertEquals(
            "Contact email address is invalid; update the contact before retry.",
            failure.redactedErrorMessage,
        )
        assertNull(failure.nextRetryDelayMs)
    }

    @Test
    fun `email messaging network failure is retryable`() {
        val failure = DispatchProviderRetryPolicy.emailProviderException(
            MessagingException("timeout", SocketTimeoutException("smtp timeout")),
        )

        assertEquals(DispatchAttemptResult.FAILED_RETRYABLE, failure.result)
        assertEquals(DispatchProviderRetryPolicy.ERROR_EMAIL_TRANSIENT_PROVIDER_FAILURE, failure.errorType)
        assertEquals("SocketTimeoutException", failure.errorCode)
        assertEquals(DispatchProviderRetryPolicy.DEFAULT_RETRY_DELAY_MS, failure.nextRetryDelayMs)
    }

    @Test
    fun `selection keeps retryable failure when another route failed finally`() {
        val selected = DispatchProviderRetryPolicy.select(
            DispatchProviderRetryPolicy.whatsAppAutomationUnavailable(),
            DispatchProviderRetryPolicy.smsProviderException(RuntimeException("radio unavailable")),
        )

        assertEquals(DispatchAttemptResult.FAILED_RETRYABLE, selected.result)
        assertEquals(DispatchProviderRetryPolicy.ERROR_SMS_TRANSIENT_PROVIDER_FAILURE, selected.errorType)
    }

    @Test
    fun `automatic retry limit converts retryable failure to final`() {
        val limited = DispatchProviderRetryPolicy.applyAutomaticRetryLimit(
            failure = DispatchProviderRetryPolicy.smsProviderException(RuntimeException("radio unavailable")),
            retryCount = DispatchProviderRetryPolicy.MAX_AUTOMATIC_RETRY_FAILURES,
        )

        assertEquals(DispatchAttemptResult.FAILED_FINAL, limited.result)
        assertEquals(DispatchProviderRetryPolicy.ERROR_SMS_TRANSIENT_PROVIDER_FAILURE, limited.errorType)
        assertNull(limited.nextRetryDelayMs)
    }
}
