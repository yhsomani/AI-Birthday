package com.example.core.automation.sender

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.db.AppDatabase
import com.example.core.db.dao.DispatchAttemptDao
import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.dao.SentMessageDao
import com.example.core.db.entities.SentMessageEntity
import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.MessageChannel
import com.example.domain.model.dispatch.DispatchAttemptResult
import dagger.hilt.android.EntryPointAccessors
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SmsStatusReceiverTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private val sentMessageDao: SentMessageDao by lazy { db.sentMessageDao() }
    private val dispatchAttemptDao: DispatchAttemptDao = mockk(relaxed = true)
    private val pendingMessageDao: PendingMessageDao = mockk(relaxed = true)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        mockkStatic(EntryPointAccessors::class)
        val mockEntryPoint = mockk<SmsStatusReceiver.SmsStatusReceiverEntryPoint>()
        every {
            EntryPointAccessors.fromApplication(
                any(),
                SmsStatusReceiver.SmsStatusReceiverEntryPoint::class.java
            )
        } returns mockEntryPoint
        every { mockEntryPoint.sentMessageDao() } returns sentMessageDao
        every { mockEntryPoint.dispatchAttemptDao() } returns dispatchAttemptDao
        every { mockEntryPoint.pendingMessageDao() } returns pendingMessageDao
    }

    @After
    fun tearDown() {
        db.close()
        unmockkAll()
    }

    @Test
    fun `test SMS_SENT updates deliveryStatus to SENT on RESULT_OK`() = runTest {
        val messageId = UUID.randomUUID().toString()
        val sentMessage = SentMessageEntity(
            id = messageId,
            contactId = null,
            eventType = "BIRTHDAY",
            eventYear = 2026,
            messageText = "Happy Birthday",
            channel = MessageChannel.SMS.raw,
            sentAtMs = System.currentTimeMillis(),
            deliveryStatus = MessageDeliveryStatus.PENDING_DELIVERY.raw,
            aiGenerated = true
        )
        sentMessageDao.insert(sentMessage)

        val intent = Intent("com.example.SMS_SENT").apply {
            putExtra("sent_message_id", messageId)
            putExtra("dispatch_attempt_id", "attempt_1")
            putExtra("pending_message_id", "pending_1")
        }

        val receiver = SmsStatusReceiver()
        val controller = spyk(receiver)
        every { controller.getResultCode() } returns Activity.RESULT_OK

        controller.onReceive(context, intent)

        var updated: SentMessageEntity? = null
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < 2000) {
            updated = sentMessageDao.getAll().first().find { it.id == messageId }
            if (updated?.deliveryStatus == MessageDeliveryStatus.SENT.raw) break
            Thread.sleep(50)
        }

        assertNotNull(updated)
        assertEquals(MessageDeliveryStatus.SENT.raw, updated!!.deliveryStatus)
        coVerify {
            dispatchAttemptDao.updateSmsCallbackOutcome(
                id = "attempt_1",
                resolvedAtMs = any(),
                result = DispatchAttemptResult.SENT.raw,
                channel = MessageChannel.SMS.raw,
                deliveryStatus = MessageDeliveryStatus.SENT.raw,
                providerMessageId = messageId,
                errorType = null,
                errorCode = null,
                redactedErrorMessage = null,
                deadLetteredAtMs = null,
            )
        }
        coVerify(exactly = 0) { pendingMessageDao.markSmsCallbackFailed(any()) }
    }

    @Test
    fun `test SMS_SENT updates deliveryStatus to FAILED on error`() = runTest {
        val messageId = UUID.randomUUID().toString()
        val sentMessage = SentMessageEntity(
            id = messageId,
            contactId = null,
            eventType = "BIRTHDAY",
            eventYear = 2026,
            messageText = "Happy Birthday",
            channel = MessageChannel.SMS.raw,
            sentAtMs = System.currentTimeMillis(),
            deliveryStatus = MessageDeliveryStatus.PENDING_DELIVERY.raw,
            aiGenerated = true
        )
        sentMessageDao.insert(sentMessage)

        val intent = Intent("com.example.SMS_SENT").apply {
            putExtra("sent_message_id", messageId)
            putExtra("dispatch_attempt_id", "attempt_failed")
            putExtra("pending_message_id", "pending_failed")
        }

        val receiver = SmsStatusReceiver()
        val controller = spyk(receiver)
        every { controller.getResultCode() } returns 5 // Some error code

        controller.onReceive(context, intent)

        var updated: SentMessageEntity? = null
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < 2000) {
            updated = sentMessageDao.getAll().first().find { it.id == messageId }
            if (updated?.deliveryStatus == MessageDeliveryStatus.FAILED.raw) break
            Thread.sleep(50)
        }

        assertNotNull(updated)
        assertEquals(MessageDeliveryStatus.FAILED.raw, updated!!.deliveryStatus)
        coVerify {
            dispatchAttemptDao.updateSmsCallbackOutcome(
                id = "attempt_failed",
                resolvedAtMs = any(),
                result = DispatchAttemptResult.FAILED_FINAL.raw,
                channel = MessageChannel.SMS.raw,
                deliveryStatus = MessageDeliveryStatus.FAILED.raw,
                providerMessageId = messageId,
                errorType = "SMS_SENT_CALLBACK_FAILED",
                errorCode = "5",
                redactedErrorMessage = "Android SMS sent callback reported failure after send handoff.",
                deadLetteredAtMs = any(),
            )
        }
        coVerify { pendingMessageDao.markSmsCallbackFailed("pending_failed") }
    }

    @Test
    fun `test SMS_DELIVERED updates deliveryStatus to DELIVERED on RESULT_OK`() = runTest {
        val messageId = UUID.randomUUID().toString()
        val sentMessage = SentMessageEntity(
            id = messageId,
            contactId = null,
            eventType = "BIRTHDAY",
            eventYear = 2026,
            messageText = "Happy Birthday",
            channel = MessageChannel.SMS.raw,
            sentAtMs = System.currentTimeMillis(),
            deliveryStatus = MessageDeliveryStatus.SENT.raw,
            aiGenerated = true
        )
        sentMessageDao.insert(sentMessage)

        val intent = Intent("com.example.SMS_DELIVERED").apply {
            putExtra("sent_message_id", messageId)
            putExtra("dispatch_attempt_id", "attempt_delivered")
            putExtra("pending_message_id", "pending_delivered")
        }

        val receiver = SmsStatusReceiver()
        val controller = spyk(receiver)
        every { controller.getResultCode() } returns Activity.RESULT_OK

        controller.onReceive(context, intent)

        var updated: SentMessageEntity? = null
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < 2000) {
            updated = sentMessageDao.getAll().first().find { it.id == messageId }
            if (updated?.deliveryStatus == MessageDeliveryStatus.DELIVERED.raw) break
            Thread.sleep(50)
        }

        assertNotNull(updated)
        assertEquals(MessageDeliveryStatus.DELIVERED.raw, updated!!.deliveryStatus)
        coVerify {
            dispatchAttemptDao.updateSmsCallbackOutcome(
                id = "attempt_delivered",
                resolvedAtMs = any(),
                result = DispatchAttemptResult.DELIVERED.raw,
                channel = MessageChannel.SMS.raw,
                deliveryStatus = MessageDeliveryStatus.DELIVERED.raw,
                providerMessageId = messageId,
                errorType = null,
                errorCode = null,
                redactedErrorMessage = null,
                deadLetteredAtMs = null,
            )
        }
        coVerify(exactly = 0) { pendingMessageDao.markSmsCallbackFailed(any()) }
    }
}
