package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.core.gemini.GeminiClient
import com.example.domain.service.PreferencesRepository
import com.example.domain.usecase.SyncContactsUseCase
import com.example.domain.usecase.TestSendUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit4.MockKRule
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AutomationSetupCommandRunnerTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @RelaxedMockK
    private lateinit var preferencesRepository: PreferencesRepository

    @RelaxedMockK
    private lateinit var syncContactsUseCase: SyncContactsUseCase

    @RelaxedMockK
    private lateinit var geminiClient: GeminiClient

    @RelaxedMockK
    private lateinit var testSendUseCase: TestSendUseCase

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        every { preferencesRepository.getGeminiApiKey() } returns ""
        every { preferencesRepository.isAiWishGenerationEnabled() } returns true
    }

    @Test
    fun `syncContactsMessage reports google sync count`() = runTest {
        coEvery { syncContactsUseCase(forceRefresh = true) } returns SyncContactsUseCase.SyncOutcome(
            googleCount = 7,
            deviceCount = 3,
            inserted = 2,
            updated = 8,
        )

        assertEquals(
            context.getString(R.string.automation_setup_sync_success, 7),
            runner().syncContactsMessage(),
        )
        coVerify { syncContactsUseCase(forceRefresh = true) }
    }

    @Test
    fun `syncContactsMessage reports failure without leaking exception detail`() = runTest {
        coEvery { syncContactsUseCase(forceRefresh = true) } throws RuntimeException("secret sync token")

        assertEquals(
            context.getString(R.string.automation_setup_sync_failed),
            runner().syncContactsMessage(),
        )
    }

    @Test
    fun `safeGenerationMessage reports missing ai access before blockers`() {
        val state = AutomationSetupUiState(
            recommendedFix = requiredFix(title = "SMS", detail = "Permission missing"),
        )

        assertEquals(
            context.getString(R.string.automation_setup_dry_run_missing_ai),
            runner(hasFirebaseAuth = false).safeGenerationMessage(state),
        )
    }

    @Test
    fun `safeGenerationMessage prefers ranked blocker when ai access is ready`() {
        every { preferencesRepository.getGeminiApiKey() } returns "api-key"
        val state = AutomationSetupUiState(
            checks = listOf(
                ReadinessCheck(
                    title = "Contacts",
                    detail = "No contacts",
                    status = ReadinessStatus.ACTION_REQUIRED,
                ),
            ),
            recommendedFix = requiredFix(title = "Gemini", detail = "Quota exceeded"),
        )

        assertEquals(
            context.getString(R.string.automation_setup_dry_run_blocker, "Gemini", "Quota exceeded"),
            runner().safeGenerationMessage(state),
        )
    }

    @Test
    fun `testAiGenerationMessage maps provider errors through diagnoser`() = runTest {
        coEvery { geminiClient.generate(any()) } returns """{"error":"429 quota exhausted"}"""

        assertEquals(
            context.getString(R.string.automation_setup_ai_error_quota),
            runner().testAiGenerationMessage(),
        )
    }

    @Test
    fun `testAiGenerationMessage reports success for non-error response`() = runTest {
        coEvery { geminiClient.generate(any()) } returns """{"standard":"Ready"}"""

        assertEquals(
            context.getString(R.string.automation_setup_ai_test_success),
            runner().testAiGenerationMessage(),
        )
    }

    @Test
    fun `testEmailSendMessage maps missing setup`() = runTest {
        coEvery { testSendUseCase(any()) } returns TestSendUseCase.Outcome.MissingEmailSetup

        assertEquals(
            context.getString(R.string.automation_setup_email_missing),
            runner().testEmailSendMessage(),
        )
    }

    @Test
    fun `saveWhatsAppAutomationConsentMessage persists consent and returns confirmation`() {
        assertEquals(
            context.getString(R.string.automation_setup_whatsapp_consent_saved),
            runner().saveWhatsAppAutomationConsentMessage(granted = true),
        )
        verify { preferencesRepository.setWhatsAppAutomationConsentGranted(true) }
    }

    private fun runner(hasFirebaseAuth: Boolean = false): AutomationSetupCommandRunner {
        return AutomationSetupCommandRunner(
            context = context,
            preferencesRepository = preferencesRepository,
            syncContactsUseCase = syncContactsUseCase,
            geminiClient = geminiClient,
            testSendUseCase = testSendUseCase,
            aiFailureDiagnoser = AutomationSetupAiFailureDiagnoser(context),
            hasFirebaseAuth = { hasFirebaseAuth },
        )
    }

    private fun requiredFix(title: String, detail: String): AiDoctorRecommendedFix {
        return AiDoctorRecommendedFix(
            title = title,
            detail = detail,
            actionLabel = "Fix",
            action = AiDoctorAction.OPEN_SETTINGS,
            status = ReadinessStatus.ACTION_REQUIRED,
            group = ReadinessGroup.REQUIRED,
        )
    }
}
