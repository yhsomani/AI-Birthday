package com.example.ui.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.core.gemini.GeminiClient
import com.example.core.prefs.SecurePrefs
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.StyleProfileRepository
import com.example.domain.usecase.SyncContactsUseCase
import com.example.domain.usecase.TestSendUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutomationSetupViewModelTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @RelaxedMockK
    private lateinit var securePrefs: SecurePrefs

    @RelaxedMockK
    private lateinit var syncContactsUseCase: SyncContactsUseCase

    @RelaxedMockK
    private lateinit var geminiClient: GeminiClient

    @RelaxedMockK
    private lateinit var contactRepository: ContactRepository

    @RelaxedMockK
    private lateinit var styleProfileRepository: StyleProfileRepository

    @RelaxedMockK
    private lateinit var testSendUseCase: TestSendUseCase

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        every { securePrefs.getGoogleOAuthToken() } returns ""
        every { securePrefs.getGeminiApiKey() } returns ""
        every { securePrefs.getSenderEmail() } returns ""
        every { securePrefs.getSenderEmailPassword() } returns ""
        every { securePrefs.isAiWishGenerationEnabled() } returns true
        coEvery { contactRepository.getAllSync() } returns emptyList()
        coEvery { styleProfileRepository.getProfileOnce() } returns null
        coEvery { testSendUseCase(any()) } returns TestSendUseCase.Outcome.MissingEmailSetup
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `summarizeForTesting returns required summary for blockers`() = runTest(testDispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        val summary = viewModel.summarizeForTesting(
            listOf(
                ReadinessCheck(
                    title = context.getString(R.string.automation_setup_check_gemini),
                    detail = context.getString(R.string.automation_setup_gemini_auth_missing),
                    status = ReadinessStatus.ACTION_REQUIRED,
                ),
            ),
        )

        assertEquals(ReadinessStatus.ACTION_REQUIRED, summary.status)
        assertEquals(context.getString(R.string.automation_setup_summary_blockers, 1), summary.title)
        assertTrue(summary.detail.contains(context.getString(R.string.automation_setup_check_gemini)))
    }

    @Test
    fun `summarizeForTesting returns healthy summary when all checks pass`() = runTest(testDispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        val summary = viewModel.summarizeForTesting(
            listOf(
                ReadinessCheck(
                    title = context.getString(R.string.automation_setup_check_gemini),
                    detail = context.getString(R.string.automation_setup_gemini_key_ok),
                    status = ReadinessStatus.OK,
                ),
            ),
        )

        assertEquals(ReadinessStatus.OK, summary.status)
        assertEquals(context.getString(R.string.automation_setup_summary_ok), summary.title)
    }

    @Test
    fun `diagnoseAiFailureForTesting redacts sensitive fallback text`() = runTest(testDispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        val message = viewModel.diagnoseAiFailureForTesting(
            "Unexpected user=aarav@example.com Authorization=Bearer ya29.secret-token phone=+91 98765 43210",
        )

        assertFalse(message.contains("aarav@example.com"))
        assertFalse(message.contains("ya29.secret-token"))
        assertFalse(message.contains("+91 98765 43210"))
        assertTrue(message.contains("[REDACTED_EMAIL]"))
    }

    @Test
    fun `testEmailSend reports missing setup`() = runTest(testDispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.testEmailSend()
        advanceUntilIdle()

        assertEquals(context.getString(R.string.automation_setup_email_missing), viewModel.uiState.value.operationMessage)
        assertFalse(viewModel.uiState.value.isTestingEmail)
    }

    private fun newViewModel(): AutomationSetupViewModel {
        return AutomationSetupViewModel(
            appContext = context,
            securePrefs = securePrefs,
            syncContactsUseCase = syncContactsUseCase,
            geminiClient = geminiClient,
            contactRepository = contactRepository,
            styleProfileRepository = styleProfileRepository,
            testSendUseCase = testSendUseCase,
        )
    }
}
