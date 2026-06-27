package com.example.ui.screens.settings

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
class SettingsScreenInteractionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun signOutConfirmation_showsChecklistAndDispatchesActions() {
        val actions = mutableListOf<String>()

        composeRule.setContent {
            RelateAITheme {
                SignOutConfirmationDialog(
                    onDismiss = { actions += "dismiss" },
                    onConfirm = { actions += "confirm" },
                )
            }
        }

        composeRule.onNodeWithTag(SettingsScreenTestTags.SIGN_OUT_DIALOG)
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.settings_sign_out_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.settings_sign_out_check_local_data))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.settings_sign_out_check_backup))
            .assertIsDisplayed()

        composeRule.onNodeWithText(context.getString(R.string.settings_sign_out_cancel))
            .performClick()
        composeRule.onNodeWithTag(SettingsScreenTestTags.SIGN_OUT_CONFIRM)
            .performClick()

        assertEquals(listOf("dismiss", "confirm"), actions)
    }
}
