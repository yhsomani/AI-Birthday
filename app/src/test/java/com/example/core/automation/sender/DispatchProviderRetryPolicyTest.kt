package com.example.core.automation.sender

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
    fun `whatsapp automation failure is final until sender exposes richer cause`() {
        val failure = DispatchProviderRetryPolicy.whatsAppAutomationUnavailable()

        assertEquals(DispatchAttemptResult.FAILED_FINAL, failure.result)
        assertEquals(DispatchProviderRetryPolicy.ERROR_WHATSAPP_AUTOMATION_UNAVAILABLE, failure.errorType)
        assertEquals("ACCESSIBILITY_AUTOMATION_UNAVAILABLE", failure.errorCode)
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
}
