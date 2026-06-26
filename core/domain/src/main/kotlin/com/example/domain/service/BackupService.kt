package com.example.domain.service

import android.net.Uri

data class BackupExportResult(
    val fileName: String,
    val sizeBytes: Long,
)

data class BackupImportResult(
    val recordsRestored: Int,
    val restoreMode: BackupRestoreMode = BackupRestoreMode.REPLACE,
)

data class BackupPreviewResult(
    val backupVersion: Int,
    val appVersion: String,
    val exportedAtMs: Long,
    val counts: BackupRecordCounts,
    val restoreMode: BackupRestoreMode = BackupRestoreMode.REPLACE,
) {
    val totalRecords: Int
        get() = counts.totalRecords
}

enum class BackupRestoreMode {
    REPLACE
}

data class BackupRecordCounts(
    val contacts: Int = 0,
    val events: Int = 0,
    val pendingMessages: Int = 0,
    val sentMessages: Int = 0,
    val styleProfiles: Int = 0,
    val memoryNotes: Int = 0,
    val giftHistory: Int = 0,
    val activityLogs: Int = 0,
    val messageFeedback: Int = 0,
    val preferences: Int = 0,
) {
    val totalRecords: Int
        get() = contacts +
            events +
            pendingMessages +
            sentMessages +
            styleProfiles +
            memoryNotes +
            giftHistory +
            activityLogs +
            messageFeedback +
            preferences
}

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

    suspend fun previewBackup(inputUri: Uri, passphrase: String): BackupOperationResult<BackupPreviewResult>

    suspend fun importBackup(inputUri: Uri, passphrase: String): BackupOperationResult<BackupImportResult>
}
