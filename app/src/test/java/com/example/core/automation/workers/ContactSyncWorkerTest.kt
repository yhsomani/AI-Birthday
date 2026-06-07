package com.example.core.automation.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.core.contacts.GoogleContactsSync
import com.example.core.db.dao.ContactDao
import com.example.core.prefs.SecurePrefs
import com.example.domain.usecase.ClassifyContactUseCase
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
    private val contactDao: ContactDao = mockk(relaxed = true)
    private val classifyContactUseCase: ClassifyContactUseCase = mockk(relaxed = true)
    private val prefs: SecurePrefs = mockk(relaxed = true)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockkConstructor(GoogleContactsSync::class)

        mockkStatic(FirebaseAuth::class)
        val mockAuth = mockk<FirebaseAuth>()
        val mockUser = mockk<FirebaseUser>()
        every { FirebaseAuth.getInstance() } returns mockAuth
        every { mockAuth.currentUser } returns mockUser
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `doWork with no contacts returns success`() = runTest {
        coEvery { anyConstructed<GoogleContactsSync>().fetchAll() } returns emptyList()

        val worker = TestListenableWorkerBuilder<ContactSyncWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker {
                    return ContactSyncWorker(appContext, workerParameters, contactDao, classifyContactUseCase, prefs)
                }
            })
            .build()

        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)
    }
}
