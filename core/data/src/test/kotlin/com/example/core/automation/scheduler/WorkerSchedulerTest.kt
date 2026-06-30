package com.example.core.automation.scheduler

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkContinuation
import androidx.work.WorkManager
import com.example.core.automation.sender.SmsDeliveryStatusRecovery
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, manifest = Config.NONE)
class WorkerSchedulerTest {
    private lateinit var context: Context
    private val workManager: WorkManager = mockk(relaxed = true)
    private val continuation: WorkContinuation = mockk(relaxed = true)
    private val operation: Operation = mockk(relaxed = true)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockkStatic(WorkManager::class)
        mockkObject(EventReminderScheduler)
        mockkObject(ExactSendRecovery)
        mockkObject(SmsDeliveryStatusRecovery)

        every { WorkManager.getInstance(context) } returns workManager
        every {
            workManager.enqueueUniquePeriodicWork(
                any(),
                any(),
                any<PeriodicWorkRequest>(),
            )
        } returns operation
        every {
            workManager.beginUniqueWork(
                any(),
                any(),
                any<OneTimeWorkRequest>(),
            )
        } returns continuation
        every { continuation.then(any<OneTimeWorkRequest>()) } returns continuation
        every { continuation.enqueue() } returns operation
        every { EventReminderScheduler.scheduleAll(any()) } just Runs
        every { ExactSendRecovery.recoverAsync(any()) } just Runs
        every { SmsDeliveryStatusRecovery.recoverAsync(any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `scheduleAll enqueues periodic trigger and immediate automation chain`() {
        WorkerScheduler.scheduleAll(context)

        verify {
            workManager.enqueueUniquePeriodicWork(
                "daily_trigger",
                ExistingPeriodicWorkPolicy.KEEP,
                any<PeriodicWorkRequest>(),
            )
        }
        verify {
            workManager.beginUniqueWork(
                "daily_automation_chain",
                ExistingWorkPolicy.KEEP,
                any<OneTimeWorkRequest>(),
            )
        }
        verify { ExactSendRecovery.recoverAsync(context) }
        verify { SmsDeliveryStatusRecovery.recoverAsync(context) }
        verify { continuation.enqueue() }
        verify { EventReminderScheduler.scheduleAll(context) }
    }
}
