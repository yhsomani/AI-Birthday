package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.backup.BackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class BackupRestoreUiState(
    val passphrase: String = "",
    val passwordStrength: PasswordStrength = PasswordStrength.WEAK,
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val exportSuccessFile: File? = null,
    val importSuccessCount: Int? = null,
    val errorMessage: String? = null
)

enum class PasswordStrength {
    WEAK, FAIR, STRONG, VERY_STRONG
}

@HiltViewModel
class BackupRestoreViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(BackupRestoreUiState())
    val uiState: StateFlow<BackupRestoreUiState> = _uiState.asStateFlow()

    fun updatePassphrase(passphrase: String) {
        val strength = calculateStrength(passphrase)
        _uiState.value = _uiState.value.copy(
            passphrase = passphrase,
            passwordStrength = strength,
            errorMessage = null,
            exportSuccessFile = null,
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

    fun exportBackup(context: Context, outputUri: Uri? = null, onShare: ((File) -> Unit)? = null) {
        val passphrase = _uiState.value.passphrase
        if (passphrase.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Passphrase cannot be blank")
            return
        }
        if (_uiState.value.passwordStrength == PasswordStrength.WEAK) {
            _uiState.value = _uiState.value.copy(errorMessage = "Password is too weak. Must be at least 8 chars with digit, uppercase, and special char.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true, errorMessage = null)
            try {
                val file = BackupManager.createBackup(context, passphrase)
                if (outputUri != null) {
                    context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                        file.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                }
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    exportSuccessFile = file
                )
                onShare?.invoke(file)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    errorMessage = e.localizedMessage ?: "Export failed"
                )
            }
        }
    }

    fun importBackup(context: Context, uri: Uri) {
        val passphrase = _uiState.value.passphrase
        if (passphrase.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Passphrase cannot be blank")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isImporting = true, errorMessage = null)
            val result = BackupManager.restoreBackup(context, uri, passphrase)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isImporting = false,
                    importSuccessCount = result.getOrNull()
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isImporting = false,
                    errorMessage = result.exceptionOrNull()?.localizedMessage ?: "Import failed. Check password."
                )
            }
        }
    }

    fun clearStatus() {
        _uiState.value = _uiState.value.copy(
            exportSuccessFile = null,
            importSuccessCount = null,
            errorMessage = null
        )
    }
}
