package com.example.ui.screens.settings

import android.app.Application
import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
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
import com.example.ui.viewmodel.SettingsUiState
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

    @Test
    fun contentActions_dispatchConfiguredCallbacks() {
        val actions = mutableListOf<String>()

        composeRule.setContent {
            RelateAITheme {
                val snackbarHostState = remember { SnackbarHostState() }
                SettingsContent(
                    state = SettingsUiState(
                        userName = "Yash Somani",
                        userEmail = "yash@example.com",
                        geminiApiKey = "AIzaExampleKey",
                        senderEmail = "sender@example.com",
                        senderEmailPassword = "app-password",
                        lastSyncTimestamp = "Just now",
                        lastBackupTimestamp = "Today",
                    ),
                    snackbarHostState = snackbarHostState,
                    onNavigateToStyleCoach = { actions += "style" },
                    onNavigateToAutomationSetup = { actions += "doctor" },
                    onSaveGeminiApiKey = { actions += "save_api_key" },
                    onSaveSenderEmailSettings = { actions += "save_email" },
                    onSyncContacts = { actions += "sync" },
                    onNavigateToBackupRestore = { actions += "backup" },
                    onNavigateToActivityHistory = { actions += "activity" },
                    onSignOut = { actions += "sign_out" },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.settings_ai_style_coach))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.settings_automation_setup))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.settings_save_api_key))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.settings_save_email_settings))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.settings_sync_contacts))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.backup_restore_title))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.activity_history_title))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(SettingsScreenTestTags.SIGN_OUT_TRIGGER)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(SettingsScreenTestTags.SIGN_OUT_CONFIRM)
            .performClick()

        assertEquals(
            listOf(
                "style",
                "doctor",
                "save_api_key",
                "save_email",
                "sync",
                "backup",
                "activity",
                "sign_out",
            ),
            actions,
        )
    }
}
