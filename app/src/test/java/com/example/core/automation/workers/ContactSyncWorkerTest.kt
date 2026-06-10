package com.example.core.automation.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.core.db.entities.ContactEntity
import com.example.core.prefs.SecurePrefs
import com.example.domain.repository.ContactRepository
import com.example.domain.usecase.ClassifyContactUseCase
import com.example.domain.usecase.SyncContactsUseCase
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
class ContactSyncWorkerTest {

    private lateinit var context: Context
    private val syncContactsUseCase: SyncContactsUseCase = mockk(relaxed = true)
    private val contactRepository: ContactRepository = mockk(relaxed = true)
    private val classifyContactUseCase: ClassifyContactUseCase = mockk(relaxed = true)
    private val prefs: SecurePrefs = mockk(relaxed = true)
    private val mockAuth: FirebaseAuth = mockk()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        mockkStatic(FirebaseAuth::class)
        every { FirebaseAuth.getInstance() } returns mockAuth
        every { mockAuth.currentUser } returns null
        every { prefs.getGeminiApiKey() } returns ""
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `doWork uses shared sync pipeline and skips classification without credentials`() = runTest {
        coEvery { syncContactsUseCase(forceRefresh = false) } returns SyncContactsUseCase.SyncOutcome(
            googleCount = 1,
            deviceCount = 1,
            inserted = 1,
            updated = 1
        )

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { syncContactsUseCase(forceRefresh = false) }
        coVerify(exactly = 0) { contactRepository.getAllSync() }
        coVerify(exactly = 0) { classifyContactUseCase(any()) }
    }

    @Test
    fun `doWork classifies unknown contacts after successful sync when AI is configured`() = runTest {
        val unknownContact = ContactEntity(id = "contact_1", name = "Unknown", relationshipType = "UNKNOWN")
        val knownContact = ContactEntity(id = "contact_2", name = "Known", relationshipType = "FRIEND")

        coEvery { syncContactsUseCase(forceRefresh = false) } returns SyncContactsUseCase.SyncOutcome(
            googleCount = 0,
            deviceCount = 2,
            inserted = 2,
            updated = 0
        )
        every { prefs.getGeminiApiKey() } returns "gemini-key"
        coEvery { contactRepository.getAllSync() } returns listOf(unknownContact, knownContact)

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { classifyContactUseCase("contact_1") }
        coVerify(exactly = 0) { classifyContactUseCase("contact_2") }
    }

    @Test
    fun `doWork can classify with authenticated Firebase user`() = runTest {
        val mockUser = mockk<FirebaseUser>()
        every { mockAuth.currentUser } returns mockUser
        coEvery { syncContactsUseCase(forceRefresh = false) } returns SyncContactsUseCase.SyncOutcome(
            googleCount = 0,
            deviceCount = 1,
            inserted = 1,
            updated = 0
        )
        coEvery { contactRepository.getAllSync() } returns listOf(
            ContactEntity(id = "contact_1", name = "Unknown", relationshipType = "UNKNOWN")
        )

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { classifyContactUseCase("contact_1") }
    }

    @Test
    fun `doWork retries when shared sync pipeline fails`() = runTest {
        coEvery { syncContactsUseCase(forceRefresh = false) } throws RuntimeException("sync failed")

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    private fun worker(): ContactSyncWorker =
        TestListenableWorkerBuilder<ContactSyncWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker {
                    return ContactSyncWorker(
                        appContext,
                        workerParameters,
                        syncContactsUseCase,
                        contactRepository,
                        classifyContactUseCase,
                        prefs
                    )
                }
            })
            .build()
}
