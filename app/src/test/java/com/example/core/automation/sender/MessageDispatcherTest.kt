package com.example.core.automation.sender

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.db.dao.ContactDao
import com.example.core.db.dao.DispatchAttemptDao
import com.example.core.db.dao.EventDao
import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.dao.SentMessageDao
import com.example.core.db.entities.EventEntity
import com.example.core.db.entities.SentMessageEntity
import com.example.core.prefs.SecurePrefs
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
import io.mockk.slot
import io.mockk.unmockkAll
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
        mockkConstructor(SecurePrefs::class)
        mockkConstructor(SmsSender::class)

        every { anyConstructed<SecurePrefs>().getChannelBlackout() } returns "[]"
        every { anyConstructed<SecurePrefs>().getSenderEmail() } returns ""
        every { anyConstructed<SecurePrefs>().getSenderEmailPassword() } returns ""
        every { anyConstructed<SmsSender>().send(any(), any(), any()) } just Runs
        coEvery { sentMessageDao.insert(capture(sentSlot)) } just Runs
    }

    @After
    fun tearDown() {
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
    }

    private fun dispatcher(): MessageDispatcher {
        return MessageDispatcher(context, pendingMessageDao, sentMessageDao, contactDao, eventDao, dispatchAttemptDao)
    }

    private fun dispatchRequest(
        eventId: String,
        primaryPhone: String? = "+15551234567",
        dispatchAttemptId: String? = null,
    ): MessageDispatchRequest {
        return MessageDispatchRequest(
            messageId = MessageDraftId("pending_1"),
            contactId = ContactId("contact_1"),
            occasionReference = OccasionId(eventId),
            preferredChannel = MessageChannel.SMS,
            messageText = "Selected",
            contactDisplayName = "Amit",
            primaryPhone = primaryPhone,
            primaryEmail = null,
            dispatchAttemptId = dispatchAttemptId?.let(::DispatchAttemptId),
        )
    }
}
