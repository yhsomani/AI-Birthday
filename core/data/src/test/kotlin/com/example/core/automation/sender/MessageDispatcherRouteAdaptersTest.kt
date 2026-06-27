package com.example.core.automation.sender

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.prefs.SecurePrefs
import com.example.core.resilience.LogLevel
import com.example.core.resilience.StructuredLogger
import com.example.domain.model.common.MessageDraftId
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import java.net.SocketTimeoutException
import javax.mail.AuthenticationFailedException
import javax.mail.MessagingException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MessageDispatcherRouteAdaptersTest {
    private lateinit var context: Context
    private val prefs: SecurePrefs = mockk(relaxed = true)

    @Before
    fun setUp() {
        StructuredLogger.clearForTests()
        context = ApplicationProvider.getApplicationContext()
        mockkConstructor(EmailSender::class)
        mockkConstructor(SmsSender::class)
        mockkConstructor(WhatsAppSender::class)
    }

    @After
    fun tearDown() {
        StructuredLogger.clearForTests()
        unmockkAll()
    }

    @Test
    fun dispatchWhatsAppRoute_returnsSentWhenSenderSucceeds() = runTest {
        coEvery {
            anyConstructed<WhatsAppSender>().send("+15551234567", "Selected", "event_1")
        } returns true

        val result = context.dispatchWhatsAppRoute(
            phoneNumber = "+15551234567",
            messageText = "Selected",
            eventRef = "event_1",
        )

        assertTrue(result.sent)
        assertNull(result.failure)
    }

    @Test
    fun dispatchWhatsAppRoute_mapsUnavailableAutomationToProviderFailure() = runTest {
        coEvery {
            anyConstructed<WhatsAppSender>().send("+15551234567", "Selected", "event_1")
        } returns false

        val result = context.dispatchWhatsAppRoute(
            phoneNumber = "+15551234567",
            messageText = "Selected",
            eventRef = "event_1",
        )

        assertFalse(result.sent)
        assertEquals(
            DispatchProviderRetryPolicy.ERROR_WHATSAPP_AUTOMATION_UNAVAILABLE,
            result.failure?.errorType,
        )
        assertEquals("ACCESSIBILITY_AUTOMATION_UNAVAILABLE", result.failure?.errorCode)
        assertEquals(
            "WhatsApp automation was unavailable; setup must be reviewed before retry.",
            result.failure?.redactedErrorMessage,
        )
    }

    @Test
    fun dispatchWhatsAppRouteWithFailureLog_recordsFailureLogWhenRouteFails() = runTest {
        coEvery {
            anyConstructed<WhatsAppSender>().send("+15551234567", "Selected", "event_1")
        } returns false

        val result = context.dispatchWhatsAppRouteWithFailureLog(
            messageId = MessageDraftId("pending_1"),
            phoneNumber = "+15551234567",
            messageText = "Selected",
            eventRef = "event_1",
        )

        assertFalse(result.sent)
        assertEquals(
            DispatchProviderRetryPolicy.ERROR_WHATSAPP_AUTOMATION_UNAVAILABLE,
            result.failure?.errorType,
        )
        val entry = StructuredLogger.getRecent(1).single()
        assertEquals("MessageDispatcher", entry.tag)
        assertEquals(LogLevel.WARN, entry.level)
        assertEquals("WhatsApp route failed for pending_1; trying next automatic route", entry.message)
    }

    @Test
    fun dispatchSmsRoute_returnsSentWhenSenderSucceeds() {
        every {
            anyConstructed<SmsSender>().send("+15551234567", "Selected", "sent_1")
        } just Runs

        val result = context.dispatchSmsRoute(
            phoneNumber = "+15551234567",
            messageText = "Selected",
            sentMessageId = "sent_1",
        )

        assertTrue(result.sent)
        assertNull(result.failure)
        assertNull(result.cause)
        assertFalse(result.isPermissionDenied)
    }

    @Test
    fun dispatchSmsRoute_mapsMissingPermissionToProviderFailure() {
        val exception = SecurityException("missing permission")
        every {
            anyConstructed<SmsSender>().send("+15551234567", "Selected", "sent_1")
        } throws exception

        val result = context.dispatchSmsRoute(
            phoneNumber = "+15551234567",
            messageText = "Selected",
            sentMessageId = "sent_1",
        )

        assertFalse(result.sent)
        assertEquals(
            DispatchProviderRetryPolicy.ERROR_SMS_PERMISSION_DENIED,
            result.failure?.errorType,
        )
        assertEquals("ANDROID_SEND_SMS_PERMISSION", result.failure?.errorCode)
        assertEquals(
            "SMS permission is missing; automatic SMS cannot be sent.",
            result.failure?.redactedErrorMessage,
        )
        assertEquals(exception, result.cause)
        assertTrue(result.isPermissionDenied)
    }

    @Test
    fun dispatchSmsRoute_mapsProviderExceptionToRetryableProviderFailure() {
        val exception = RuntimeException("radio unavailable")
        every {
            anyConstructed<SmsSender>().send("+15551234567", "Selected", "sent_1")
        } throws exception

        val result = context.dispatchSmsRoute(
            phoneNumber = "+15551234567",
            messageText = "Selected",
            sentMessageId = "sent_1",
        )

        assertFalse(result.sent)
        assertEquals(
            DispatchProviderRetryPolicy.ERROR_SMS_TRANSIENT_PROVIDER_FAILURE,
            result.failure?.errorType,
        )
        assertEquals("RuntimeException", result.failure?.errorCode)
        assertEquals(
            "SMS provider failed before delivery confirmation; retry is allowed.",
            result.failure?.redactedErrorMessage,
        )
        assertEquals(exception, result.cause)
        assertFalse(result.isPermissionDenied)
    }

    @Test
    fun dispatchEmailRoute_returnsSentWhenSenderSucceeds() = runTest {
        coEvery {
            anyConstructed<EmailSender>().send(
                toEmail = "amit@example.com",
                contactName = "Amit",
                messageText = "Selected",
                eventType = "BIRTHDAY",
                eventLabel = "Birthday",
                subjectOverride = null,
            )
        } returns Unit

        val result = prefs.dispatchEmailRoute(
            toEmail = "amit@example.com",
            contactName = "Amit",
            messageText = "Selected",
            eventType = "BIRTHDAY",
            eventLabel = "Birthday",
        )

        assertTrue(result.sent)
        assertNull(result.failure)
        assertNull(result.cause)
    }

    @Test
    fun dispatchEmailRoute_mapsAuthenticationFailureToProviderFailure() = runTest {
        val exception = AuthenticationFailedException("bad credentials")
        coEvery {
            anyConstructed<EmailSender>().send(
                toEmail = "amit@example.com",
                contactName = "Amit",
                messageText = "Selected",
                eventType = "BIRTHDAY",
                eventLabel = "Birthday",
                subjectOverride = null,
            )
        } throws exception

        val result = prefs.dispatchEmailRoute(
            toEmail = "amit@example.com",
            contactName = "Amit",
            messageText = "Selected",
            eventType = "BIRTHDAY",
            eventLabel = "Birthday",
        )

        assertFalse(result.sent)
        assertEquals(
            DispatchProviderRetryPolicy.ERROR_EMAIL_AUTHENTICATION_FAILED,
            result.failure?.errorType,
        )
        assertEquals("SMTP_AUTHENTICATION_FAILED", result.failure?.errorCode)
        assertEquals(
            "Email provider rejected configured credentials; setup must be reviewed.",
            result.failure?.redactedErrorMessage,
        )
        assertEquals(exception, result.cause)
    }

    @Test
    fun dispatchEmailRoute_mapsMessagingFailureToRetryableProviderFailure() = runTest {
        val exception = MessagingException("timeout", SocketTimeoutException("smtp timeout"))
        coEvery {
            anyConstructed<EmailSender>().send(
                toEmail = "amit@example.com",
                contactName = "Amit",
                messageText = "Selected",
                eventType = "BIRTHDAY",
                eventLabel = "Birthday",
                subjectOverride = null,
            )
        } throws exception

        val result = prefs.dispatchEmailRoute(
            toEmail = "amit@example.com",
            contactName = "Amit",
            messageText = "Selected",
            eventType = "BIRTHDAY",
            eventLabel = "Birthday",
        )

        assertFalse(result.sent)
        assertEquals(
            DispatchProviderRetryPolicy.ERROR_EMAIL_TRANSIENT_PROVIDER_FAILURE,
            result.failure?.errorType,
        )
        assertEquals("SocketTimeoutException", result.failure?.errorCode)
        assertEquals(
            "Email provider failed before accepting the message; retry is allowed.",
            result.failure?.redactedErrorMessage,
        )
        assertEquals(exception, result.cause)
    }

    @Test
    fun dispatchEmailRouteWithFailureLog_recordsFailureLogWhenRouteFails() = runTest {
        val exception = MessagingException("timeout", SocketTimeoutException("smtp timeout"))
        coEvery {
            anyConstructed<EmailSender>().send(
                toEmail = "amit@example.com",
                contactName = "Amit",
                messageText = "Selected",
                eventType = "BIRTHDAY",
                eventLabel = "Birthday",
                subjectOverride = null,
            )
        } throws exception

        val result = prefs.dispatchEmailRouteWithFailureLog(
            messageId = MessageDraftId("pending_2"),
            toEmail = "amit@example.com",
            contactName = "Amit",
            messageText = "Selected",
            eventType = "BIRTHDAY",
            eventLabel = "Birthday",
        )

        assertFalse(result.sent)
        assertEquals(
            DispatchProviderRetryPolicy.ERROR_EMAIL_TRANSIENT_PROVIDER_FAILURE,
            result.failure?.errorType,
        )
        val entry = StructuredLogger.getRecent(1).single()
        assertEquals("MessageDispatcher", entry.tag)
        assertEquals(LogLevel.ERROR, entry.level)
        assertEquals("Email route failed for pending_2; trying next automatic route", entry.message)
        assertEquals("MessagingException", entry.extras["exception"])
        assertEquals("timeout", entry.extras["exceptionMessage"])
    }
}
