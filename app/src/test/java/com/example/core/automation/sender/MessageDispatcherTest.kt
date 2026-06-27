package com.example.core.automation.sender

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.accessibility.WhatsAppSendFailureReason
import com.example.core.accessibility.WhatsAppSendResult
import com.example.core.automation.notifications.NotificationHelper
import com.example.core.data.R
import com.example.core.db.dao.ContactDao
import com.example.core.db.dao.DispatchAttemptDao
import com.example.core.db.dao.EventDao
import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.dao.SentMessageDao
import com.example.core.db.entities.EventEntity
import com.example.core.db.entities.SentMessageEntity
import com.example.core.prefs.SecurePrefs
import com.example.core.resilience.DeadLetterQueue
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.DispatchAttemptId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.dispatch.DispatchAttemptResult
import com.example.domain.model.dispatch.MessageDispatchRequest
import com.example.domain.model.occasion.OccasionType
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
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
class MessageDispatcherTest {

    private lateinit var context: Context
    private val pendingMessageDao: PendingMessageDao = mockk(relaxed = true)
    private val sentMessageDao: SentMessageDao = mockk(relaxed = true)
    private val contactDao: ContactDao = mockk(relaxed = true)
    private val eventDao: EventDao = mockk(relaxed = true)
    private val dispatchAttemptDao: DispatchAttemptDao = mockk(relaxed = true)
    private val sentSlot = slot<SentMessageEntity>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        DeadLetterQueue.clear()
        mockkObject(NotificationHelper)
        mockkConstructor(SecurePrefs::class)
        mockkConstructor(EmailSender::class)
        mockkConstructor(SmsSender::class)
        mockkConstructor(WhatsAppSender::class)

