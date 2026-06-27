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
import com.example.core.ui.theme.RelateAITheme
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.common.ContactId
import com.example.domain.model.contact.ContactDetailProfile
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
                    contact = contactProfile(
                        automationMode = ApprovalMode.DEFAULT,
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
                    contact = contactProfile(
                        preferredChannel = MessageChannel.SMS,
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
                    contact = contactProfile(automationMode = ApprovalMode.UNKNOWN),
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
                    contact = contactProfile(preferredChannel = MessageChannel.UNKNOWN),
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

    private fun contactProfile(
        automationMode: ApprovalMode = ApprovalMode.DEFAULT,
        preferredChannel: MessageChannel = MessageChannel.SMS,
    ): ContactDetailProfile {
        return ContactDetailProfile(
            id = ContactId("contact_1"),
            displayName = "Asha",
            contactGroup = null,
            healthScore = 80,
            nickname = null,
            birthdayDay = null,
            birthdayMonth = null,
            primaryPhone = null,
            primaryEmail = null,
            relationshipType = "UNKNOWN",
            preferredLanguage = "en",
            preferredChannel = preferredChannel,
            formalityLevel = "CASUAL",
            communicationStyle = "WARM",
            automationMode = automationMode,
            customSendTimeHour = null,
            customSendTimeMinute = null,
            giftBudgetInr = 500,
            annualBudgetInr = 0,
            skipAutoWish = false,
            interestsJson = "[]",
            sensitiveTopicsJson = "[]",
            currentLifePhaseJson = "{}",
            notesText = "",
        )
    }
}
