package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.domain.service.BackupFailureReason
import com.example.domain.service.BackupOperationResult
import com.example.domain.service.BackupService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BackupRestoreUiState(
    val passphrase: String = "",
    val passwordStrength: PasswordStrength = PasswordStrength.WEAK,
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val exportSuccessFileName: String? = null,
    val exportSuccessSizeBytes: Long = 0L,
    val importSuccessCount: Int? = null,
    val errorMessage: String? = null
)

enum class PasswordStrength {
    WEAK, FAIR, STRONG, VERY_STRONG
}

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val backupService: BackupService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupRestoreUiState())
    val uiState: StateFlow<BackupRestoreUiState> = _uiState.asStateFlow()

    fun updatePassphrase(passphrase: String) {
        val strength = calculateStrength(passphrase)
        _uiState.value = _uiState.value.copy(
            passphrase = passphrase,
            passwordStrength = strength,
            errorMessage = null,
            exportSuccessFileName = null,
            exportSuccessSizeBytes = 0L,
            importSuccessCount = null
        )
    }

    private fun calculateStrength(passphrase: String): PasswordStrength {
        if (passphrase.length < 6) return PasswordStrength.WEAK
        var score = 0
        if (passphrase.length >= 8) score++
        if (passphrase.any { it.isUpperCase() }) score++
        if (passphrase.any { it.isDigit() }) score++
        if (passphrase.any { it in "!@#$%^&*-_" }) score++
        
        return when (score) {
            4 -> PasswordStrength.VERY_STRONG
            3 -> PasswordStrength.STRONG
            2 -> PasswordStrength.FAIR
            else -> PasswordStrength.WEAK
        }
    }

    fun exportBackup(outputUri: Uri? = null) {
        val passphrase = _uiState.value.passphrase
        if (passphrase.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = context.getString(R.string.backup_error_blank_passphrase))
            return
        }
        if (_uiState.value.passwordStrength == PasswordStrength.WEAK) {
            _uiState.value = _uiState.value.copy(errorMessage = context.getString(R.string.backup_error_weak_passphrase))
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true, errorMessage = null)
            when (val result = backupService.exportBackup(outputUri, passphrase)) {
                is BackupOperationResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        exportSuccessFileName = result.value.fileName,
                        exportSuccessSizeBytes = result.value.sizeBytes,
                    )
                }
                is BackupOperationResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        errorMessage = result.reason.toErrorMessage(),
                    )
                }
            }
        }
    }

    fun importBackup(uri: Uri) {
        val passphrase = _uiState.value.passphrase
        if (passphrase.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = context.getString(R.string.backup_error_blank_passphrase))
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isImporting = true, errorMessage = null)
            when (val result = backupService.importBackup(uri, passphrase)) {
                is BackupOperationResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isImporting = false,
                        importSuccessCount = result.value.recordsRestored,
                    )
                }
                is BackupOperationResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isImporting = false,
                        errorMessage = result.reason.toErrorMessage(),
                    )
                }
            }
        }
    }

    fun clearStatus() {
        _uiState.value = _uiState.value.copy(
            exportSuccessFileName = null,
            exportSuccessSizeBytes = 0L,
            importSuccessCount = null,
            errorMessage = null
        )
    }

    private fun BackupFailureReason.toErrorMessage(): String {
        val resId = when (this) {
            BackupFailureReason.BLANK_PASSPHRASE -> R.string.backup_error_blank_passphrase
            BackupFailureReason.CANNOT_CREATE_BACKUP -> R.string.backup_error_export_failed
            BackupFailureReason.CANNOT_WRITE_BACKUP -> R.string.backup_error_write_failed
            BackupFailureReason.CANNOT_READ_BACKUP -> R.string.backup_error_read_failed
            BackupFailureReason.INVALID_BACKUP_FILE -> R.string.backup_error_invalid_file
            BackupFailureReason.WRONG_PASSPHRASE -> R.string.backup_error_wrong_passphrase
            BackupFailureReason.UNSUPPORTED_VERSION -> R.string.backup_error_unsupported_version
            BackupFailureReason.DATABASE_ERROR -> R.string.backup_error_database
        }
        return context.getString(resId)
    }
}
