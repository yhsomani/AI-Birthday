package com.example.ui.screens.splash

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.R
import com.example.core.ui.theme.RelateAITheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class SplashScreenInteractionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun splashContent_rendersBrandSubtitleAndProgress() {
        composeRule.setContent {
            RelateAITheme {
                SplashContent(alpha = 1f)
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.app_name))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.auth_subtitle))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(SplashScreenTestTags.PROGRESS)
            .assertIsDisplayed()
    }
}
