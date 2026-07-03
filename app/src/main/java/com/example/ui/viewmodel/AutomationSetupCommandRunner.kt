package com.example.ui.viewmodel

import android.content.Context
import androidx.annotation.StringRes
import com.example.R
import com.example.core.gemini.GeminiClient
import com.example.domain.service.PreferencesRepository
import com.example.domain.usecase.SyncContactsUseCase
import com.example.domain.usecase.TestSendUseCase

internal class AutomationSetupCommandRunner(
    private val context: Context,
    private val preferencesRepository: PreferencesRepository,
    private val syncContactsUseCase: SyncContactsUseCase,
    private val geminiClient: GeminiClient,
    private val testSendUseCase: TestSendUseCase,
    private val aiFailureDiagnoser: AutomationSetupAiFailureDiagnoser,
    private val hasFirebaseAuth: () -> Boolean,
) {
    suspend fun syncContactsMessage(): String {
        return try {
            val outcome = syncContactsUseCase(forceRefresh = true)
            text(R.string.automation_setup_sync_success, outcome.googleCount)
        } catch (_: Exception) {
            text(R.string.automation_setup_sync_failed)
        }
    }

    fun safeGenerationMessage(state: AutomationSetupUiState): String {
        val hasAiAccess = preferencesRepository.getGeminiApiKey().isNotBlank() || hasFirebaseAuth()
        val ready = preferencesRepository.isAiWishGenerationEnabled() && hasAiAccess
        if (!ready) {
            return text(R.string.automation_setup_dry_run_missing_ai)
        }
        val rankedBlocker = state.recommendedFix
            ?.takeIf { it.status == ReadinessStatus.ACTION_REQUIRED }
            ?.let { it.title to it.detail }
        val firstBlocker = rankedBlocker ?: state.checks
            .firstOrNull { it.status == ReadinessStatus.ACTION_REQUIRED }
            ?.let { it.title to it.detail }
        return firstBlocker?.let { (title, detail) ->
            text(R.string.automation_setup_dry_run_blocker, title, detail)
        } ?: text(R.string.automation_setup_dry_run_ready)
    }

    suspend fun testAiGenerationMessage(): String {
        return try {
            val response = geminiClient.generate(AI_TEST_PROMPT)
            if (response.contains("\"error\"", ignoreCase = true)) {
                aiFailureDiagnoser.diagnose(response)
            } else {
                text(R.string.automation_setup_ai_test_success)
            }
        } catch (_: Exception) {
            text(R.string.automation_setup_ai_test_failed)
        }
    }

    suspend fun testEmailSendMessage(): String {
        return when (testSendUseCase(text(R.string.automation_setup_email_test_message))) {
            TestSendUseCase.Outcome.Sent -> text(R.string.automation_setup_email_test_success)
            TestSendUseCase.Outcome.MissingEmailSetup -> text(R.string.automation_setup_email_missing)
            TestSendUseCase.Outcome.BlankMessage -> text(R.string.automation_setup_email_test_failed)
            TestSendUseCase.Outcome.SendFailed -> text(R.string.automation_setup_email_test_failed)
        }
    }

    fun saveWhatsAppAutomationConsentMessage(granted: Boolean): String {
        preferencesRepository.setWhatsAppAutomationConsentGranted(granted)
        return text(R.string.automation_setup_whatsapp_consent_saved)
    }

    private fun text(@StringRes resId: Int, vararg args: Any): String {
        return context.getString(resId, *args)
    }

    private companion object {
        val AI_TEST_PROMPT = """
            Return ONLY valid JSON:
            {"short":"Ready","standard":"RelateAI automation check is ready.","long":"RelateAI automation check is ready.","formal":"RelateAI automation check is ready.","funny":"RelateAI automation check is ready.","emotional":"RelateAI automation check is ready.","recommended":"standard"}
        """.trimIndent()
    }
}
