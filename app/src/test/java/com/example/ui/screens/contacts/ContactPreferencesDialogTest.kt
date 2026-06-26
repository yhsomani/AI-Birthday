package com.example.ui.screens.contacts

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.R
import com.example.core.db.entities.ContactEntity
import com.example.core.ui.theme.RelateAITheme
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.usecase.UpdateContactPreferencesUseCase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ContactPreferencesDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun automationPickerUsesTypedOptionsAndSavesSelectedMode() {
        var savedRequest: UpdateContactPreferencesUseCase.Request? = null

        composeRule.setContent {
            RelateAITheme {
                ContactPreferencesDialog(
                    contact = ContactEntity(
                        id = "contact_1",
                        name = "Asha",
                        automationMode = ApprovalMode.DEFAULT.raw,
                    ),
                    isSaving = false,
                    onDismiss = {},
                    onSave = { savedRequest = it },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.contact_preferences_automation_mode))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onAllNodesWithText("Automation: DEFAULT, SMART_APPROVE, VIP_APPROVE, FULLY_AUTO, ALWAYS_ASK")
            .assertCountEquals(0)

        composeRule.onNodeWithText(context.getString(R.string.automation_mode_fully_auto))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.save))
            .performClick()

        assertEquals(ApprovalMode.FULLY_AUTO, savedRequest?.automationMode)
    }

    @Test
    fun channelPickerUsesTypedOptionsAndSavesSelectedChannel() {
        var savedRequest: UpdateContactPreferencesUseCase.Request? = null

        composeRule.setContent {
            RelateAITheme {
                ContactPreferencesDialog(
                    contact = ContactEntity(
                        id = "contact_1",
                        name = "Asha",
                        preferredChannel = MessageChannel.SMS.raw,
                    ),
                    isSaving = false,
                    onDismiss = {},
                    onSave = { savedRequest = it },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.contact_preferences_channel))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.channel_email))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.save))
            .performClick()

        assertEquals(MessageChannel.EMAIL, savedRequest?.preferredChannel)
    }

    @Test
    fun automationPickerFallsBackToDefaultForUnsupportedStoredMode() {
        var savedRequest: UpdateContactPreferencesUseCase.Request? = null

        composeRule.setContent {
            RelateAITheme {
                ContactPreferencesDialog(
                    contact = ContactEntity(
                        id = "contact_1",
                        name = "Asha",
                        automationMode = "LEGACY_MODE",
                    ),
                    isSaving = false,
                    onDismiss = {},
                    onSave = { savedRequest = it },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.save))
            .performClick()

        assertEquals(ApprovalMode.DEFAULT, savedRequest?.automationMode)
    }

    @Test
    fun channelPickerFallsBackToSmsForUnsupportedStoredChannel() {
        var savedRequest: UpdateContactPreferencesUseCase.Request? = null

        composeRule.setContent {
            RelateAITheme {
                ContactPreferencesDialog(
                    contact = ContactEntity(
                        id = "contact_1",
                        name = "Asha",
                        preferredChannel = "LEGACY_CHANNEL",
                    ),
                    isSaving = false,
                    onDismiss = {},
                    onSave = { savedRequest = it },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.save))
            .performClick()

        assertEquals(MessageChannel.SMS, savedRequest?.preferredChannel)
    }
}
