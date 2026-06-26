package com.example.core.automation.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.example.core.automation.sender.MessageDispatcher
import com.example.core.automation.scheduler.DailyScheduler
import com.example.core.db.dao.ContactDao
import com.example.core.db.dao.EventDao
import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.dao.SentMessageDao
import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.PendingMessageEntity
import com.example.domain.model.MessageChannel
import com.example.domain.service.PreferencesRepository
import io.mockk.*
import java.util.Calendar
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
    private val eventDao: EventDao = mockk(relaxed = true)
    private val preferencesRepository: PreferencesRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockkConstructor(MessageDispatcher::class)
        mockkObject(DailyScheduler)
        coEvery { anyConstructed<MessageDispatcher>().dispatch(any(), any()) } returns Unit
        every { DailyScheduler.scheduleExactSend(any(), any()) } just Runs
        every { preferencesRepository.getQuietHoursStart() } returns 0
        every { preferencesRepository.getQuietHoursEnd() } returns 0
        every { preferencesRepository.getBlackoutDates() } returns "[]"
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
            channel = MessageChannel.SMS.raw, scheduledForMs = 0, approvalMode = "MANUAL",
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
                    return MessageDispatchWorker(appContext, workerParameters, pendingMessageDao, sentMessageDao, contactDao, eventDao, preferencesRepository)
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
            channel = MessageChannel.SMS.raw, scheduledForMs = 0, approvalMode = "MANUAL",
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
                    return MessageDispatchWorker(appContext, workerParameters, pendingMessageDao, sentMessageDao, contactDao, eventDao, preferencesRepository)
                }
            })
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { pendingMessageDao.updateStatus("msg_1", "DISPATCHING") }
        coVerify { anyConstructed<MessageDispatcher>().dispatch(pendingMsg, contact) }
    }

    @Test
    fun `doWork defers future approved message without dispatching`() = runTest {
        val scheduledForMs = System.currentTimeMillis() + 60_000L
        val pendingMsg = PendingMessageEntity(
            id = "msg_1", contactId = "c1", eventId = "e1",
            shortVariant = "", standardVariant = "Happy Birthday", longVariant = "",
            formalVariant = "", funnyVariant = "", emotionalVariant = "",
            selectedVariant = "standard", selectedVariantText = "Happy Birthday",
            channel = MessageChannel.SMS.raw, scheduledForMs = scheduledForMs, approvalMode = "FULLY_AUTO",
            status = "APPROVED"
        )
        val contact = ContactEntity(id = "c1", name = "Alice")

        coEvery { pendingMessageDao.getById("msg_1") } returns pendingMsg
        coEvery { contactDao.getById("c1") } returns contact

        val worker = buildWorker("msg_1")

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        verify { DailyScheduler.scheduleExactSend(any(), "msg_1") }
        coVerify(exactly = 0) { pendingMessageDao.updateStatus("msg_1", "DISPATCHING") }
        coVerify(exactly = 0) { anyConstructed<MessageDispatcher>().dispatch(any(), any()) }
    }

    @Test
    fun `doWork sends due smart approve pending message`() = runTest {
        val pendingMsg = PendingMessageEntity(
            id = "msg_1", contactId = "c1", eventId = "e1",
            shortVariant = "", standardVariant = "Happy Birthday", longVariant = "",
            formalVariant = "", funnyVariant = "", emotionalVariant = "",
            selectedVariant = "standard", selectedVariantText = "Happy Birthday",
            channel = MessageChannel.SMS.raw, scheduledForMs = 0, approvalMode = "SMART_APPROVE",
            status = "PENDING"
        )
        val contact = ContactEntity(id = "c1", name = "Alice")

        coEvery { pendingMessageDao.getById("msg_1") } returns pendingMsg
        coEvery { contactDao.getById("c1") } returns contact

        val worker = buildWorker("msg_1")

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { pendingMessageDao.updateStatus("msg_1", "DISPATCHING") }
        coVerify { anyConstructed<MessageDispatcher>().dispatch(pendingMsg, contact) }
    }

    @Test
    fun `doWork expires overdue vip approve pending message`() = runTest {
        val pendingMsg = PendingMessageEntity(
            id = "msg_1", contactId = "c1", eventId = "e1",
            shortVariant = "", standardVariant = "Happy Birthday", longVariant = "",
            formalVariant = "", funnyVariant = "", emotionalVariant = "",
            selectedVariant = "standard", selectedVariantText = "Happy Birthday",
            channel = MessageChannel.SMS.raw,
            scheduledForMs = System.currentTimeMillis() - (3 * 60 * 60 * 1000L),
            approvalMode = "VIP_APPROVE",
            status = "PENDING"
        )
        val contact = ContactEntity(id = "c1", name = "Alice")

        coEvery { pendingMessageDao.getById("msg_1") } returns pendingMsg
        coEvery { contactDao.getById("c1") } returns contact

        val worker = buildWorker("msg_1")

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { pendingMessageDao.updateStatus("msg_1", "EXPIRED") }
        coVerify(exactly = 0) { anyConstructed<MessageDispatcher>().dispatch(any(), any()) }
    }

    @Test
    fun `doWork marks message failed when dispatcher throws unexpectedly`() = runTest {
        val pendingMsg = PendingMessageEntity(
            id = "msg_1", contactId = "c1", eventId = "e1",
            shortVariant = "", standardVariant = "Happy Birthday", longVariant = "",
            formalVariant = "", funnyVariant = "", emotionalVariant = "",
            selectedVariant = "standard", selectedVariantText = "Happy Birthday",
            channel = MessageChannel.SMS.raw, scheduledForMs = 0, approvalMode = "MANUAL",
            status = "APPROVED"
        )
        val contact = ContactEntity(id = "c1", name = "Alice")

        coEvery { pendingMessageDao.getById("msg_1") } returns pendingMsg
        coEvery { contactDao.getById("c1") } returns contact
        coEvery { anyConstructed<MessageDispatcher>().dispatch(pendingMsg, contact) } throws
            IllegalStateException("dispatcher crashed")

        val worker = TestListenableWorkerBuilder<MessageDispatchWorker>(context)
            .setInputData(workDataOf(MessageDispatchWorkRequests.KEY_PENDING_MESSAGE_ID to "msg_1"))
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker {
                    return MessageDispatchWorker(appContext, workerParameters, pendingMessageDao, sentMessageDao, contactDao, eventDao, preferencesRepository)
                }
            })
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        coVerify { pendingMessageDao.updateStatus("msg_1", "DISPATCHING") }
        coVerify { pendingMessageDao.updateStatus("msg_1", "FAILED") }
    }

    @Test
    fun `doWork defers approved message during quiet hours`() = runTest {
        val nowHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        every { preferencesRepository.getQuietHoursStart() } returns nowHour
        every { preferencesRepository.getQuietHoursEnd() } returns ((nowHour + 1) % 24)
        val pendingMsg = PendingMessageEntity(
            id = "msg_1", contactId = "c1", eventId = "e1",
            shortVariant = "", standardVariant = "Happy Birthday", longVariant = "",
            formalVariant = "", funnyVariant = "", emotionalVariant = "",
            selectedVariant = "standard", selectedVariantText = "Happy Birthday",
            channel = MessageChannel.SMS.raw, scheduledForMs = 0, approvalMode = "MANUAL",
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
                    return MessageDispatchWorker(appContext, workerParameters, pendingMessageDao, sentMessageDao, contactDao, eventDao, preferencesRepository)
                }
            })
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { pendingMessageDao.insert(match { it.id == "msg_1" && it.scheduledForMs > System.currentTimeMillis() }) }
        verify { DailyScheduler.scheduleExactSend(any(), "msg_1") }
        coVerify(exactly = 0) { pendingMessageDao.updateStatus("msg_1", "DISPATCHING") }
        coVerify(exactly = 0) { anyConstructed<MessageDispatcher>().dispatch(any(), any()) }
    }

    private fun buildWorker(pendingMessageId: String): MessageDispatchWorker {
        return TestListenableWorkerBuilder<MessageDispatchWorker>(context)
            .setInputData(workDataOf(MessageDispatchWorkRequests.KEY_PENDING_MESSAGE_ID to pendingMessageId))
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker {
                    return MessageDispatchWorker(appContext, workerParameters, pendingMessageDao, sentMessageDao, contactDao, eventDao, preferencesRepository)
                }
            })
            .build()
    }
}
