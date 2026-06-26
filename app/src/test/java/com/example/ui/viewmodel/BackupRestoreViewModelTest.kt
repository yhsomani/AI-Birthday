package com.example.ui.viewmodel

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.domain.service.BackupExportResult
import com.example.domain.service.BackupFailureReason
import com.example.domain.service.BackupImportResult
import com.example.domain.service.BackupOperationResult
import com.example.domain.service.BackupPreviewResult
import com.example.domain.service.BackupRecordCounts
import com.example.domain.service.BackupRestoreMode
import com.example.domain.service.BackupService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@org.robolectric.annotation.Config(sdk = [34])
class BackupRestoreViewModelTest {

    @Test
    fun `passphrase strength calculation works correctly`() {
        val viewModel = viewModel()

        viewModel.updatePassphrase("123")
        assertEquals(PasswordStrength.WEAK, viewModel.uiState.value.passwordStrength)

        viewModel.updatePassphrase("123456")
        assertEquals(PasswordStrength.WEAK, viewModel.uiState.value.passwordStrength)

        viewModel.updatePassphrase("Abc12345")
        assertEquals(PasswordStrength.STRONG, viewModel.uiState.value.passwordStrength)

        viewModel.updatePassphrase("Abc12345!")
        assertEquals(PasswordStrength.VERY_STRONG, viewModel.uiState.value.passwordStrength)
    }

    @Test
    fun `blank passphrase shows localized validation error`() {
        val viewModel = viewModel()

        viewModel.exportBackup()

        assertEquals("Passphrase cannot be blank.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `weak passphrase blocks export before service call`() {
        val service = FakeBackupService()
        val viewModel = viewModel(service)

        viewModel.updatePassphrase("abcdef")
        viewModel.exportBackup()

        assertFalse(service.exportCalled)
        assertEquals(
            "Password is too weak. Use at least 8 characters with an uppercase letter, a number, and a symbol.",
            viewModel.uiState.value.errorMessage,
        )
    }

    @Test
    fun `blank passphrase blocks import before service call`() {
        val service = FakeBackupService()
        val viewModel = viewModel(service)

        viewModel.importBackup(Uri.EMPTY)

        assertFalse(service.previewCalled)
        assertFalse(service.importCalled)
        assertEquals("Passphrase cannot be blank.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `export success updates file status`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = viewModel(
                FakeBackupService(
                    exportResult = BackupOperationResult.Success(
                        BackupExportResult("backup.enc", 1234L)
                    )
                )
            )

            viewModel.updatePassphrase("Abc12345!")
            viewModel.exportBackup()
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals("backup.enc", viewModel.uiState.value.exportSuccessFileName)
            assertEquals(1234L, viewModel.uiState.value.exportSuccessSizeBytes)
            assertNull(viewModel.uiState.value.errorMessage)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `import failure maps to stable localized message`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val service = FakeBackupService(
                previewResult = BackupOperationResult.Failure(BackupFailureReason.WRONG_PASSPHRASE)
            )
            val viewModel = viewModel(service)

            viewModel.updatePassphrase("Abc12345!")
            viewModel.importBackup(Uri.EMPTY)
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(
                "The passphrase does not match this backup.",
                viewModel.uiState.value.errorMessage,
            )
            assertFalse(service.importCalled)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `import first previews backup without mutating database`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val service = FakeBackupService(
                previewResult = BackupOperationResult.Success(
                    BackupPreviewResult(
                        backupVersion = 2,
                        appVersion = "1.0",
                        exportedAtMs = 1_700_000_000_000,
                        counts = BackupRecordCounts(contacts = 2, events = 1),
                    )
                )
            )
            val viewModel = viewModel(service)

            viewModel.updatePassphrase("Abc12345!")
            viewModel.importBackup(Uri.parse("content://backup"))
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(2, viewModel.uiState.value.importPreview?.backupVersion)
            assertEquals("1.0", viewModel.uiState.value.importPreview?.appVersion)
            assertEquals(3, viewModel.uiState.value.importPreview?.totalRecords)
            assertEquals(BackupRestoreMode.REPLACE, viewModel.uiState.value.importPreview?.restoreMode)
            assertFalse(service.importCalled)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `confirm import restores after preview`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val service = FakeBackupService(
                previewResult = BackupOperationResult.Success(
                    BackupPreviewResult(
                        backupVersion = 2,
                        appVersion = "1.0",
                        exportedAtMs = 1_700_000_000_000,
                        counts = BackupRecordCounts(contacts = 3),
                    )
                ),
                importResult = BackupOperationResult.Success(BackupImportResult(3)),
            )
            val viewModel = viewModel(service)

            viewModel.updatePassphrase("Abc12345!")
            viewModel.importBackup(Uri.parse("content://backup"))
            dispatcher.scheduler.advanceUntilIdle()
            viewModel.confirmImportBackup()
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(3, viewModel.uiState.value.importSuccessCount)
            assertNull(viewModel.uiState.value.importPreview)
            assertEquals(Uri.parse("content://backup"), service.importUri)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun viewModel(
        backupService: FakeBackupService = FakeBackupService(),
    ): BackupRestoreViewModel {
        return BackupRestoreViewModel(
            context = ApplicationProvider.getApplicationContext(),
            backupService = backupService,
        )
    }

    private class FakeBackupService(
        private val exportResult: BackupOperationResult<BackupExportResult> =
            BackupOperationResult.Success(BackupExportResult("relateai_backup.enc", 42L)),
        private val previewResult: BackupOperationResult<BackupPreviewResult> =
            BackupOperationResult.Success(
                BackupPreviewResult(
                    backupVersion = 2,
                    appVersion = "test",
                    exportedAtMs = 1L,
                    counts = BackupRecordCounts(contacts = 1),
                )
            ),
        private val importResult: BackupOperationResult<BackupImportResult> =
            BackupOperationResult.Success(BackupImportResult(3)),
    ) : BackupService {
        var exportCalled = false
        var previewCalled = false
        var importCalled = false
        var importUri: Uri? = null

        override suspend fun exportBackup(
            outputUri: Uri?,
            passphrase: String,
        ): BackupOperationResult<BackupExportResult> {
            exportCalled = true
            return exportResult
        }

        override suspend fun previewBackup(
            inputUri: Uri,
            passphrase: String,
        ): BackupOperationResult<BackupPreviewResult> {
            previewCalled = true
            return previewResult
        }

        override suspend fun importBackup(
            inputUri: Uri,
            passphrase: String,
        ): BackupOperationResult<BackupImportResult> {
            importCalled = true
            importUri = inputUri
            return importResult
        }
    }
}
