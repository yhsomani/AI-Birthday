package com.example.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupRestoreViewModelTest {

    @Test
    fun `passphrase strength calculation works correctly`() {
        val viewModel = BackupRestoreViewModel()

        viewModel.updatePassphrase("123")
        assertEquals(PasswordStrength.WEAK, viewModel.uiState.value.passwordStrength)

        viewModel.updatePassphrase("123456")
        assertEquals(PasswordStrength.WEAK, viewModel.uiState.value.passwordStrength)

        viewModel.updatePassphrase("Abc12345")
        assertEquals(PasswordStrength.STRONG, viewModel.uiState.value.passwordStrength)

        viewModel.updatePassphrase("Abc12345!")
        assertEquals(PasswordStrength.VERY_STRONG, viewModel.uiState.value.passwordStrength)
    }
}
