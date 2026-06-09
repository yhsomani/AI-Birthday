package com.example.core.automation.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.core.automation.notifications.NotificationHelper
import com.example.core.db.dao.ContactDao
import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.entities.ContactEntity
import com.example.core.gemini.GeminiClient
import com.example.core.gemini.RateLimiter
import com.example.core.prefs.SecurePrefs
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
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
class RevivalWorkerTest {

    private lateinit var context: Context
    private val contactDao: ContactDao = mockk(relaxed = true)
    private val pendingMessageDao: PendingMessageDao = mockk(relaxed = true)
    private val geminiClient: GeminiClient = mockk(relaxed = true)
    private val prefs: SecurePrefs = mockk(relaxed = true)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockkStatic(FirebaseAuth::class)
        mockkObject(RateLimiter)
        mockkObject(NotificationHelper)

        val mockAuth = mockk<FirebaseAuth>()
        val mockUser = mockk<FirebaseUser>()
        every { FirebaseAuth.getInstance() } returns mockAuth
        every { mockAuth.currentUser } returns mockUser

        every { prefs.getGeminiApiKey() } returns "test_api_key"
        every { prefs.isAiWishGenerationEnabled() } returns true

        coEvery { RateLimiter.waitIfNeeded() } returns Unit
        every { NotificationHelper.showRevivalNotification(any(), any(), any(), any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `doWork with revival contacts generates reconnect message`() = runTest {
        val contact = ContactEntity(id = "c1", name = "Priya", healthScore = 20, lastInteractionDate = System.currentTimeMillis() - 40L * 24 * 60 * 60 * 1000L)
        coEvery { contactDao.getContactsForRevival(any()) } returns listOf(contact)
        coEvery { geminiClient.generate(any()) } returns "Hey Priya, it's been a while!"

        val worker = TestListenableWorkerBuilder<RevivalWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker {
                    return RevivalWorker(appContext, workerParameters, contactDao, pendingMessageDao, geminiClient, prefs)
                }
            })
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { pendingMessageDao.insert(any()) }
        coVerify { contactDao.updateLastRevivalAttempt("c1", any()) }
        verify { NotificationHelper.showRevivalNotification(any(), "Priya", any(), any(), "c1") }
    }
}
