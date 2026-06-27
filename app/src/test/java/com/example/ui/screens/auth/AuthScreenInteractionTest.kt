package com.example.ui.screens.auth

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.R
import com.example.core.ui.theme.RelateAITheme
import com.example.ui.viewmodel.AuthUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class AuthScreenInteractionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun authActionsAndStates_renderExpectedControls() {
        val actions = mutableListOf<String>()
        var state by mutableStateOf(AuthUiState())
        var showDevBypass by mutableStateOf(true)

        composeRule.setContent {
            RelateAITheme {
                AuthContent(
                    state = state,
                    onSignIn = { actions += "signIn" },
                    onDevBypass = { actions += "bypass" },
                    showDevBypass = showDevBypass,
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.app_name))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(AuthScreenTestTags.SIGN_IN_BUTTON)
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(AuthScreenTestTags.DEV_BYPASS_BUTTON)
            .assertIsDisplayed()
            .performClick()

        state = AuthUiState(isLoading = true)
        composeRule.onNodeWithTag(AuthScreenTestTags.LOADING)
            .assertIsDisplayed()

        state = AuthUiState(error = "Unable to sign in.")
        composeRule.onNodeWithTag(AuthScreenTestTags.ERROR)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Unable to sign in.")
            .assertIsDisplayed()

        showDevBypass = false
        composeRule.onAllNodesWithTag(AuthScreenTestTags.DEV_BYPASS_BUTTON)
            .assertCountEquals(0)

        assertEquals(listOf("signIn", "bypass"), actions)
    }
}
