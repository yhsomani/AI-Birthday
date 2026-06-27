package com.example.core.automation.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import com.example.domain.usecase.DiscoverEventsUseCase
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EventDiscoveryWorkerTest {

    private lateinit var context: Context
    private val discoverEventsUseCase: DiscoverEventsUseCase = mockk(relaxed = true)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `doWork delegates event discovery to shared use case`() = runTest {
        coEvery { discoverEventsUseCase() } returns DiscoverEventsUseCase.DiscoveryOutcome(
            contacts = 1,
            events = 1,
        )

        val worker = TestListenableWorkerBuilder<EventDiscoveryWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker {
                    return EventDiscoveryWorker(appContext, workerParameters, discoverEventsUseCase)
                }
            })
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { discoverEventsUseCase() }
    }

    @Test
    fun `doWork retries when shared discovery use case fails`() = runTest {
        coEvery { discoverEventsUseCase() } throws IllegalStateException("boom")

        val worker = TestListenableWorkerBuilder<EventDiscoveryWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker {
                    return EventDiscoveryWorker(appContext, workerParameters, discoverEventsUseCase)
                }
            })
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        coVerify(exactly = 1) { discoverEventsUseCase() }
    }
}
