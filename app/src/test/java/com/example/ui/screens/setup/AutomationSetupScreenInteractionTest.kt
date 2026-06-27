package com.example.ui.screens.setup

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.R
import com.example.core.ui.theme.RelateAITheme
import com.example.ui.viewmodel.AiDoctorSummary
import com.example.ui.viewmodel.AutomationSetupUiState
import com.example.ui.viewmodel.ReadinessCheck
import com.example.ui.viewmodel.ReadinessGroup
import com.example.ui.viewmodel.ReadinessStatus
import com.example.ui.viewmodel.SetupProgressSummary
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class AutomationSetupScreenInteractionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun contentActionButtonsInvokeCallbacks() {
        val actions = mutableListOf<String>()

        composeRule.setContent {
            RelateAITheme {
                AutomationSetupContent(
                    state = interactionState(),
                    isIgnoringBatteryOptimizations = false,
                    onRefresh = { actions += "refresh" },
                    onDryRun = { actions += "dryRun" },
                    onSyncContacts = { actions += "syncContacts" },
                    onTestAi = { actions += "testAi" },
                    onTestEmail = { actions += "testEmail" },
                )
            }
        }

        clickAction(R.string.automation_setup_action_refresh)
        clickAction(R.string.automation_setup_action_dry_run)
        clickAction(R.string.automation_setup_action_sync_contacts)
        clickAction(R.string.automation_setup_action_test_ai)
        clickAction(R.string.automation_setup_action_test_email)

        assertEquals(
            listOf("refresh", "dryRun", "syncContacts", "testAi", "testEmail"),
            actions,
        )
    }

    private fun clickAction(labelRes: Int) {
        composeRule.onNodeWithText(context.getString(labelRes))
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
    }

    private fun interactionState(): AutomationSetupUiState {
        return AutomationSetupUiState(
            checks = listOf(
                ReadinessCheck(
                    title = "Gemini access",
                    detail = "Ready for a test generation.",
                    status = ReadinessStatus.OK,
                    group = ReadinessGroup.REQUIRED,
                ),
            ),
            summary = AiDoctorSummary(
                title = "Automation is ready",
                detail = "All critical diagnostics are passing.",
                status = ReadinessStatus.OK,
            ),
            setupProgress = SetupProgressSummary(
                completedSteps = 1,
                totalSteps = 1,
            ),
        )
    }
}
