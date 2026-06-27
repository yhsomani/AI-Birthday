package com.example.ui.screens.onboarding

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.R
import com.example.core.ui.theme.RelateAITheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class OnboardingScreenInteractionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun onboardingActions_dispatchExpectedCallbacks() {
        val actions = mutableListOf<String>()

        composeRule.setContent {
            RelateAITheme {
                OnboardingContent(
                    onContinue = { actions += "continue" },
                    onOpenAutomationSetup = { actions += "setup" },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.onboarding_setup_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.onboarding_setup_google_title))
            .assertIsDisplayed()

        composeRule.onNodeWithTag(OnboardingTestTags.SETUP_CHECKLIST_BUTTON)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(OnboardingTestTags.CONTINUE_BUTTON)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertEquals(listOf("setup", "continue"), actions)
    }
}
