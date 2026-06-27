package com.example.core.automation.sender

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.automation.notifications.NotificationHelper
import com.example.core.data.R
import com.example.core.db.dao.SentMessageDao
import com.example.core.db.entities.SentMessageEntity
import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.common.SentMessageId
import com.example.domain.model.dispatch.MessageDispatchOccasion
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
class MessageDispatcherSmsRouteAdaptersTest {
    private lateinit var context: Context
    private val sentMessageDao: SentMessageDao = mockk(relaxed = true)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockkConstructor(SmsSender::class)
        mockkObject(NotificationHelper)
        every { NotificationHelper.showSetupNotification(any(), any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun dispatchSmsRouteWithSentMessageRecord_insertsPendingRecordAndReturnsSentOutcome() = runTest {
        val sentSlot = slot<SentMessageEntity>()
        coEvery { sentMessageDao.insert(capture(sentSlot)) } just Runs
        every {
            anyConstructed<SmsSender>().send("+15551234567", "Selected", "sent_sms")
        } just Runs

        val outcome = context.dispatchSmsRouteWithSentMessageRecord(
            sentMessageDao = sentMessageDao,
            messageId = MessageDraftId("pending_1"),
            contactId = ContactId("contact_1"),
            dispatchOccasion = dispatchOccasion(),
            phoneNumber = "+15551234567",
            contactDisplayName = "Amit",
            messageText = "Selected",
            sentMessageId = SentMessageId("sent_sms"),
            eventYear = 2026,
            sentAtMs = 1_800_000_000_000L,
        )

        assertTrue(outcome.sent)
        assertTrue(outcome.successfulSentMessageInserted)
        assertNull(outcome.failure)
        assertEquals("sent_sms", sentSlot.captured.id)
        assertEquals("contact_1", sentSlot.captured.contactId)
        assertEquals("event_1", sentSlot.captured.eventId)
        assertEquals(OccasionType.BIRTHDAY.raw, sentSlot.captured.occasionType)
        assertEquals("Birthday", sentSlot.captured.occasionLabel)
        assertEquals(2026, sentSlot.captured.eventYear)
        assertEquals("Selected", sentSlot.captured.messageText)
        assertEquals(MessageDeliveryStatus.PENDING_DELIVERY.raw, sentSlot.captured.deliveryStatus)
        coVerify(exactly = 0) { sentMessageDao.updateDeliveryStatus(any(), any()) }
    }

    @Test
    fun dispatchSmsRouteWithSentMessageRecord_marksInsertedRecordFailedWhenProviderFails() = runTest {
        coEvery { sentMessageDao.insert(any()) } just Runs
        every {
            anyConstructed<SmsSender>().send("+15551234567", "Selected", "sent_sms")
        } throws RuntimeException("radio unavailable")

        val outcome = context.dispatchSmsRouteWithSentMessageRecord(
            sentMessageDao = sentMessageDao,
            messageId = MessageDraftId("pending_1"),
            contactId = ContactId("contact_1"),
            dispatchOccasion = dispatchOccasion(),
            phoneNumber = "+15551234567",
            contactDisplayName = "Amit",
            messageText = "Selected",
            sentMessageId = SentMessageId("sent_sms"),
            eventYear = 2026,
            sentAtMs = 1_800_000_000_000L,
        )

        assertFalse(outcome.sent)
        assertFalse(outcome.successfulSentMessageInserted)
        assertEquals(
            DispatchProviderRetryPolicy.ERROR_SMS_TRANSIENT_PROVIDER_FAILURE,
            outcome.failure?.errorType,
        )
        coVerify {
            sentMessageDao.updateDeliveryStatus(
                "sent_sms",
                MessageDeliveryStatus.FAILED.raw,
            )
        }
    }

    @Test
    fun dispatchSmsRouteWithSentMessageRecord_showsSetupNotificationWhenPermissionIsMissing() = runTest {
        coEvery { sentMessageDao.insert(any()) } just Runs
        every {
            anyConstructed<SmsSender>().send("+15551234567", "Selected", "sent_sms")
        } throws SecurityException("missing permission")

        val outcome = context.dispatchSmsRouteWithSentMessageRecord(
            sentMessageDao = sentMessageDao,
            messageId = MessageDraftId("pending_1"),
            contactId = ContactId("contact_1"),
            dispatchOccasion = dispatchOccasion(),
            phoneNumber = "+15551234567",
            contactDisplayName = "Amit",
            messageText = "Selected",
            sentMessageId = SentMessageId("sent_sms"),
            eventYear = 2026,
            sentAtMs = 1_800_000_000_000L,
        )

        assertFalse(outcome.sent)
        assertEquals(
            DispatchProviderRetryPolicy.ERROR_SMS_PERMISSION_DENIED,
            outcome.failure?.errorType,
        )
        verify {
            NotificationHelper.showSetupNotification(
                context,
                context.getString(R.string.notification_setup_sms_permission_title),
                context.getString(R.string.notification_setup_sms_permission_message, "Amit"),
            )
        }
    }

    @Test
    fun dispatchSmsRouteWithSentMessageRecord_returnsFailureWhenPendingRecordInsertFails() = runTest {
        coEvery { sentMessageDao.insert(any()) } throws RuntimeException("database unavailable")

        val outcome = context.dispatchSmsRouteWithSentMessageRecord(
            sentMessageDao = sentMessageDao,
            messageId = MessageDraftId("pending_1"),
            contactId = ContactId("contact_1"),
            dispatchOccasion = dispatchOccasion(),
            phoneNumber = "+15551234567",
            contactDisplayName = "Amit",
            messageText = "Selected",
            sentMessageId = SentMessageId("sent_sms"),
            eventYear = 2026,
            sentAtMs = 1_800_000_000_000L,
        )

        assertFalse(outcome.sent)
        assertFalse(outcome.successfulSentMessageInserted)
        assertEquals(
            DispatchProviderRetryPolicy.ERROR_SMS_TRANSIENT_PROVIDER_FAILURE,
            outcome.failure?.errorType,
        )
        verify(exactly = 0) { anyConstructed<SmsSender>().send(any(), any(), any()) }
        coVerify(exactly = 0) { sentMessageDao.updateDeliveryStatus(any(), any()) }
    }

    private fun dispatchOccasion(): MessageDispatchOccasion {
        return MessageDispatchOccasion(
            occasionId = OccasionId("event_1"),
            occasionType = OccasionType.BIRTHDAY,
            occasionLabel = "Birthday",
        )
    }
}
