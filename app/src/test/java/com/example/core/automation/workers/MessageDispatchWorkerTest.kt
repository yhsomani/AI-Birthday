package com.example.core.automation.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.example.core.automation.sender.MessageDispatcher
import com.example.core.db.dao.ContactDao
import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.dao.SentMessageDao
import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.PendingMessageEntity
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MessageDispatchWorkerTest {

    private lateinit var context: Context
    private val pendingMessageDao: PendingMessageDao = mockk(relaxed = true)
    private val sentMessageDao: SentMessageDao = mockk(relaxed = true)
    private val contactDao: ContactDao = mockk(relaxed = true)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockkConstructor(MessageDispatcher::class)
        coEvery { anyConstructed<MessageDispatcher>().dispatch(any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `doWork double-send guard exits successfully on dispatching status`() = runTest {
        val pendingMsg = PendingMessageEntity(
            id = "msg_1", contactId = "c1", eventId = "e1",
            shortVariant = "", standardVariant = "", longVariant = "",
            formalVariant = "", funnyVariant = "", emotionalVariant = "",
            selectedVariant = "standard", selectedVariantText = "",
            channel = "SMS", scheduledForMs = 0, approvalMode = "MANUAL",
            status = "DISPATCHING"
        )
        val contact = ContactEntity(id = "c1", name = "Alice")

        coEvery { pendingMessageDao.getById("msg_1") } returns pendingMsg
        coEvery { contactDao.getById("c1") } returns contact

        val worker = TestListenableWorkerBuilder<MessageDispatchWorker>(context)
            .setInputData(workDataOf(MessageDispatchWorkRequests.KEY_PENDING_MESSAGE_ID to "msg_1"))
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker {
                    return MessageDispatchWorker(appContext, workerParameters, pendingMessageDao, sentMessageDao, contactDao)
                }
            })
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `doWork sends message and returns success on APPROVED status`() = runTest {
        val pendingMsg = PendingMessageEntity(
            id = "msg_1", contactId = "c1", eventId = "e1",
            shortVariant = "", standardVariant = "Happy Birthday", longVariant = "",
            formalVariant = "", funnyVariant = "", emotionalVariant = "",
            selectedVariant = "standard", selectedVariantText = "Happy Birthday",
            channel = "SMS", scheduledForMs = 0, approvalMode = "MANUAL",
            status = "APPROVED"
        )
        val contact = ContactEntity(id = "c1", name = "Alice")

        coEvery { pendingMessageDao.getById("msg_1") } returns pendingMsg
        coEvery { contactDao.getById("c1") } returns contact

        val worker = TestListenableWorkerBuilder<MessageDispatchWorker>(context)
            .setInputData(workDataOf(MessageDispatchWorkRequests.KEY_PENDING_MESSAGE_ID to "msg_1"))
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker {
                    return MessageDispatchWorker(appContext, workerParameters, pendingMessageDao, sentMessageDao, contactDao)
                }
            })
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { pendingMessageDao.updateStatus("msg_1", "DISPATCHING") }
        coVerify { anyConstructed<MessageDispatcher>().dispatch(pendingMsg, contact) }
    }
}
