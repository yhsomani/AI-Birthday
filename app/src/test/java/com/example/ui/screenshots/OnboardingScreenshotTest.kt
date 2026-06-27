package com.example.ui.screenshots

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.screens.onboarding.OnboardingContent
import com.example.ui.screens.onboarding.OnboardingTestTags
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
class OnboardingScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun onboardingDefault_compactPhone() {
        setOnboardingContent()

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/onboarding_default_compact_phone.png")
    }

    @Test
    fun onboardingDefault_compactPhoneLargeFont() {
        setOnboardingContent(fontScale = LargeFontScale)

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/onboarding_default_compact_phone_large_font.png")
    }

    @Test
    fun onboardingActions_compactPhoneLargeFont() {
        setOnboardingContent(fontScale = LargeFontScale)

        composeRule.onNodeWithTag(OnboardingTestTags.SETUP_CHECKLIST_BUTTON)
            .performScrollTo()
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/onboarding_actions_compact_phone_large_font.png")
    }

    private fun setOnboardingContent(fontScale: Float = DefaultFontScale) {
        composeRule.setRelateScreenshotContent(fontScale = fontScale) {
            OnboardingContent(
                onContinue = {},
                onOpenAutomationSetup = {},
            )
        }
    }
}
