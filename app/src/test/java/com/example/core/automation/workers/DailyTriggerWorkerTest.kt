package com.example.core.automation.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.core.automation.notifications.NotificationHelper
import com.example.core.automation.scheduler.WorkerScheduler
import com.example.core.prefs.SecurePrefs
import com.example.domain.service.EventReminderSchedulerService
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
class DailyTriggerWorkerTest {

    private lateinit var context: Context
    private val prefs: SecurePrefs = mockk(relaxed = true)
    private val eventReminderSchedulerService: EventReminderSchedulerService = mockk(relaxed = true)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockkObject(WorkerScheduler)
        mockkObject(NotificationHelper)
        every { WorkerScheduler.scheduleDailyAutomationChain(any()) } just Runs
        every { NotificationHelper.showSystemAlert(any(), any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `doWork schedules daily chain and returns success`() = runTest {
        val worker = dailyTriggerWorker()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        verify { WorkerScheduler.scheduleDailyAutomationChain(any()) }
        verify { eventReminderSchedulerService.rescheduleAll() }
    }

    @Test
    fun `doWork keeps never backed up state separate from reminder baseline`() = runTest {
        every { prefs.getLastBackupMs() } returns 0L
        every { prefs.getLastBackupReminderMs() } returns 0L
        val worker = dailyTriggerWorker()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        verify(exactly = 0) { prefs.setLastBackupMs(any()) }
        verify { prefs.setLastBackupReminderMs(any()) }
        verify(exactly = 0) { NotificationHelper.showSystemAlert(any(), any(), any()) }
    }

    @Test
    fun `doWork shows backup reminder when exported backup is stale`() = runTest {
        every { prefs.getLastBackupMs() } returns System.currentTimeMillis() - 31L * DAY_MS
        every { prefs.getLastBackupReminderMs() } returns 0L
        val worker = dailyTriggerWorker()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        verify { NotificationHelper.showSystemAlert(any(), any(), any()) }
        verify { prefs.setLastBackupReminderMs(any()) }
    }

    private fun dailyTriggerWorker(): DailyTriggerWorker {
        return TestListenableWorkerBuilder<DailyTriggerWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker {
                    return DailyTriggerWorker(
                        appContext,
                        workerParameters,
                        prefs,
                        eventReminderSchedulerService,
                    )
                }
            })
            .build()
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000L
    }
}
