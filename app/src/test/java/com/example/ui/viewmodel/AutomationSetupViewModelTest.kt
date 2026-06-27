package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.core.resilience.DeadLetterEntry
import com.example.core.resilience.DeadLetterQueue
import com.example.core.resilience.HealthMonitor
import com.example.core.resilience.StructuredLogger
import com.example.core.gemini.GeminiClient
import com.example.core.prefs.SecurePrefs
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.DiagnosticSnapshotId
import com.example.domain.model.common.DispatchAttemptId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.contact.ContactAutomationReadinessProfile
import com.example.domain.model.diagnostic.DiagnosticSnapshot
import com.example.domain.model.diagnostic.DiagnosticSnapshotSource
import com.example.domain.model.diagnostic.DiagnosticSnapshotStatus
import com.example.domain.model.dispatch.DispatchAttempt
import com.example.domain.model.dispatch.DispatchAttemptCreator
import com.example.domain.model.dispatch.DispatchAttemptResult
import com.example.domain.model.dispatch.DispatchEligibilityRecord
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.DiagnosticSnapshotRepository
import com.example.domain.repository.DispatchAttemptRepository
import com.example.domain.repository.StyleProfileRepository
import com.example.domain.usecase.SyncContactsUseCase
import com.example.domain.usecase.TestSendUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit4.MockKRule
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.flow.flowOf
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
@Config(sdk = [34], application = Application::class)
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

    @RelaxedMockK
    private lateinit var dispatchAttemptRepository: DispatchAttemptRepository

    @RelaxedMockK
    private lateinit var diagnosticSnapshotRepository: DiagnosticSnapshotRepository

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        DeadLetterQueue.clear()
        HealthMonitor.clearForTests()
        StructuredLogger.clearForTests()
        context = ApplicationProvider.getApplicationContext()
        every { securePrefs.getGoogleOAuthToken() } returns ""
        every { securePrefs.getGeminiApiKey() } returns ""
        every { securePrefs.getSenderEmail() } returns ""
        every { securePrefs.getSenderEmailPassword() } returns ""
        every { securePrefs.isAiWishGenerationEnabled() } returns true
        every { securePrefs.isWhatsAppAutomationConsentGranted() } returns true
        coEvery { contactRepository.getAutomationReadinessProfiles() } returns emptyList()
        coEvery { styleProfileRepository.getProfileOnce() } returns null
        coEvery { testSendUseCase(any()) } returns TestSendUseCase.Outcome.MissingEmailSetup
        every { dispatchAttemptRepository.countFailureRecoveryQueue() } returns flowOf(0)
        every { dispatchAttemptRepository.countDeadLettered() } returns flowOf(0)
        coEvery { dispatchAttemptRepository.getFailureRecoveryQueue(any()) } returns emptyList()
        coEvery { diagnosticSnapshotRepository.getLatestBySource(any()) } returns null
        coEvery { diagnosticSnapshotRepository.record(any()) } returns Unit
    }

    @After
    fun tearDown() {
        DeadLetterQueue.clear()
        HealthMonitor.clearForTests()
        StructuredLogger.clearForTests()
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
        coEvery { contactRepository.getAutomationReadinessProfiles() } returns listOf(
            readinessProfile(
                id = "ready",
                notesText = "College friend",
            ),
            readinessProfile(
                id = "generic",
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
    fun `buildChecksForTesting counts email preferred contacts from readiness profiles`() = runTest(testDispatcher) {
        coEvery { contactRepository.getAutomationReadinessProfiles() } returns listOf(
            readinessProfile(
                id = "email",
                preferredChannel = MessageChannel.EMAIL,
            ),
            readinessProfile(
                id = "sms",
                preferredChannel = MessageChannel.SMS,
            ),
            readinessProfile(
                id = "legacy",
                preferredChannel = MessageChannel.UNKNOWN,
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
    fun `buildChecksForTesting requires WhatsApp consent before automation channel is ready`() = runTest(testDispatcher) {
        every { securePrefs.isWhatsAppAutomationConsentGranted() } returns false
        val viewModel = newViewModel()
        advanceUntilIdle()

        val whatsAppCheck = viewModel.buildChecksForTesting()
            .first { it.title == context.getString(R.string.automation_setup_check_whatsapp) }

        assertEquals(ReadinessStatus.ACTION_REQUIRED, whatsAppCheck.status)
        assertEquals(
            context.getString(R.string.automation_setup_whatsapp_consent_needed),
            whatsAppCheck.detail,
        )
    }

    @Test
    fun `setWhatsAppAutomationConsent persists acknowledgement and updates state`() = runTest(testDispatcher) {
        every { securePrefs.isWhatsAppAutomationConsentGranted() } returns false
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.setWhatsAppAutomationConsent(true)
        advanceUntilIdle()

        verify { securePrefs.setWhatsAppAutomationConsentGranted(true) }
        assertTrue(viewModel.uiState.value.whatsAppAutomationConsentGranted)
        assertEquals(
            context.getString(R.string.automation_setup_whatsapp_consent_saved),
            viewModel.uiState.value.operationMessage,
        )
    }

    @Test
    fun `buildChecksForTesting surfaces persisted dispatch recovery rows`() = runTest(testDispatcher) {
        every { dispatchAttemptRepository.countFailureRecoveryQueue() } returns flowOf(2)
        every { dispatchAttemptRepository.countDeadLettered() } returns flowOf(1)
        coEvery { dispatchAttemptRepository.getFailureRecoveryQueue(1) } returns listOf(
            DispatchAttempt(
                id = DispatchAttemptId("attempt_1"),
                messageDraftId = MessageDraftId("draft_1"),
                contactId = null,
                occasionId = null,
                channel = MessageChannel.SMS,
                routeRank = 0,
                eligibilityDecision = DispatchEligibilityRecord.SEND_NOW,
                blockOrDeferReason = null,
                requestedAtMs = 1_700_000_000_000,
                attemptedAtMs = 1_700_000_000_100,
                resolvedAtMs = 1_700_000_000_200,
                result = DispatchAttemptResult.FAILED_FINAL,
                deliveryStatus = MessageDeliveryStatus.FAILED,
                providerMessageId = null,
                errorType = "NO_DELIVERY_ROUTE",
                errorCode = null,
                redactedErrorMessage = "All automatic delivery routes failed.",
                retryCount = 0,
                nextRetryAtMs = null,
                deadLetteredAtMs = 1_700_000_000_200,
                createdBy = DispatchAttemptCreator.WORKER,
            ),
        )
        val viewModel = newViewModel()
        advanceUntilIdle()

        val recoveryCheck = viewModel.buildChecksForTesting()
            .first { it.title == context.getString(R.string.automation_setup_check_dead_letter) }

        assertEquals(ReadinessStatus.WARNING, recoveryCheck.status)
        assertEquals(AiDoctorAction.OPEN_ACTIVITY_HISTORY, recoveryCheck.action)
        assertEquals(context.getString(R.string.automation_setup_action_view_activity), recoveryCheck.actionLabel)
        assertTrue(recoveryCheck.detail.contains("2 persisted dispatch recovery records"))
        assertTrue(recoveryCheck.detail.contains("1 are dead-lettered"))
        assertTrue(recoveryCheck.detail.contains("SMS FAILED_FINAL"))
        assertTrue(recoveryCheck.detail.contains("draft_1"))
    }

    @Test
    fun `buildChecksForTesting ignores legacy in-memory dead letters when persisted recovery is empty`() =
        runTest(testDispatcher) {
            DeadLetterQueue.enqueue(
                DeadLetterEntry(
                    id = "draft_memory_only",
                    payload = "Message text",
                    errorMessage = "Legacy failure",
                    errorType = "LEGACY",
                    retryCount = 0,
                ),
            )

            val viewModel = newViewModel()
            advanceUntilIdle()

            val recoveryCheck = viewModel.buildChecksForTesting()
                .first { it.title == context.getString(R.string.automation_setup_check_dead_letter) }

            assertEquals(ReadinessStatus.OK, recoveryCheck.status)
            assertEquals(context.getString(R.string.automation_setup_dead_letter_none), recoveryCheck.detail)
        }

    @Test
    fun `buildChecksForTesting surfaces recent persisted HealthMonitor warning after process restart`() =
        runTest(testDispatcher) {
            coEvery {
                diagnosticSnapshotRepository.getLatestBySource(DiagnosticSnapshotSource.HEALTH_MONITOR)
            } returns DiagnosticSnapshot(
                id = DiagnosticSnapshotId("health_1"),
                source = DiagnosticSnapshotSource.HEALTH_MONITOR,
                status = DiagnosticSnapshotStatus.WARNING,
                summary = "HealthMonitor: healthy=false; recentErrors=1",
                checksJson = "{}",
                createdAtMs = System.currentTimeMillis(),
            )
            val viewModel = newViewModel()
            advanceUntilIdle()

            val recentErrorsCheck = viewModel.buildChecksForTesting()
                .first { it.title == context.getString(R.string.automation_setup_check_recent_errors) }

            assertEquals(ReadinessStatus.WARNING, recentErrorsCheck.status)
            assertEquals(AiDoctorAction.OPEN_ACTIVITY_HISTORY, recentErrorsCheck.action)
            assertEquals(context.getString(R.string.automation_setup_action_view_activity), recentErrorsCheck.actionLabel)
            assertTrue(recentErrorsCheck.detail.contains("HealthMonitor"))
        }

    @Test
    fun `buildChecksForTesting persists redacted AI Doctor diagnostic snapshot`() =
        runTest(testDispatcher) {
            val recorded = mutableListOf<DiagnosticSnapshot>()
            coEvery {
                diagnosticSnapshotRepository.getLatestBySource(DiagnosticSnapshotSource.HEALTH_MONITOR)
            } returns DiagnosticSnapshot(
                id = DiagnosticSnapshotId("health_secret"),
                source = DiagnosticSnapshotSource.HEALTH_MONITOR,
                status = DiagnosticSnapshotStatus.WARNING,
                summary = "HealthMonitor user=aarav@example.com Authorization=Bearer ya29.secret-token",
                checksJson = "{}",
                createdAtMs = System.currentTimeMillis(),
            )
            coEvery { diagnosticSnapshotRepository.record(capture(recorded)) } returns Unit
            val viewModel = newViewModel()
            advanceUntilIdle()

            viewModel.buildChecksForTesting()
            val aiDoctorSnapshot = recorded.last { it.source == DiagnosticSnapshotSource.AI_DOCTOR }

            assertEquals(DiagnosticSnapshotSource.AI_DOCTOR, aiDoctorSnapshot.source)
            assertFalse(aiDoctorSnapshot.summary.contains("aarav@example.com"))
            assertFalse(aiDoctorSnapshot.checksJson.contains("aarav@example.com"))
            assertFalse(aiDoctorSnapshot.summary.contains("ya29.secret-token"))
            assertFalse(aiDoctorSnapshot.checksJson.contains("ya29.secret-token"))
            assertTrue(aiDoctorSnapshot.checksJson.contains(context.getString(R.string.automation_setup_check_recent_errors)))
            assertTrue(aiDoctorSnapshot.checksJson.contains("[REDACTED_EMAIL]"))
            assertTrue(aiDoctorSnapshot.checksJson.contains("Bearer [REDACTED]"))
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
            dispatchAttemptRepository = dispatchAttemptRepository,
            diagnosticSnapshotRepository = diagnosticSnapshotRepository,
        )
    }

    private fun readinessProfile(
        id: String,
        preferredChannel: MessageChannel = MessageChannel.SMS,
        nickname: String? = null,
        notesText: String = "",
        interestsJson: String = "[]",
        sharedHistoryJson: String = "[]",
        classificationConfidence: Double = 0.0,
    ): ContactAutomationReadinessProfile {
        return ContactAutomationReadinessProfile(
            id = ContactId(id),
            preferredChannel = preferredChannel,
            nickname = nickname,
            notesText = notesText,
            interestsJson = interestsJson,
            sharedHistoryJson = sharedHistoryJson,
            classificationConfidence = classificationConfidence,
        )
    }
}
