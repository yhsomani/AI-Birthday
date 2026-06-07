package com.example.core.automation.sender

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.db.AppDatabase
import com.example.core.db.dao.SentMessageDao
import com.example.core.db.entities.SentMessageEntity
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
            channel = "SMS",
            sentAtMs = System.currentTimeMillis(),
            deliveryStatus = "PENDING_DELIVERY",
            aiGenerated = true
        )
        sentMessageDao.insert(sentMessage)

        val intent = Intent("com.example.SMS_SENT").apply {
            putExtra("sent_message_id", messageId)
        }

        val receiver = SmsStatusReceiver()
        val controller = spyk(receiver)
        every { controller.getResultCode() } returns Activity.RESULT_OK

        controller.onReceive(context, intent)

        var updated: SentMessageEntity? = null
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < 2000) {
            updated = sentMessageDao.getAll().first().find { it.id == messageId }
            if (updated?.deliveryStatus == "SENT") break
            Thread.sleep(50)
        }

        assertNotNull(updated)
        assertEquals("SENT", updated!!.deliveryStatus)
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
            channel = "SMS",
            sentAtMs = System.currentTimeMillis(),
            deliveryStatus = "PENDING_DELIVERY",
            aiGenerated = true
        )
        sentMessageDao.insert(sentMessage)

        val intent = Intent("com.example.SMS_SENT").apply {
            putExtra("sent_message_id", messageId)
        }

        val receiver = SmsStatusReceiver()
        val controller = spyk(receiver)
        every { controller.getResultCode() } returns 5 // Some error code

        controller.onReceive(context, intent)

        var updated: SentMessageEntity? = null
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < 2000) {
            updated = sentMessageDao.getAll().first().find { it.id == messageId }
            if (updated?.deliveryStatus == "FAILED") break
            Thread.sleep(50)
        }

        assertNotNull(updated)
        assertEquals("FAILED", updated!!.deliveryStatus)
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
            channel = "SMS",
            sentAtMs = System.currentTimeMillis(),
            deliveryStatus = "SENT",
            aiGenerated = true
        )
        sentMessageDao.insert(sentMessage)

        val intent = Intent("com.example.SMS_DELIVERED").apply {
            putExtra("sent_message_id", messageId)
        }

        val receiver = SmsStatusReceiver()
        val controller = spyk(receiver)
        every { controller.getResultCode() } returns Activity.RESULT_OK

        controller.onReceive(context, intent)

        var updated: SentMessageEntity? = null
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < 2000) {
            updated = sentMessageDao.getAll().first().find { it.id == messageId }
            if (updated?.deliveryStatus == "DELIVERED") break
            Thread.sleep(50)
        }

        assertNotNull(updated)
        assertEquals("DELIVERED", updated!!.deliveryStatus)
    }
}