        every { anyConstructed<SecurePrefs>().getChannelBlackout() } returns "[]"
        every { anyConstructed<SecurePrefs>().isWhatsAppAutomationConsentGranted() } returns true
        every { anyConstructed<SecurePrefs>().getSenderEmail() } returns ""
        every { anyConstructed<SecurePrefs>().getSenderEmailPassword() } returns ""
        coEvery { anyConstructed<EmailSender>().send(any(), any(), any(), any(), any(), any()) } returns Unit
        coEvery { anyConstructed<WhatsAppSender>().sendWithResult(any(), any(), any()) } returns WhatsAppSendResult.Sent
        every { anyConstructed<SmsSender>().send(any(), any(), any()) } just Runs
        every { NotificationHelper.showSetupNotification(any(), any(), any()) } just Runs
        coEvery { sentMessageDao.insert(capture(sentSlot)) } just Runs
    }

    @After
    fun tearDown() {
        DeadLetterQueue.clear()
        unmockkAll()
    }

    @Test
    fun `dispatch stores resolved event id separately from occasion type`() = runTest {
        val event = EventEntity(
            id = "event_1",
            contactId = "contact_1",
            type = OccasionType.ANNIVERSARY.raw,
            label = "Wedding anniversary",
            dayOfMonth = 12,
            month = 6,
            nextOccurrenceMs = 1800000000000,
        )
        coEvery { eventDao.getById("event_1") } returns event

        dispatcher().dispatch(dispatchRequest(eventId = "event_1", dispatchAttemptId = "attempt_1"))

        assertEquals("event_1", sentSlot.captured.eventId)
        assertEquals(OccasionType.ANNIVERSARY.raw, sentSlot.captured.occasionType)
        assertEquals(OccasionType.ANNIVERSARY.raw, sentSlot.captured.eventType)
        assertEquals("Wedding anniversary", sentSlot.captured.occasionLabel)
        coVerify {
            dispatchAttemptDao.updateOutcome(
                id = "attempt_1",
                attemptedAtMs = any(),
                resolvedAtMs = any(),
                result = DispatchAttemptResult.PENDING_DELIVERY.raw,
                channel = MessageChannel.SMS.raw,
                deliveryStatus = MessageDeliveryStatus.PENDING_DELIVERY.raw,
                providerMessageId = null,
                errorType = null,
                errorCode = null,
                redactedErrorMessage = null,
                retryCount = 0,
                nextRetryAtMs = null,
                deadLetteredAtMs = null,
            )
        }
        coVerify { pendingMessageDao.updateStatus("pending_1", "SENT") }
        coVerify { contactDao.updateLastWished("contact_1", any()) }
        coVerify { contactDao.incrementConsecutiveYearsWished("contact_1") }
        coVerify { contactDao.updateHealthScoreDelta("contact_1", 5) }
    }

    @Test
    fun `dispatch classifies unresolved synthetic event refs without event id`() = runTest {
        coEvery { eventDao.getById("FOLLOWUP_sent_1") } returns null

        dispatcher().dispatch(dispatchRequest(eventId = "FOLLOWUP_sent_1"))

        assertNull(sentSlot.captured.eventId)
        assertEquals(OccasionType.FOLLOW_UP.raw, sentSlot.captured.occasionType)
        assertEquals(OccasionType.FOLLOW_UP.raw, sentSlot.captured.eventType)
        assertNull(sentSlot.captured.occasionLabel)
    }

    @Test
    fun `dispatch marks attempt failed when no automatic delivery route exists`() = runTest {
        dispatcher().dispatch(dispatchRequest(eventId = "event_1", primaryPhone = null, dispatchAttemptId = "attempt_2"))

        coVerify { pendingMessageDao.updateStatus("pending_1", "FAILED") }
        coVerify {
            dispatchAttemptDao.updateOutcome(
                id = "attempt_2",
                attemptedAtMs = any(),
                resolvedAtMs = any(),
                result = DispatchAttemptResult.FAILED_FINAL.raw,
                channel = MessageChannel.SMS.raw,
                deliveryStatus = MessageDeliveryStatus.FAILED.raw,
                providerMessageId = null,
                errorType = "NO_DELIVERY_ROUTE",
                errorCode = null,
                redactedErrorMessage = "All automatic delivery routes failed.",
                retryCount = 0,
                nextRetryAtMs = null,
                deadLetteredAtMs = any(),
            )
        }
        val deadLetter = DeadLetterQueue.getAll().single()
        assertEquals("pending_1", deadLetter.id)
        assertEquals("Selected", deadLetter.payload)
        assertEquals("All automatic delivery routes failed.", deadLetter.errorMessage)
        assertEquals("NO_DELIVERY_ROUTE", deadLetter.errorType)
        assertEquals(0, deadLetter.retryCount)
    }

    @Test
    fun `dispatch marks attempt retryable when sms provider fails transiently`() = runTest {
        every { anyConstructed<SecurePrefs>().getChannelBlackout() } returns "[\"WHATSAPP\"]"
        every { anyConstructed<SmsSender>().send(any(), any(), any()) } throws RuntimeException("radio unavailable")

        dispatcher().dispatch(dispatchRequest(eventId = "event_1", dispatchAttemptId = "attempt_3"))

        coVerify { sentMessageDao.updateDeliveryStatus(any(), MessageDeliveryStatus.FAILED.raw) }
        coVerify { pendingMessageDao.updateStatus("pending_1", "FAILED") }
        coVerify {
            dispatchAttemptDao.updateOutcome(
                id = "attempt_3",
                attemptedAtMs = any(),
                resolvedAtMs = any(),
                result = DispatchAttemptResult.FAILED_RETRYABLE.raw,
                channel = MessageChannel.SMS.raw,
                deliveryStatus = MessageDeliveryStatus.FAILED.raw,
                providerMessageId = null,
                errorType = DispatchProviderRetryPolicy.ERROR_SMS_TRANSIENT_PROVIDER_FAILURE,
                errorCode = "RuntimeException",
                redactedErrorMessage = "SMS provider failed before delivery confirmation; retry is allowed.",
                retryCount = 0,
                nextRetryAtMs = any(),
                deadLetteredAtMs = null,
            )
        }
        assertEquals(0, DeadLetterQueue.count())
    }

    @Test
    fun `dispatch shows setup notification when sms permission is missing`() = runTest {
        every { anyConstructed<SecurePrefs>().getChannelBlackout() } returns "[\"WHATSAPP\"]"
        every { anyConstructed<SmsSender>().send(any(), any(), any()) } throws SecurityException("missing permission")

        dispatcher().dispatch(dispatchRequest(eventId = "event_1", dispatchAttemptId = "attempt_4"))

        verify {
            NotificationHelper.showSetupNotification(
                context,
                context.getString(R.string.notification_setup_sms_permission_title),
                context.getString(R.string.notification_setup_sms_permission_message, "Amit"),
            )
        }
        coVerify { sentMessageDao.updateDeliveryStatus(any(), MessageDeliveryStatus.FAILED.raw) }
        coVerify { pendingMessageDao.updateStatus("pending_1", "FAILED") }
        coVerify {
            dispatchAttemptDao.updateOutcome(
                id = "attempt_4",
                attemptedAtMs = any(),
                resolvedAtMs = any(),
                result = DispatchAttemptResult.FAILED_FINAL.raw,
                channel = MessageChannel.SMS.raw,
                deliveryStatus = MessageDeliveryStatus.FAILED.raw,
                providerMessageId = null,
                errorType = DispatchProviderRetryPolicy.ERROR_SMS_PERMISSION_DENIED,
                errorCode = "ANDROID_SEND_SMS_PERMISSION",
                redactedErrorMessage = "SMS permission is missing; automatic SMS cannot be sent.",
                retryCount = 0,
                nextRetryAtMs = null,
                deadLetteredAtMs = any(),
            )
        }
    }

    @Test
    fun `dispatch falls back to sms when whatsapp automation is unavailable`() = runTest {
        coEvery {
            anyConstructed<WhatsAppSender>().sendWithResult(any(), any(), any())
        } returns WhatsAppSendResult.Failed(WhatsAppSendFailureReason.SERVICE_DISABLED)

        dispatcher().dispatch(
            dispatchRequest(
                eventId = "event_1",
                preferredChannel = MessageChannel.WHATSAPP,
                dispatchAttemptId = "attempt_5",
            )
        )

        assertEquals(MessageChannel.SMS.raw, sentSlot.captured.channel)
        assertEquals(MessageDeliveryStatus.PENDING_DELIVERY.raw, sentSlot.captured.deliveryStatus)
        coVerify {
            dispatchAttemptDao.updateOutcome(
                id = "attempt_5",
                attemptedAtMs = any(),
                resolvedAtMs = any(),
                result = DispatchAttemptResult.PENDING_DELIVERY.raw,
                channel = MessageChannel.SMS.raw,
                deliveryStatus = MessageDeliveryStatus.PENDING_DELIVERY.raw,
                providerMessageId = null,
                errorType = null,
                errorCode = null,
                redactedErrorMessage = null,
                retryCount = 0,
                nextRetryAtMs = null,
                deadLetteredAtMs = null,
            )
        }
        coVerify { pendingMessageDao.updateStatus("pending_1", "SENT") }
        assertEquals(0, DeadLetterQueue.count())
    }

    @Test
    fun `dispatch falls back to sms when whatsapp consent is missing`() = runTest {
        every { anyConstructed<SecurePrefs>().isWhatsAppAutomationConsentGranted() } returns false

        dispatcher().dispatch(
            dispatchRequest(
                eventId = "event_1",
                preferredChannel = MessageChannel.WHATSAPP,
                dispatchAttemptId = "attempt_5_consent",
            )
        )

        coVerify(exactly = 0) {
            anyConstructed<WhatsAppSender>().sendWithResult(any(), any(), any())
        }
        assertEquals(MessageChannel.SMS.raw, sentSlot.captured.channel)
        coVerify {
            dispatchAttemptDao.updateOutcome(
                id = "attempt_5_consent",
                attemptedAtMs = any(),
                resolvedAtMs = any(),
                result = DispatchAttemptResult.PENDING_DELIVERY.raw,
                channel = MessageChannel.SMS.raw,
                deliveryStatus = MessageDeliveryStatus.PENDING_DELIVERY.raw,
                providerMessageId = null,
                errorType = null,
                errorCode = null,
                redactedErrorMessage = null,
                retryCount = 0,
                nextRetryAtMs = null,
                deadLetteredAtMs = null,
            )
        }
        assertEquals(0, DeadLetterQueue.count())
    }

    @Test
    fun `dispatch records sent outcome when preferred email succeeds`() = runTest {
        every { anyConstructed<SecurePrefs>().getSenderEmail() } returns "sender@example.com"
        every { anyConstructed<SecurePrefs>().getSenderEmailPassword() } returns "app-password"

        dispatcher().dispatch(
            dispatchRequest(
                eventId = "event_1",
                preferredChannel = MessageChannel.EMAIL,
                primaryEmail = "amit@example.com",
                dispatchAttemptId = "attempt_6",
            )
        )

        assertEquals(MessageChannel.EMAIL.raw, sentSlot.captured.channel)
        assertEquals(MessageDeliveryStatus.SENT.raw, sentSlot.captured.deliveryStatus)
        coVerify {
            dispatchAttemptDao.updateOutcome(
                id = "attempt_6",
                attemptedAtMs = any(),
                resolvedAtMs = any(),
                result = DispatchAttemptResult.SENT.raw,
                channel = MessageChannel.EMAIL.raw,
                deliveryStatus = MessageDeliveryStatus.SENT.raw,
                providerMessageId = null,
                errorType = null,
                errorCode = null,
                redactedErrorMessage = null,
                retryCount = 0,
                nextRetryAtMs = null,
                deadLetteredAtMs = null,
            )
        }
        coVerify { pendingMessageDao.updateStatus("pending_1", "SENT") }
        assertEquals(0, DeadLetterQueue.count())
    }

    private fun dispatcher(): MessageDispatcher {
        return MessageDispatcher(context, pendingMessageDao, sentMessageDao, contactDao, eventDao, dispatchAttemptDao)
    }

    private fun dispatchRequest(
        eventId: String,
        primaryPhone: String? = "+15551234567",
        primaryEmail: String? = null,
        preferredChannel: MessageChannel = MessageChannel.SMS,
        dispatchAttemptId: String? = null,
    ): MessageDispatchRequest {
        return MessageDispatchRequest(
            messageId = MessageDraftId("pending_1"),
            contactId = ContactId("contact_1"),
            occasionReference = OccasionId(eventId),
            preferredChannel = preferredChannel,
            messageText = "Selected",
            contactDisplayName = "Amit",
            primaryPhone = primaryPhone,
            primaryEmail = primaryEmail,
            dispatchAttemptId = dispatchAttemptId?.let(::DispatchAttemptId),
        )
    }
}
