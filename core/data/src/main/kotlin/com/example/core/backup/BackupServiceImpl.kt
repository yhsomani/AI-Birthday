package com.example.core.backup

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import androidx.room.withTransaction
import com.example.core.db.AppDatabase
import com.example.core.prefs.SecurePrefs
import com.example.core.resilience.StructuredLogger
import com.example.domain.service.BackupDocumentReference
import com.example.domain.service.BackupExportResult
import com.example.domain.service.BackupFailureReason
import com.example.domain.service.BackupImportResult
import com.example.domain.service.BackupOperationResult
import com.example.domain.service.BackupPreviewResult
import com.example.domain.service.BackupRestoreMode
import com.example.domain.service.BackupService
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.AEADBadTagException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class BackupServiceImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val securePrefs: SecurePrefs,
) : BackupService {

    private val payloadCodec = BackupPayloadCodec()

    override suspend fun exportBackup(
        outputDocument: BackupDocumentReference?,
        passphrase: String,
    ): BackupOperationResult<BackupExportResult> = withContext(Dispatchers.IO) {
        if (passphrase.isBlank()) {
            return@withContext BackupOperationResult.Failure(BackupFailureReason.BLANK_PASSPHRASE)
        }

        try {
            val backupFile = createEncryptedBackupFile(passphrase)
            val backupFileName = backupFile.name
            val backupSizeBytes = backupFile.length()
            val outputUri = outputDocument?.toAndroidUri()
            if (outputUri != null) {
                val didWrite = try {
                    context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                        backupFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    } != null
                } catch (e: Exception) {
                    StructuredLogger.e(TAG, "Failed to write backup to selected destination", e)
                    false
                } finally {
                    deleteInternalExportCopy(backupFile)
                }

                if (!didWrite) {
                    return@withContext BackupOperationResult.Failure(BackupFailureReason.CANNOT_WRITE_BACKUP)
                }
            }

            try {
                securePrefs.setLastBackupMs(System.currentTimeMillis())
            } catch (e: Exception) {
                StructuredLogger.e(TAG, "Failed to save last backup timestamp", e)
            }

            StructuredLogger.i(
                TAG,
                "Encrypted backup created",
                mapOf("sizeBytes" to backupSizeBytes.toString()),
            )
            BackupOperationResult.Success(
                BackupExportResult(
                    fileName = backupFileName,
                    sizeBytes = backupSizeBytes,
                )
            )
        } catch (e: Exception) {
            StructuredLogger.e(TAG, "Failed to create encrypted backup", e)
            BackupOperationResult.Failure(BackupFailureReason.CANNOT_CREATE_BACKUP)
        }
    }

    override suspend fun importBackup(
        inputDocument: BackupDocumentReference,
        passphrase: String,
    ): BackupOperationResult<BackupImportResult> = withContext(Dispatchers.IO) {
        if (passphrase.isBlank()) {
            return@withContext BackupOperationResult.Failure(BackupFailureReason.BLANK_PASSPHRASE)
        }

        val backup = when (val result = readValidatedBackup(inputDocument, passphrase)) {
            is BackupOperationResult.Success -> result.value
            is BackupOperationResult.Failure -> return@withContext result
        }

        try {
            val count = database.withTransaction {
                database.replaceWithBackupPayload(backup)
            }
            backup.preferences?.let { restorePreferences(it) }
            StructuredLogger.i(TAG, "Encrypted backup restored", mapOf("recordsRestored" to count.toString()))
            BackupOperationResult.Success(
                BackupImportResult(
                    recordsRestored = count,
                    restoreMode = BackupRestoreMode.REPLACE,
                )
            )
        } catch (e: SQLiteConstraintException) {
            StructuredLogger.e(TAG, "Backup restore failed due to database constraint", e)
            BackupOperationResult.Failure(BackupFailureReason.DATABASE_ERROR)
        } catch (e: Exception) {
            StructuredLogger.e(TAG, "Backup restore failed during database transaction", e)
            BackupOperationResult.Failure(BackupFailureReason.DATABASE_ERROR)
        }
    }

    override suspend fun previewBackup(
        inputDocument: BackupDocumentReference,
        passphrase: String,
    ): BackupOperationResult<BackupPreviewResult> = withContext(Dispatchers.IO) {
        if (passphrase.isBlank()) {
            return@withContext BackupOperationResult.Failure(BackupFailureReason.BLANK_PASSPHRASE)
        }

        when (val result = readValidatedBackup(inputDocument, passphrase)) {
            is BackupOperationResult.Success -> BackupOperationResult.Success(result.value.toPreviewResult())
            is BackupOperationResult.Failure -> result
        }
    }

    private fun readValidatedBackup(
        inputDocument: BackupDocumentReference,
        passphrase: String,
    ): BackupOperationResult<BackupPayloadDto> {
        val inputUri = inputDocument.toAndroidUri()
        val encryptedJson = try {
            context.contentResolver.openInputStream(inputUri)?.use { inputStream ->
                readUtf8TextWithLimit(inputStream)
            } ?: return BackupOperationResult.Failure(BackupFailureReason.CANNOT_READ_BACKUP)
        } catch (e: BackupFileTooLargeException) {
            StructuredLogger.w(TAG, "Backup file exceeds import size limit")
            return BackupOperationResult.Failure(BackupFailureReason.INVALID_BACKUP_FILE)
        } catch (e: Exception) {
            StructuredLogger.e(TAG, "Failed to read backup file", e)
            return BackupOperationResult.Failure(BackupFailureReason.CANNOT_READ_BACKUP)
        }

        val backup = try {
            val json = BackupEncryption.decrypt(encryptedJson, passphrase)
            payloadCodec.fromJson(json)
                ?: return BackupOperationResult.Failure(BackupFailureReason.INVALID_BACKUP_FILE)
        } catch (e: AEADBadTagException) {
            StructuredLogger.w(TAG, "Backup decrypt failed authentication check")
            return BackupOperationResult.Failure(BackupFailureReason.WRONG_PASSPHRASE)
        } catch (e: BackupEncryptionException) {
            StructuredLogger.w(TAG, "Backup file failed validation", e)
            return BackupOperationResult.Failure(BackupFailureReason.INVALID_BACKUP_FILE)
        } catch (e: JsonDataException) {
            StructuredLogger.w(TAG, "Backup JSON data is invalid", e)
            return BackupOperationResult.Failure(BackupFailureReason.INVALID_BACKUP_FILE)
        } catch (e: JsonEncodingException) {
            StructuredLogger.w(TAG, "Backup JSON encoding is invalid", e)
            return BackupOperationResult.Failure(BackupFailureReason.INVALID_BACKUP_FILE)
        } catch (e: Exception) {
            StructuredLogger.e(TAG, "Backup import failed before database restore", e)
            return BackupOperationResult.Failure(BackupFailureReason.INVALID_BACKUP_FILE)
        }

        val backupVersion = backup.manifest?.backupVersion ?: backup.version
        if (backupVersion > CURRENT_BACKUP_VERSION) {
            return BackupOperationResult.Failure(BackupFailureReason.UNSUPPORTED_VERSION)
        }
        if (!payloadCodec.hasValidManifestChecksum(backup)) {
            StructuredLogger.w(TAG, "Backup manifest checksum mismatch")
            return BackupOperationResult.Failure(BackupFailureReason.INVALID_BACKUP_FILE)
        }

        return BackupOperationResult.Success(backup)
    }

    private fun BackupDocumentReference.toAndroidUri(): Uri = Uri.parse(uriString)

    private fun deleteInternalExportCopy(backupFile: File) {
        if (!backupFile.exists()) return
        if (!backupFile.delete()) {
            StructuredLogger.w(
                TAG,
                "Failed to delete internal encrypted backup copy",
                extras = mapOf("fileName" to backupFile.name),
            )
        }
    }

    private suspend fun createEncryptedBackupFile(passphrase: String): File {
        val timestampMs = System.currentTimeMillis()
        val preferences = capturePreferences()
        val recordSnapshot = database.captureBackupRecordSnapshot(preferences)
        val backup = BackupPayloadDto(
            version = CURRENT_BACKUP_VERSION,
            timestampMs = timestampMs,
            manifest = BackupManifestDto(
                backupVersion = CURRENT_BACKUP_VERSION,
                appVersion = resolveAppVersionName(),
                exportedAtMs = timestampMs,
                counts = recordSnapshot.counts(),
                dataChecksumSha256 = payloadCodec.checksumFor(recordSnapshot),
            ),
            contacts = recordSnapshot.contacts,
            events = recordSnapshot.events,
            pendingMessages = recordSnapshot.pendingMessages,
            sentMessages = recordSnapshot.sentMessages,
            styleProfile = recordSnapshot.styleProfile,
            memoryNotes = recordSnapshot.memoryNotes,
            giftHistory = recordSnapshot.giftHistory,
            activityLogs = recordSnapshot.activityLogs,
            messageFeedback = recordSnapshot.messageFeedback,
            dispatchAttempts = recordSnapshot.dispatchAttempts,
            preferences = preferences,
        )

        val encryptedJson = BackupEncryption.encrypt(payloadCodec.toJson(backup), passphrase)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(context.filesDir, "relateai_backup_$timestamp.enc").apply {
            writeText(encryptedJson)
        }
    }

    private fun capturePreferences(): BackupPreferencesDto {
        return try {
            BackupPreferencesDto.from(securePrefs)
        } catch (e: Exception) {
            StructuredLogger.w(TAG, "Failed to read backup preferences; using defaults", e)
            BackupPreferencesDto.defaults()
        }
    }

    private fun restorePreferences(preferences: BackupPreferencesDto) {
        try {
            preferences.restoreTo(securePrefs)
        } catch (e: Exception) {
            StructuredLogger.w(TAG, "Failed to restore backup preferences", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveAppVersionName(): String {
        return try {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
                ?.takeIf { it.isNotBlank() }
                ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    private companion object {
        const val TAG = "BackupService"
    }
}
