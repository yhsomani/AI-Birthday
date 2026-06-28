package com.example.ui.screenshots

import android.app.Application
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.domain.service.BackupRestoreMode
import com.example.ui.screens.backup.BackupRestoreContent
import com.example.ui.screens.backup.BackupRestoreTestTags
import com.example.ui.viewmodel.BackupImportPreviewUiModel
import com.example.ui.viewmodel.BackupRestoreUiState
import com.example.ui.viewmodel.PasswordStrength
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
class BackupRestoreScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun backupRestorePassphrase_compactPhone() {
        setBackupRestoreContent(state = BackupRestoreUiState())

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/backup_restore_passphrase_compact_phone.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun backupRestorePassphrase_typicalPhone() {
        setBackupRestoreContent(state = BackupRestoreUiState())

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/backup_restore_passphrase_typical_phone.png")
    }

    @Test
    fun backupRestoreActions_compactPhoneLargeFont() {
        setBackupRestoreContent(
            state = securePassphraseState(),
            fontScale = LargeFontScale,
        )

        scrollTo(BackupRestoreTestTags.IMPORT_ACTION)
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/backup_restore_actions_compact_phone_large_font.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun backupRestoreActions_typicalPhone() {
        setBackupRestoreContent(state = securePassphraseState())

        scrollTo(BackupRestoreTestTags.IMPORT_ACTION)
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/backup_restore_actions_typical_phone.png")
    }

    @Test
    fun backupRestoreImportPreview_compactPhoneLargeFont() {
        setBackupRestoreContent(
            state = securePassphraseState().copy(
                importPreview = BackupImportPreviewUiModel(
                    backupVersion = 2,
                    appVersion = "1.4.0",
                    exportedAtMs = 1_767_614_400_000L,
                    totalRecords = 128,
                    restoreMode = BackupRestoreMode.REPLACE,
                ),
            ),
            fontScale = LargeFontScale,
        )

        scrollTo(BackupRestoreTestTags.STATUS_CARD)
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/backup_restore_import_preview_compact_phone_large_font.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun backupRestoreImportPreview_typicalPhone() {
        setBackupRestoreContent(
            state = securePassphraseState().copy(
                importPreview = BackupImportPreviewUiModel(
                    backupVersion = 2,
                    appVersion = "1.4.0",
                    exportedAtMs = 1_767_614_400_000L,
                    totalRecords = 128,
                    restoreMode = BackupRestoreMode.REPLACE,
                ),
            ),
        )

        scrollTo(BackupRestoreTestTags.STATUS_CARD)
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/backup_restore_import_preview_typical_phone.png")
    }

    @Test
    @Config(qualifiers = "hi-rIN-w360dp-h800dp-xhdpi")
    fun backupRestoreImportPreview_compactPhoneHindiLargeFont() {
        setBackupRestoreContent(
            state = securePassphraseState().copy(
                importPreview = BackupImportPreviewUiModel(
                    backupVersion = 2,
                    appVersion = "1.4.0",
                    exportedAtMs = 1_767_614_400_000L,
                    totalRecords = 128,
                    restoreMode = BackupRestoreMode.REPLACE,
                ),
            ),
            fontScale = LargeFontScale,
        )

        scrollTo(BackupRestoreTestTags.STATUS_CARD)
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/backup_restore_import_preview_compact_phone_hindi_large_font.png")
    }

    @Test
    fun backupRestoreExporting_compactPhone() {
        setBackupRestoreContent(
            state = securePassphraseState().copy(isExporting = true),
            animationFrameMillis = ProgressAnimationFrameMillis,
        )

        scrollTo(BackupRestoreTestTags.EXPORT_ACTION)
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/backup_restore_exporting_compact_phone.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun backupRestoreExporting_typicalPhone() {
        setBackupRestoreContent(
            state = securePassphraseState().copy(isExporting = true),
            animationFrameMillis = ProgressAnimationFrameMillis,
        )

        scrollTo(BackupRestoreTestTags.EXPORT_ACTION)
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/backup_restore_exporting_typical_phone.png")
    }

    @Test
    fun backupRestoreError_compactPhone() {
        setBackupRestoreContent(
            state = securePassphraseState().copy(
                errorMessage = "The passphrase does not match this backup.",
            ),
        )

        scrollTo(BackupRestoreTestTags.STATUS_CARD)
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/backup_restore_error_compact_phone.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun backupRestoreError_typicalPhone() {
        setBackupRestoreContent(
            state = securePassphraseState().copy(
                errorMessage = "The passphrase does not match this backup.",
            ),
        )

        scrollTo(BackupRestoreTestTags.STATUS_CARD)
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/backup_restore_error_typical_phone.png")
    }

    private fun setBackupRestoreContent(
        state: BackupRestoreUiState,
        fontScale: Float = DefaultFontScale,
        animationFrameMillis: Long? = null,
    ) {
        if (animationFrameMillis != null) {
            composeRule.mainClock.autoAdvance = false
        }
        composeRule.setRelateScreenshotContent(fontScale = fontScale) {
            BackupRestoreContent(
                uiState = state,
                passwordVisible = false,
                onPassphraseChange = {},
                onTogglePasswordVisibility = {},
                onExportRequested = {},
                onImportRequested = {},
                onConfirmImport = {},
                onClearStatus = {},
                onBack = {},
            )
        }
        if (animationFrameMillis != null) {
            composeRule.mainClock.advanceTimeBy(animationFrameMillis)
            composeRule.waitForIdle()
        }
    }

    private fun scrollTo(tag: String) {
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasTestTag(tag))
    }

    private fun securePassphraseState(): BackupRestoreUiState {
        return BackupRestoreUiState(
            passphrase = "BirthdayVault2026!",
            passwordStrength = PasswordStrength.VERY_STRONG,
        )
    }

    private companion object {
        const val ProgressAnimationFrameMillis = 750L
    }
}
