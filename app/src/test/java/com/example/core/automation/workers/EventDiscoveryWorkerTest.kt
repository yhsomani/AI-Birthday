package com.example.core.automation.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.core.db.dao.ContactDao
import com.example.core.db.dao.EventDao
import com.example.core.db.entities.ContactEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
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
    private val contactDao: ContactDao = mockk(relaxed = true)
    private val eventDao: EventDao = mockk(relaxed = true)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `doWork discovers events and updates database`() = runTest {
        val contact = ContactEntity(
            id = "c1",
            name = "John",
            birthdayDay = 15,
            birthdayMonth = 5,
            birthdayYear = 1990
        )
        coEvery { contactDao.getAllSync() } returns listOf(contact)

        val worker = TestListenableWorkerBuilder<EventDiscoveryWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker {
                    return EventDiscoveryWorker(appContext, workerParameters, contactDao, eventDao)
                }
            })
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { eventDao.upsert(any()) }
    }
}
