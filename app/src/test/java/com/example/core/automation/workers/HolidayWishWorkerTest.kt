package com.example.core.automation.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.example.core.automation.notifications.NotificationHelper
import com.example.core.automation.scheduler.DailyScheduler
import com.example.core.db.dao.ContactDao
import com.example.core.db.dao.EventDao
import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.dao.SentMessageDao
import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.PendingMessageEntity
import com.example.core.gemini.GeminiClient
import com.example.core.gemini.RateLimiter
import com.example.core.prefs.SecurePrefs
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageStatus
import com.example.domain.model.notification.ApprovalNotificationRequest
import com.example.domain.model.occasion.OccasionType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
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
class HolidayWishWorkerTest {

    private lateinit var context: Context
    private val contactDao: ContactDao = mockk(relaxed = true)
    private val eventDao: EventDao = mockk(relaxed = true)
    private val pendingMessageDao: PendingMessageDao = mockk(relaxed = true)
    private val sentMessageDao: SentMessageDao = mockk(relaxed = true)
    private val geminiClient: GeminiClient = mockk(relaxed = true)
    private val prefs: SecurePrefs = mockk(relaxed = true)
    private val nowMs = calendarMs(2026, Calendar.DECEMBER, 28, 10, 0)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockkStatic(FirebaseAuth::class)
        mockkObject(RateLimiter)
        mockkObject(DailyScheduler)
        mockkObject(NotificationHelper)

        val mockAuth = mockk<FirebaseAuth>()
        val mockUser = mockk<FirebaseUser>()
        every { FirebaseAuth.getInstance() } returns mockAuth
        every { mockAuth.currentUser } returns mockUser

        every { prefs.getGeminiApiKey() } returns "test_api_key"
        every { prefs.isAiWishGenerationEnabled() } returns true
        every { prefs.getGlobalApprovalMode() } returns ApprovalMode.SMART_APPROVE
        every { prefs.getQuietHoursStart() } returns 0
        every { prefs.getQuietHoursEnd() } returns 0
        every { prefs.getBlackoutDates() } returns "[]"

