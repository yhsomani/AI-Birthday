package com.example.ui.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.core.db.entities.ContactEntity
import com.example.core.gemini.GeminiClient
import com.example.core.prefs.SecurePrefs
import com.example.domain.model.MessageChannel
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
    fun `refresh assigns readiness checks to grouped AI Doctor sections`() = runTest(testDispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        val groupsByTitle = viewModel.buildChecksForTesting().associate {
            it.title to it.group
        }

        assertEquals(
            ReadinessGroup.REQUIRED,
            groupsByTitle[context.getString(R.string.automation_setup_check_sms)],
        )
        assertEquals(
            ReadinessGroup.QUALITY,
            groupsByTitle[context.getString(R.string.automation_setup_check_style_coach)],
        )
        assertEquals(
            ReadinessGroup.RELIABILITY,
            groupsByTitle[context.getString(R.string.automation_setup_check_exact_sends)],
        )
        assertEquals(
            ReadinessGroup.RECOVERY,
            groupsByTitle[context.getString(R.string.automation_setup_check_recent_errors)],
        )
    }

    @Test
    fun `buildChecksForTesting adds generic message risk diagnostic from personalization context`() = runTest(testDispatcher) {
        coEvery { contactRepository.getAllSync() } returns listOf(
            ContactEntity(
                id = "ready",
                name = "Ready Contact",
                notesText = "College friend",
            ),
            ContactEntity(
                id = "generic",
                name = "Generic Contact",
            ),
        )
        val viewModel = newViewModel()
        advanceUntilIdle()

        val genericRisk = viewModel.buildChecksForTesting()
            .first { it.title == context.getString(R.string.automation_setup_check_generic_messages) }

        assertEquals(ReadinessStatus.WARNING, genericRisk.status)
        assertEquals(AiDoctorAction.OPEN_CONTACTS, genericRisk.action)
        assertEquals(context.getString(R.string.automation_setup_action_review_contacts), genericRisk.actionLabel)
        assertEquals(
            context.getString(R.string.automation_setup_generic_messages_low, 1, 2),
            genericRisk.detail,
        )
    }

    @Test
    fun `buildChecksForTesting counts email preferred contacts through MessageChannel parser`() = runTest(testDispatcher) {
        coEvery { contactRepository.getAllSync() } returns listOf(
            ContactEntity(
                id = "email",
                name = "Email Contact",
                preferredChannel = " ${MessageChannel.EMAIL.raw.lowercase()} ",
            ),
            ContactEntity(
                id = "sms",
                name = "SMS Contact",
                preferredChannel = MessageChannel.SMS.raw,
            ),
            ContactEntity(
                id = "legacy",
                name = "Legacy Contact",
                preferredChannel = "telegram",
            ),
        )
        val viewModel = newViewModel()
        advanceUntilIdle()

        val emailCheck = viewModel.buildChecksForTesting()
            .first { it.title == context.getString(R.string.automation_setup_check_email) }

        assertEquals(ReadinessStatus.ACTION_REQUIRED, emailCheck.status)
        assertEquals(AiDoctorAction.OPEN_SETTINGS, emailCheck.action)
        assertEquals(
            context.getString(R.string.automation_setup_email_missing_for_contacts, 1),
            emailCheck.detail,
        )
    }

    @Test
    fun `recommendedFixForTesting ranks required blockers before earlier warnings and quality fixes`() = runTest(testDispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        val recommendedFix = viewModel.recommendedFixForTesting(
            listOf(
                ReadinessCheck(
                    title = "Style Coach",
                    detail = "No writing samples",
                    status = ReadinessStatus.ACTION_REQUIRED,
                    actionLabel = "Open Style Coach",
                    action = AiDoctorAction.OPEN_STYLE_COACH,
                    group = ReadinessGroup.QUALITY,
                ),
                ReadinessCheck(
                    title = "Daily Automation",
                    detail = "Daily work is missing",
                    status = ReadinessStatus.WARNING,
                    actionLabel = "Refresh",
                    action = AiDoctorAction.REFRESH,
                    group = ReadinessGroup.RELIABILITY,
                ),
                ReadinessCheck(
                    title = MessageChannel.SMS.raw,
                    detail = "SMS permission is missing",
                    status = ReadinessStatus.ACTION_REQUIRED,
                    actionLabel = "App Settings",
                    action = AiDoctorAction.OPEN_APP_SETTINGS,
                    group = ReadinessGroup.REQUIRED,
                ),
            ),
        )

        assertEquals(MessageChannel.SMS.raw, recommendedFix?.title)
        assertEquals(AiDoctorAction.OPEN_APP_SETTINGS, recommendedFix?.action)
        assertEquals(ReadinessGroup.REQUIRED, recommendedFix?.group)
    }

    @Test
    fun `setupProgressForTesting counts ok warnings and blockers`() = runTest(testDispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        val progress = viewModel.setupProgressForTesting(
            listOf(
                ReadinessCheck(
                    title = "Ready",
                    detail = "Ready",
                    status = ReadinessStatus.OK,
                ),
                ReadinessCheck(
                    title = "Warning",
                    detail = "Warning",
                    status = ReadinessStatus.WARNING,
                ),
                ReadinessCheck(
                    title = "Blocker",
                    detail = "Blocker",
                    status = ReadinessStatus.ACTION_REQUIRED,
                ),
            ),
        )

        assertEquals(1, progress.completedSteps)
        assertEquals(3, progress.totalSteps)
        assertEquals(1, progress.warningCount)
        assertEquals(1, progress.actionRequiredCount)
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
