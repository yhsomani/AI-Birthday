package com.example.ui.screenshots

import android.app.Application
import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.R
import com.example.domain.model.ApprovalMode
import com.example.ui.screens.settings.SettingsContent
import com.example.ui.screens.settings.SettingsScreenTestTags
import com.example.ui.viewmodel.SettingsUiState
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
class SettingsScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun settingsOverview_compactPhone() {
        setSettingsContent(state = populatedSettingsState())

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/settings_overview_compact_phone.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun settingsOverview_typicalPhone() {
        setSettingsContent(state = populatedSettingsState())

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/settings_overview_typical_phone.png")
    }

    @Test
    fun settingsAiConfiguration_compactPhoneLargeFont() {
        setSettingsContent(
            state = populatedSettingsState().copy(
                geminiApiKeySaved = true,
                automationMode = ApprovalMode.VIP_APPROVE,
                channelBlackoutWhatsApp = true,
            ),
            fontScale = LargeFontScale,
        )

        composeRule.onNodeWithText(context.getString(R.string.settings_automation_mode))
            .performScrollTo()
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/settings_ai_configuration_compact_phone_large_font.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun settingsAiConfiguration_typicalPhone() {
        setSettingsContent(
            state = populatedSettingsState().copy(
                geminiApiKeySaved = true,
                automationMode = ApprovalMode.VIP_APPROVE,
                channelBlackoutWhatsApp = true,
            ),
        )

        composeRule.onNodeWithText(context.getString(R.string.settings_automation_mode))
            .performScrollTo()
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/settings_ai_configuration_typical_phone.png")
    }

    @Test
    fun settingsDataTools_compactPhoneLargeFont() {
        setSettingsContent(
            state = populatedSettingsState().copy(
                isSyncing = true,
                showLegacyDbNotice = true,
            ),
            fontScale = LargeFontScale,
        )

        composeRule.onNodeWithText(context.getString(R.string.activity_history_title))
            .performScrollTo()
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/settings_data_tools_compact_phone_large_font.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun settingsDataTools_typicalPhone() {
        setSettingsContent(
            state = populatedSettingsState().copy(
                isSyncing = true,
                showLegacyDbNotice = true,
            ),
        )

        composeRule.onNodeWithText(context.getString(R.string.activity_history_title))
            .performScrollTo()
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/settings_data_tools_typical_phone.png")
    }

    @Test
    @Config(qualifiers = "hi-rIN-w360dp-h800dp-xhdpi")
    fun settingsDataTools_compactPhoneHindiLargeFont() {
        setSettingsContent(
            state = populatedSettingsState().copy(
                userName = "यश सोमानी",
                lastSyncTimestamp = "अभी",
                lastBackupTimestamp = "आज",
                isSyncing = true,
                showLegacyDbNotice = true,
            ),
            fontScale = LargeFontScale,
        )

        composeRule.onNodeWithText(context.getString(R.string.activity_history_title))
            .performScrollTo()
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/settings_data_tools_compact_phone_hindi_large_font.png")
    }

    @Test
    fun settingsSignOutDialog_compactPhone() {
        setSettingsContent(state = populatedSettingsState())

        composeRule.onNodeWithTag(SettingsScreenTestTags.SIGN_OUT_TRIGGER)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(SettingsScreenTestTags.SIGN_OUT_DIALOG)
            .captureRoboImage("src/test/screenshots/baseline/settings_sign_out_dialog_compact_phone.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun settingsSignOutDialog_typicalPhone() {
        setSettingsContent(state = populatedSettingsState())

        composeRule.onNodeWithTag(SettingsScreenTestTags.SIGN_OUT_TRIGGER)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(SettingsScreenTestTags.SIGN_OUT_DIALOG)
            .captureRoboImage("src/test/screenshots/baseline/settings_sign_out_dialog_typical_phone.png")
    }

    private fun setSettingsContent(
        state: SettingsUiState,
        fontScale: Float = DefaultFontScale,
    ) {
        composeRule.setRelateScreenshotContent(fontScale = fontScale) {
            val snackbarHostState = remember { SnackbarHostState() }
            SettingsContent(
                state = state,
                snackbarHostState = snackbarHostState,
            )
        }
    }

    private fun populatedSettingsState(): SettingsUiState {
        return SettingsUiState(
            userName = "Yash Somani",
            userEmail = "yash@example.com",
            birthdayReminders = true,
            aiWishGeneration = true,
            biometricLockEnabled = true,
            lastSyncTimestamp = "Just now",
            lastBackupTimestamp = "Today",
            geminiApiKey = "AIzaExampleKey2026",
            senderEmail = "sender@example.com",
            senderEmailPassword = "app-password",
            quietHoursStart = "22",
            quietHoursEnd = "8",
        )
    }
}
