package com.example.ui.viewmodel

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.domain.service.BackupExportResult
import com.example.domain.service.BackupFailureReason
import com.example.domain.service.BackupImportResult
import com.example.domain.service.BackupOperationResult
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
            val viewModel = viewModel(
                FakeBackupService(
                    importResult = BackupOperationResult.Failure(BackupFailureReason.WRONG_PASSPHRASE)
                )
            )

            viewModel.updatePassphrase("Abc12345!")
            viewModel.importBackup(Uri.EMPTY)
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(
                "The passphrase does not match this backup.",
                viewModel.uiState.value.errorMessage,
            )
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
        private val importResult: BackupOperationResult<BackupImportResult> =
            BackupOperationResult.Success(BackupImportResult(3)),
    ) : BackupService {
        var exportCalled = false

        override suspend fun exportBackup(
            outputUri: Uri?,
            passphrase: String,
        ): BackupOperationResult<BackupExportResult> {
            exportCalled = true
            return exportResult
        }

        override suspend fun importBackup(
            inputUri: Uri,
            passphrase: String,
        ): BackupOperationResult<BackupImportResult> {
            return importResult
        }
    }
}
