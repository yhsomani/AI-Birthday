package com.example.ui.screenshots

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.screens.setup.AutomationSetupContent
import com.example.ui.screens.setup.AutomationSetupTestTags
import com.example.ui.viewmodel.AiDoctorAction
import com.example.ui.viewmodel.AiDoctorRecommendedFix
import com.example.ui.viewmodel.AiDoctorSummary
import com.example.ui.viewmodel.AutomationSetupUiState
import com.example.ui.viewmodel.ReadinessCheck
import com.example.ui.viewmodel.ReadinessGroup
import com.example.ui.viewmodel.ReadinessStatus
import com.example.ui.viewmodel.SetupProgressSummary
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@Category(ScreenshotTests::class)
@RunWith(AndroidJUnit4::class)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "w360dp-h800dp-xhdpi",
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AutomationSetupScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aiDoctorBlockers_compactPhone() {
        setAutomationSetupContent(state = blockerState())

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/ai_doctor_blockers_compact_phone.png")
    }

    @Test
    fun aiDoctorBlockers_compactPhoneLargeFont() {
        setAutomationSetupContent(
            state = blockerState(),
            fontScale = LargeFontScale,
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/ai_doctor_blockers_compact_phone_large_font.png")
    }

    @Test
    fun aiDoctorSetupCards_compactPhoneLargeFont() {
        setAutomationSetupContent(
            state = blockerState(),
            fontScale = LargeFontScale,
        )

        composeRule.onNodeWithTag(AutomationSetupTestTags.WHATSAPP_CARD)
            .performScrollTo()
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/ai_doctor_setup_cards_compact_phone_large_font.png")
    }

    @Test
    fun aiDoctorHealthy_compactPhone() {
        setAutomationSetupContent(state = healthyState())

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/ai_doctor_healthy_compact_phone.png")
    }

    @Test
    fun aiDoctorRefreshing_compactPhone() {
        setAutomationSetupContent(
            state = blockerState().copy(isRefreshing = true),
            animationFrameMillis = ProgressAnimationFrameMillis,
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/ai_doctor_refreshing_compact_phone.png")
    }

    private fun setAutomationSetupContent(
        state: AutomationSetupUiState,
        fontScale: Float = DefaultFontScale,
        animationFrameMillis: Long? = null,
    ) {
        if (animationFrameMillis != null) {
            composeRule.mainClock.autoAdvance = false
        }
        composeRule.setRelateScreenshotContent(fontScale = fontScale) {
            AutomationSetupContent(
                state = state,
                isIgnoringBatteryOptimizations = false,
            )
        }
        if (animationFrameMillis != null) {
            composeRule.mainClock.advanceTimeBy(animationFrameMillis)
            composeRule.waitForIdle()
        }
    }

    private fun blockerState(): AutomationSetupUiState {
        val checks = listOf(
            check(
                title = "Google Contacts",
                detail = "Contacts are synced and ready for personalization.",
                status = ReadinessStatus.OK,
                group = ReadinessGroup.REQUIRED,
            ),
            check(
                title = "Gemini access",
                detail = "Add a Gemini API key or sign in before generating wishes.",
                status = ReadinessStatus.ACTION_REQUIRED,
                actionLabel = "Open settings",
                action = AiDoctorAction.OPEN_SETTINGS,
                group = ReadinessGroup.REQUIRED,
            ),
            check(
                title = "SMS permission",
                detail = "Allow SMS permission before scheduled sends can run automatically.",
                status = ReadinessStatus.ACTION_REQUIRED,
                actionLabel = "App settings",
                action = AiDoctorAction.OPEN_APP_SETTINGS,
                group = ReadinessGroup.REQUIRED,
            ),
            check(
                title = "Style Coach",
                detail = "Add two more writing samples to improve tone matching.",
                status = ReadinessStatus.WARNING,
                actionLabel = "Open Style Coach",
                action = AiDoctorAction.OPEN_STYLE_COACH,
                group = ReadinessGroup.QUALITY,
            ),
            check(
                title = "Personalization",
                detail = "4 of 12 contacts have enough memories, notes, or interests.",
                status = ReadinessStatus.WARNING,
                actionLabel = "Review contacts",
                action = AiDoctorAction.OPEN_CONTACTS,
                group = ReadinessGroup.QUALITY,
            ),
            check(
                title = "WhatsApp automation",
                detail = "Confirm consent and enable the Accessibility service before WhatsApp can be automated.",
                status = ReadinessStatus.ACTION_REQUIRED,
                actionLabel = "Open Accessibility",
                action = AiDoctorAction.OPEN_ACCESSIBILITY_SETTINGS,
                group = ReadinessGroup.RELIABILITY,
            ),
            check(
                title = "Exact scheduled sends",
                detail = "Exact alarm access is missing, so time-sensitive sends may fall back.",
                status = ReadinessStatus.ACTION_REQUIRED,
                actionLabel = "App settings",
                action = AiDoctorAction.OPEN_APP_SETTINGS,
                group = ReadinessGroup.RELIABILITY,
            ),
            check(
                title = "Failed-send recovery",
                detail = "2 persisted dispatch recovery records need review; 1 is dead-lettered.",
                status = ReadinessStatus.WARNING,
                actionLabel = "View activity",
                action = AiDoctorAction.OPEN_ACTIVITY_HISTORY,
                group = ReadinessGroup.RECOVERY,
            ),
        )
        return AutomationSetupUiState(
            checks = checks,
            summary = AiDoctorSummary(
                title = "4 blockers need attention",
                detail = "Start with Gemini access so wish generation can run safely.",
                status = ReadinessStatus.ACTION_REQUIRED,
            ),
            recommendedFix = AiDoctorRecommendedFix(
                title = "Gemini access",
                detail = "Add a key or sign in, then run the AI generation test.",
                actionLabel = "Open settings",
                action = AiDoctorAction.OPEN_SETTINGS,
                status = ReadinessStatus.ACTION_REQUIRED,
                group = ReadinessGroup.REQUIRED,
            ),
            setupProgress = SetupProgressSummary(
                completedSteps = 1,
                totalSteps = checks.size,
                actionRequiredCount = 4,
                warningCount = 3,
            ),
            operationMessage = "Dry run blocked by Gemini access.",
            whatsAppAutomationConsentGranted = false,
        )
    }

    private fun healthyState(): AutomationSetupUiState {
        val checks = listOf(
            check(
                title = "Google Contacts",
                detail = "Contacts are synced and ready.",
                status = ReadinessStatus.OK,
                group = ReadinessGroup.REQUIRED,
            ),
            check(
                title = "Gemini access",
                detail = "Gemini access is configured and testable.",
                status = ReadinessStatus.OK,
                actionLabel = "Test AI",
                action = AiDoctorAction.TEST_AI,
                group = ReadinessGroup.REQUIRED,
            ),
            check(
                title = "Personalization",
                detail = "11 of 12 contacts have useful context.",
                status = ReadinessStatus.OK,
                group = ReadinessGroup.QUALITY,
            ),
            check(
                title = "WhatsApp automation",
                detail = "Consent is recorded and the automation service is ready.",
                status = ReadinessStatus.OK,
                group = ReadinessGroup.RELIABILITY,
            ),
            check(
                title = "Failed-send recovery",
                detail = "No failed or dead-lettered sends need recovery.",
                status = ReadinessStatus.OK,
                group = ReadinessGroup.RECOVERY,
            ),
        )
        return AutomationSetupUiState(
            checks = checks,
            summary = AiDoctorSummary(
                title = "Automation is ready",
                detail = "All critical diagnostics are passing.",
                status = ReadinessStatus.OK,
            ),
            setupProgress = SetupProgressSummary(
                completedSteps = checks.size,
                totalSteps = checks.size,
            ),
            operationMessage = "AI generation test passed.",
            whatsAppAutomationConsentGranted = true,
        )
    }

    private fun check(
        title: String,
        detail: String,
        status: ReadinessStatus,
        group: ReadinessGroup,
        actionLabel: String? = null,
        action: AiDoctorAction = AiDoctorAction.NONE,
    ): ReadinessCheck {
        return ReadinessCheck(
            title = title,
            detail = detail,
            status = status,
            actionLabel = actionLabel,
            action = action,
            group = group,
        )
    }

    private companion object {
        const val ProgressAnimationFrameMillis = 750L
    }
}
