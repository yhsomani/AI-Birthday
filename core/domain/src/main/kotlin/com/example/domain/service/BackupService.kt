package com.example.domain.service

import android.net.Uri

data class BackupExportResult(
    val fileName: String,
    val sizeBytes: Long,
)

data class BackupImportResult(
    val recordsRestored: Int,
)

enum class BackupFailureReason {
    BLANK_PASSPHRASE,
    CANNOT_CREATE_BACKUP,
    CANNOT_WRITE_BACKUP,
    CANNOT_READ_BACKUP,
    INVALID_BACKUP_FILE,
    WRONG_PASSPHRASE,
    UNSUPPORTED_VERSION,
    DATABASE_ERROR,
}

sealed class BackupOperationResult<out T> {
    data class Success<T>(val value: T) : BackupOperationResult<T>()
    data class Failure(val reason: BackupFailureReason) : BackupOperationResult<Nothing>()
}

interface BackupService {
    suspend fun exportBackup(outputUri: Uri?, passphrase: String): BackupOperationResult<BackupExportResult>

    suspend fun importBackup(inputUri: Uri, passphrase: String): BackupOperationResult<BackupImportResult>
}
