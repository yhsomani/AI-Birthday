package com.example.core.automation.sender

import com.example.core.resilience.LogLevel
import com.example.core.resilience.StructuredLogger
import com.example.domain.model.MessageChannel
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.MessageDraftId
import java.net.SocketTimeoutException
import javax.mail.MessagingException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MessageDispatcherLoggingAdaptersTest {

    @Before
    fun setUp() {
        StructuredLogger.clearForTests()
    }

    @After
    fun tearDown() {
        StructuredLogger.clearForTests()
    }

    @Test
    fun recordMessageDispatchLifecycleLog_recordsDispatchStartedInfo() {
        recordMessageDispatchLifecycleLog(
            messageDispatchStartedLog(
                messageId = MessageDraftId("pending_start"),
                preferredChannel = MessageChannel.SMS,
                contactId = ContactId("contact_1"),
            )
        )

        val entry = StructuredLogger.getRecent(1).single()
        assertEquals("MessageDispatcher", entry.tag)
        assertEquals(LogLevel.INFO, entry.level)
        assertEquals("Dispatching message", entry.message)
        assertEquals("pending_start", entry.extras["messageId"])
        assertEquals("SMS", entry.extras["channel"])
        assertEquals("contact_1", entry.extras["contactId"])
    }

    @Test
    fun recordMessageDispatchLifecycleLog_recordsNoRouteWarningWithRouteMetadata() {
        recordMessageDispatchLifecycleLog(
            messageDispatchNoRouteLog(
                messageId = MessageDraftId("pending_no_route"),
                preferredChannel = MessageChannel.EMAIL,
                blockedChannels = setOf(MessageChannel.SMS),
                hasPhone = false,
                hasEmail = true,
            )
        )

        val entry = StructuredLogger.getRecent(1).single()
        assertEquals("MessageDispatcher", entry.tag)
        assertEquals(LogLevel.WARN, entry.level)
        assertEquals("No available delivery route for automatic message dispatch", entry.message)
        assertEquals("pending_no_route", entry.extras["messageId"])
        assertEquals("EMAIL", entry.extras["preferredChannel"])
        assertEquals("SMS", entry.extras["blockedChannels"])
        assertEquals("false", entry.extras["hasPhone"])
        assertEquals("true", entry.extras["hasEmail"])
    }

    @Test
    fun recordMessageDispatchLifecycleLog_recordsDispatchSucceededInfo() {
        recordMessageDispatchLifecycleLog(
            messageDispatchSucceededLog(
                messageId = MessageDraftId("pending_sent"),
                channel = MessageChannel.WHATSAPP,
            )
        )

        val entry = StructuredLogger.getRecent(1).single()
        assertEquals("MessageDispatcher", entry.tag)
        assertEquals(LogLevel.INFO, entry.level)
        assertEquals("Message dispatched successfully", entry.message)
        assertEquals("pending_sent", entry.extras["messageId"])
        assertEquals("WHATSAPP", entry.extras["channel"])
    }

    @Test
    fun recordMessageDispatchLifecycleLog_recordsDispatchFailedWarning() {
        recordMessageDispatchLifecycleLog(
            messageDispatchFailedLog(
                messageId = MessageDraftId("pending_failed"),
                preferredChannel = MessageChannel.SMS,
                failure = DispatchProviderRetryPolicy.smsPermissionDenied(),
            )
        )

        val entry = StructuredLogger.getRecent(1).single()
        assertEquals("MessageDispatcher", entry.tag)
        assertEquals(LogLevel.WARN, entry.level)
        assertEquals(
            "Failed to dispatch message pending_failed via SMS: " +
                DispatchProviderRetryPolicy.ERROR_SMS_PERMISSION_DENIED,
            entry.message,
        )
        assertEquals(emptyMap<String, String>(), entry.extras)
    }

    @Test
    fun recordMessageDispatchLifecycleLog_recordsDispatchAttemptOutcomeFailureWithCauseMetadata() {
        recordMessageDispatchLifecycleLog(
            messageDispatchAttemptOutcomeUpdateFailedLog(
                dispatchAttemptId = "attempt_1",
                cause = IllegalStateException("dao unavailable"),
            )
        )

        val entry = StructuredLogger.getRecent(1).single()
        assertEquals("MessageDispatcher", entry.tag)
        assertEquals(LogLevel.ERROR, entry.level)
        assertEquals("Failed to update dispatch attempt attempt_1", entry.message)
        assertNull(entry.throwable)
        assertEquals("IllegalStateException", entry.extras["exception"])
        assertEquals("dao unavailable", entry.extras["exceptionMessage"])
    }

    @Test
    fun messageDispatchRouteFailureLog_mapsSmsPermissionFailureToPermissionReason() {
        val log = messageDispatchRouteFailureLog(
            messageId = MessageDraftId("pending_1"),
            channel = MessageChannel.SMS,
            failure = DispatchProviderRetryPolicy.smsPermissionDenied(),
            cause = SecurityException("missing permission"),
        )

        assertEquals(MessageDraftId("pending_1"), log.messageId)
        assertEquals(MessageChannel.SMS, log.channel)
        assertEquals(MessageDispatchRouteFailureReason.SMS_PERMISSION_DENIED, log.reason)
    }

    @Test
    fun recordMessageDispatchRouteFailureLog_recordsWhatsAppRouteWarning() {
        recordMessageDispatchRouteFailureLog(
            messageDispatchRouteFailureLog(
                messageId = MessageDraftId("pending_1"),
                channel = MessageChannel.WHATSAPP,
                failure = DispatchProviderRetryPolicy.whatsAppAutomationUnavailable(),
            )
        )

        val entry = StructuredLogger.getRecent(1).single()
        assertEquals("MessageDispatcher", entry.tag)
        assertEquals(LogLevel.WARN, entry.level)
        assertEquals("WhatsApp route failed for pending_1; trying next automatic route", entry.message)
        assertNull(entry.throwable)
        assertEquals(emptyMap<String, String>(), entry.extras)
    }

    @Test
    fun recordMessageDispatchRouteFailureLog_recordsSmsPermissionErrorWithCauseMetadata() {
        recordMessageDispatchRouteFailureLog(
            messageDispatchRouteFailureLog(
                messageId = MessageDraftId("pending_2"),
                channel = MessageChannel.SMS,
                failure = DispatchProviderRetryPolicy.smsPermissionDenied(),
                cause = SecurityException("missing permission"),
            )
        )

        val entry = StructuredLogger.getRecent(1).single()
        assertEquals("MessageDispatcher", entry.tag)
        assertEquals(LogLevel.ERROR, entry.level)
        assertEquals("SMS permission not granted for message pending_2", entry.message)
        assertNull(entry.throwable)
        assertEquals("SecurityException", entry.extras["exception"])
        assertEquals("missing permission", entry.extras["exceptionMessage"])
    }

    @Test
    fun recordMessageDispatchRouteFailureLog_recordsEmailProviderErrorWithCauseMetadata() {
        recordMessageDispatchRouteFailureLog(
            messageDispatchRouteFailureLog(
                messageId = MessageDraftId("pending_3"),
                channel = MessageChannel.EMAIL,
                failure = DispatchProviderRetryPolicy.emailProviderException(
                    MessagingException("timeout", SocketTimeoutException("smtp timeout"))
                ),
                cause = MessagingException("timeout", SocketTimeoutException("smtp timeout")),
            )
        )

        val entry = StructuredLogger.getRecent(1).single()
        assertEquals("MessageDispatcher", entry.tag)
        assertEquals(LogLevel.ERROR, entry.level)
        assertEquals("Email route failed for pending_3; trying next automatic route", entry.message)
        assertNull(entry.throwable)
        assertEquals("MessagingException", entry.extras["exception"])
        assertEquals("timeout", entry.extras["exceptionMessage"])
    }
}