        coEvery { RateLimiter.waitIfNeeded() } returns Unit
        coEvery { sentMessageDao.getByContact(any()) } returns emptyList()
        every { DailyScheduler.scheduleExactSend(any(), any()) } just Runs
        every { NotificationHelper.showApprovalNotification(any(), any(), any()) } just Runs
        every { NotificationHelper.showSetupNotification(any(), any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `doWork creates fully auto fixed-date holiday wish without review notification`() = runTest {
        val contact = contact(automationMode = "FULLY_AUTO")
        val pendingSlot = slot<PendingMessageEntity>()

        coEvery { contactDao.getAllSync() } returns listOf(contact)
        coEvery { pendingMessageDao.getByEventId("HOLIDAY_NEW_YEAR_c1_2027") } returns null
        coEvery { geminiClient.generate(any()) } returns "Happy New Year Amit, hope this year brings more cricket nights and relaxed coffee catchups."

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { pendingMessageDao.insert(capture(pendingSlot)) }
        assertEquals("HOLIDAY_NEW_YEAR_c1_2027", pendingSlot.captured.eventId)
        assertEquals("FULLY_AUTO", pendingSlot.captured.approvalMode)
        assertEquals(MessageStatus.APPROVED.raw, pendingSlot.captured.status)
        assertEquals(100, pendingSlot.captured.qualityScore)
        assertEquals(2027, pendingSlot.captured.scheduledYear)
        coVerify {
            eventDao.upsert(match {
                it.id == "HOLIDAY_NEW_YEAR_c1_2027" &&
                    it.type == OccasionType.HOLIDAY.raw &&
                    it.label == "New Year" &&
                    it.source == "AI_INFERRED"
            })
        }
        verify { DailyScheduler.scheduleExactSend(any(), pendingSlot.captured.id) }
        verify(exactly = 0) { NotificationHelper.showApprovalNotification(any(), any(), any()) }
    }

    @Test
    fun `doWork downgrades fallback fully auto holiday wish to smart approve`() = runTest {
        val contact = contact(automationMode = "FULLY_AUTO")
        val pendingSlot = slot<PendingMessageEntity>()

        coEvery { contactDao.getAllSync() } returns listOf(contact)
        coEvery { pendingMessageDao.getByEventId("HOLIDAY_NEW_YEAR_c1_2027") } returns null
        coEvery { geminiClient.generate(any()) } returns ""

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { pendingMessageDao.insert(capture(pendingSlot)) }
        assertEquals("SMART_APPROVE", pendingSlot.captured.approvalMode)
        assertEquals(MessageStatus.PENDING.raw, pendingSlot.captured.status)
        assertEquals(35, pendingSlot.captured.qualityScore)
        assertEquals(true, pendingSlot.captured.isUsingFallback)
        verify { DailyScheduler.scheduleExactSend(any(), pendingSlot.captured.id) }
        verify {
            NotificationHelper.showApprovalNotification(
                any(),
                match<ApprovalNotificationRequest> {
                    it.contactId.value == contact.id &&
                        it.eventId.value == "HOLIDAY_NEW_YEAR_c1_2027" &&
                        it.messageId.value == pendingSlot.captured.id
                },
                any(),
            )
        }
    }

    @Test
    fun `doWork forces review and skips scheduling holiday wish when no route is available`() = runTest {
        val contact = contact(automationMode = "FULLY_AUTO", primaryPhone = null)
        val pendingSlot = slot<PendingMessageEntity>()

        coEvery { contactDao.getAllSync() } returns listOf(contact)
        coEvery { pendingMessageDao.getByEventId("HOLIDAY_NEW_YEAR_c1_2027") } returns null
        coEvery { geminiClient.generate(any()) } returns "Happy New Year Amit, hope the year brings more cricket nights and relaxed coffee catchups."

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { pendingMessageDao.insert(capture(pendingSlot)) }
        assertEquals("ALWAYS_ASK", pendingSlot.captured.approvalMode)
        assertEquals(MessageStatus.PENDING.raw, pendingSlot.captured.status)
        assertEquals(MessageChannel.SMS.raw, pendingSlot.captured.channel)
        verify(exactly = 0) { DailyScheduler.scheduleExactSend(any(), any()) }
        verify {
            NotificationHelper.showApprovalNotification(
                any(),
                match<ApprovalNotificationRequest> {
                    it.contactId.value == contact.id &&
                        it.eventId.value == "HOLIDAY_NEW_YEAR_c1_2027" &&
                        it.messageId.value == pendingSlot.captured.id
                },
                any(),
            )
        }
    }

    @Test
    fun `doWork skips holiday wish when deterministic pending already exists`() = runTest {
        val contact = contact(automationMode = "SMART_APPROVE")
        val existing = PendingMessageEntity(
            id = "pending1",
            contactId = "c1",
            eventId = "HOLIDAY_NEW_YEAR_c1_2027",
            shortVariant = "Already queued",
            standardVariant = "Already queued",
            longVariant = "Already queued",
            formalVariant = "Already queued",
            funnyVariant = "Already queued",
            emotionalVariant = "Already queued",
            channel = MessageChannel.SMS.raw,
            scheduledForMs = nowMs,
            approvalMode = "SMART_APPROVE",
        )

        coEvery { contactDao.getAllSync() } returns listOf(contact)
        coEvery { pendingMessageDao.getByEventId("HOLIDAY_NEW_YEAR_c1_2027") } returns existing

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { geminiClient.generate(any()) }
        coVerify(exactly = 0) { eventDao.upsert(any()) }
        coVerify(exactly = 0) { pendingMessageDao.insert(any()) }
    }

    private fun worker(): HolidayWishWorker {
        return TestListenableWorkerBuilder<HolidayWishWorker>(context)
            .setInputData(workDataOf(HolidayWishWorker.KEY_NOW_MS to nowMs))
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker {
                    return HolidayWishWorker(
                        appContext,
                        workerParameters,
                        contactDao,
                        eventDao,
                        pendingMessageDao,
                        sentMessageDao,
                        geminiClient,
                        prefs,
                    )
                }
            })
            .build()
    }

    private fun contact(
        automationMode: String,
        primaryPhone: String? = "+15551234567",
    ): ContactEntity {
        return ContactEntity(
            id = "c1",
            name = "Amit Shah",
            relationshipType = "FRIEND",
            primaryPhone = primaryPhone,
            preferredChannel = MessageChannel.SMS.raw,
            automationMode = automationMode,
            interestsJson = "[\"cricket\",\"coffee\"]",
        )
    }

    private companion object {
        fun calendarMs(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
            return Calendar.getInstance().apply {
                clear()
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, day)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
            }.timeInMillis
        }
    }
}
