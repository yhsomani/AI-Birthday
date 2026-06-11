package com.example.ui.screens.backup

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.R
import com.example.core.ui.theme.RelateAITheme
import com.example.ui.viewmodel.BackupRestoreUiState
import com.example.ui.viewmodel.PasswordStrength
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BackupRestoreScreenInteractionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun passphraseVisibilityExportImportDismissAndBack_dispatchExpectedActions() {
        val actions = mutableListOf<String>()
        var uiState by mutableStateOf(BackupRestoreUiState())
        var passwordVisible by mutableStateOf(false)

        composeRule.setBackupRestoreContent(
            state = { uiState },
            passwordVisible = { passwordVisible },
            onPassphraseChange = {
                uiState = uiState.copy(
                    passphrase = it,
                    passwordStrength = PasswordStrength.VERY_STRONG,
                )
            },
            onTogglePasswordVisibility = {
                passwordVisible = !passwordVisible
                actions += "visibility:$passwordVisible"
            },
            onExportRequested = {
                actions += "export"
                uiState = uiState.copy(
                    exportSuccessFileName = "backup.enc",
                    exportSuccessSizeBytes = 2_048L,
                    importSuccessCount = null,
                    errorMessage = null,
                )
            },
            onImportRequested = {
                actions += "import"
                uiState = uiState.copy(
                    exportSuccessFileName = null,
                    exportSuccessSizeBytes = 0L,
                    importSuccessCount = 7,
                    errorMessage = null,
                )
            },
            onClearStatus = {
                actions += "dismiss"
                uiState = uiState.copy(
                    exportSuccessFileName = null,
                    exportSuccessSizeBytes = 0L,
                    importSuccessCount = null,
                    errorMessage = null,
                )
            },
            onBack = { actions += "back" },
        )

        composeRule.onNodeWithContentDescription(context.getString(R.string.back))
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(BackupRestoreTestTags.PASSPHRASE_FIELD)
            .assertIsDisplayed()
            .performTextInput("Abc12345!")
        composeRule.onNodeWithTag(BackupRestoreTestTags.STRENGTH_INDICATOR)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(BackupRestoreTestTags.VISIBILITY_TOGGLE)
            .assertIsDisplayed()
            .performClick()

        composeRule.assertScrollableTagVisible(BackupRestoreTestTags.EXPORT_ACTION)
        composeRule.onNodeWithTag(BackupRestoreTestTags.EXPORT_ACTION)
            .assertIsEnabled()
            .performClick()
        composeRule.assertScrollableTagVisible(BackupRestoreTestTags.STATUS_CARD)
        composeRule.onNodeWithText(context.getString(R.string.backup_export_success_title))
            .assertIsDisplayed()

        composeRule.assertScrollableTagVisible(BackupRestoreTestTags.IMPORT_ACTION)
        composeRule.onNodeWithTag(BackupRestoreTestTags.IMPORT_ACTION)
            .assertIsEnabled()
            .performClick()
        composeRule.assertScrollableTagVisible(BackupRestoreTestTags.STATUS_CARD)
        composeRule.onNodeWithText(context.getString(R.string.backup_import_success_title))
            .assertIsDisplayed()

        composeRule.onNodeWithTag(BackupRestoreTestTags.DISMISS_STATUS)
            .assertIsDisplayed()
            .performClick()

        assertEquals("Abc12345!", uiState.passphrase)
        assertEquals(
            listOf("back", "visibility:true", "export", "import", "dismiss"),
            actions,
        )
    }

    @Test
    fun invalidLoadingSuccessAndErrorStates_renderExpectedControls() {
        var uiState by mutableStateOf(BackupRestoreUiState())

        composeRule.setBackupRestoreContent(
            state = { uiState },
        )

        composeRule.assertScrollableTagVisible(BackupRestoreTestTags.EXPORT_ACTION)
        composeRule.onNodeWithTag(BackupRestoreTestTags.EXPORT_ACTION)
            .assertIsNotEnabled()
        composeRule.assertScrollableTagVisible(BackupRestoreTestTags.IMPORT_ACTION)
        composeRule.onNodeWithTag(BackupRestoreTestTags.IMPORT_ACTION)
            .assertIsNotEnabled()

        uiState = BackupRestoreUiState(
            passphrase = "abcdef",
            passwordStrength = PasswordStrength.WEAK,
        )
        composeRule.assertScrollableTagVisible(BackupRestoreTestTags.EXPORT_ACTION)
        composeRule.onNodeWithTag(BackupRestoreTestTags.EXPORT_ACTION)
            .assertIsNotEnabled()
        composeRule.assertScrollableTagVisible(BackupRestoreTestTags.IMPORT_ACTION)
        composeRule.onNodeWithTag(BackupRestoreTestTags.IMPORT_ACTION)
            .assertIsEnabled()
        composeRule.assertScrollableTagVisible(BackupRestoreTestTags.STRENGTH_INDICATOR)
        composeRule.onNodeWithText(context.getString(R.string.backup_password_strength_weak))
            .assertIsDisplayed()

        uiState = BackupRestoreUiState(
            passphrase = "Abc12345!",
            passwordStrength = PasswordStrength.VERY_STRONG,
            isExporting = true,
        )
        composeRule.assertScrollableTagVisible(BackupRestoreTestTags.EXPORT_ACTION)
        composeRule.onNodeWithTag(BackupRestoreTestTags.EXPORT_ACTION)
            .assertIsNotEnabled()
        composeRule.onNodeWithTag(BackupRestoreTestTags.IMPORT_ACTION)
            .assertIsNotEnabled()
        composeRule.onNodeWithTag(BackupRestoreTestTags.EXPORT_PROGRESS)
            .assertIsDisplayed()

        uiState = BackupRestoreUiState(
            passphrase = "Abc12345!",
            passwordStrength = PasswordStrength.VERY_STRONG,
            isImporting = true,
        )
        composeRule.assertScrollableTagVisible(BackupRestoreTestTags.IMPORT_ACTION)
        composeRule.onNodeWithTag(BackupRestoreTestTags.IMPORT_ACTION)
            .assertIsNotEnabled()
        composeRule.onNodeWithTag(BackupRestoreTestTags.EXPORT_ACTION)
            .assertIsNotEnabled()
        composeRule.onNodeWithTag(BackupRestoreTestTags.IMPORT_PROGRESS)
            .assertIsDisplayed()

        uiState = BackupRestoreUiState(
            passphrase = "Abc12345!",
            passwordStrength = PasswordStrength.VERY_STRONG,
            errorMessage = context.getString(R.string.backup_error_wrong_passphrase),
        )
        composeRule.assertScrollableTagVisible(BackupRestoreTestTags.STATUS_CARD)
        composeRule.onNodeWithText(context.getString(R.string.backup_action_failed_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.backup_error_wrong_passphrase))
            .assertIsDisplayed()

        uiState = BackupRestoreUiState(
            passphrase = "Abc12345!",
            passwordStrength = PasswordStrength.VERY_STRONG,
            importSuccessCount = 4,
        )
        composeRule.assertScrollableTagVisible(BackupRestoreTestTags.STATUS_CARD)
        composeRule.onNodeWithText(context.getString(R.string.backup_import_success_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.backup_import_success_details, 4))
            .assertIsDisplayed()
    }

    private fun ComposeContentTestRule.setBackupRestoreContent(
        state: () -> BackupRestoreUiState,
        passwordVisible: () -> Boolean = { false },
        onPassphraseChange: (String) -> Unit = {},
        onTogglePasswordVisibility: () -> Unit = {},
        onExportRequested: () -> Unit = {},
        onImportRequested: () -> Unit = {},
        onClearStatus: () -> Unit = {},
        onBack: () -> Unit = {},
    ) {
        setContent {
            RelateAITheme {
                BackupRestoreContent(
                    uiState = state(),
                    passwordVisible = passwordVisible(),
                    onPassphraseChange = onPassphraseChange,
                    onTogglePasswordVisibility = onTogglePasswordVisibility,
                    onExportRequested = onExportRequested,
                    onImportRequested = onImportRequested,
                    onClearStatus = onClearStatus,
                    onBack = onBack,
                )
            }
        }
    }

    private fun ComposeContentTestRule.assertScrollableTagVisible(tag: String) {
        onNode(hasScrollAction()).performScrollToNode(hasTestTag(tag))
        onNodeWithTag(tag).assertIsDisplayed()
    }
}
