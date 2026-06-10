package com.example.core.backup

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import androidx.room.withTransaction
import com.example.core.db.AppDatabase
import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.EventEntity
import com.example.core.db.entities.GiftHistoryEntity
import com.example.core.db.entities.MemoryNoteEntity
import com.example.core.db.entities.PendingMessageEntity
import com.example.core.db.entities.SentMessageEntity
import com.example.core.db.entities.StyleProfileEntity
import com.example.core.prefs.SecurePrefs
import com.example.core.resilience.StructuredLogger
import com.example.domain.service.BackupExportResult
import com.example.domain.service.BackupFailureReason
import com.example.domain.service.BackupImportResult
import com.example.domain.service.BackupOperationResult
import com.example.domain.service.BackupService
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.AEADBadTagException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_IMPORT_BYTES = 25 * 1024 * 1024

@Singleton
class BackupServiceImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val securePrefs: SecurePrefs,
) : BackupService {

    private val adapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(BackupData::class.java)
        .indent("  ")

    override suspend fun exportBackup(
        outputUri: Uri?,
        passphrase: String,
    ): BackupOperationResult<BackupExportResult> = withContext(Dispatchers.IO) {
        if (passphrase.isBlank()) {
            return@withContext BackupOperationResult.Failure(BackupFailureReason.BLANK_PASSPHRASE)
        }

        try {
            val backupFile = createEncryptedBackupFile(passphrase)
            if (outputUri != null) {
                try {
                    context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                        backupFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    } ?: return@withContext BackupOperationResult.Failure(BackupFailureReason.CANNOT_WRITE_BACKUP)
                } catch (e: Exception) {
                    StructuredLogger.e(TAG, "Failed to write backup to selected destination", e)
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
                mapOf("sizeBytes" to backupFile.length().toString()),
            )
            BackupOperationResult.Success(
                BackupExportResult(
                    fileName = backupFile.name,
                    sizeBytes = backupFile.length(),
                )
            )
        } catch (e: Exception) {
            StructuredLogger.e(TAG, "Failed to create encrypted backup", e)
            BackupOperationResult.Failure(BackupFailureReason.CANNOT_CREATE_BACKUP)
        }
    }

    override suspend fun importBackup(
        inputUri: Uri,
        passphrase: String,
    ): BackupOperationResult<BackupImportResult> = withContext(Dispatchers.IO) {
        if (passphrase.isBlank()) {
            return@withContext BackupOperationResult.Failure(BackupFailureReason.BLANK_PASSPHRASE)
        }

        val encryptedJson = try {
            context.contentResolver.openInputStream(inputUri)?.use { inputStream ->
                readUtf8TextWithLimit(inputStream)
            }
                ?: return@withContext BackupOperationResult.Failure(BackupFailureReason.CANNOT_READ_BACKUP)
        } catch (e: BackupFileTooLargeException) {
            StructuredLogger.w(TAG, "Backup file exceeds import size limit")
            return@withContext BackupOperationResult.Failure(BackupFailureReason.INVALID_BACKUP_FILE)
        } catch (e: Exception) {
            StructuredLogger.e(TAG, "Failed to read backup file", e)
            return@withContext BackupOperationResult.Failure(BackupFailureReason.CANNOT_READ_BACKUP)
        }

        val backup = try {
            val json = BackupEncryption.decrypt(encryptedJson, passphrase)
            adapter.fromJson(json)
                ?: return@withContext BackupOperationResult.Failure(BackupFailureReason.INVALID_BACKUP_FILE)
        } catch (e: AEADBadTagException) {
            StructuredLogger.w(TAG, "Backup decrypt failed authentication check")
            return@withContext BackupOperationResult.Failure(BackupFailureReason.WRONG_PASSPHRASE)
        } catch (e: BackupEncryptionException) {
            StructuredLogger.w(TAG, "Backup file failed validation", e)
            return@withContext BackupOperationResult.Failure(BackupFailureReason.INVALID_BACKUP_FILE)
        } catch (e: JsonDataException) {
            StructuredLogger.w(TAG, "Backup JSON data is invalid", e)
            return@withContext BackupOperationResult.Failure(BackupFailureReason.INVALID_BACKUP_FILE)
        } catch (e: JsonEncodingException) {
            StructuredLogger.w(TAG, "Backup JSON encoding is invalid", e)
            return@withContext BackupOperationResult.Failure(BackupFailureReason.INVALID_BACKUP_FILE)
        } catch (e: Exception) {
            StructuredLogger.e(TAG, "Backup import failed before database restore", e)
            return@withContext BackupOperationResult.Failure(BackupFailureReason.INVALID_BACKUP_FILE)
        }

        if (backup.version > CURRENT_BACKUP_VERSION) {
            return@withContext BackupOperationResult.Failure(BackupFailureReason.UNSUPPORTED_VERSION)
        }

        try {
            val count = database.withTransaction {
                var restored = 0
                backup.contacts.forEach { database.contactDao().upsert(it); restored++ }
                backup.events.forEach { database.eventDao().upsert(it); restored++ }
                backup.pendingMessages.forEach { database.pendingMessageDao().insert(it); restored++ }
                backup.sentMessages.forEach { database.sentMessageDao().insert(it); restored++ }
                backup.styleProfile?.let { database.styleProfileDao().upsert(it); restored++ }
                backup.memoryNotes.forEach { database.memoryNoteDao().upsert(it); restored++ }
                backup.giftHistory.forEach { database.giftHistoryDao().upsert(it); restored++ }
                restored
            }
            StructuredLogger.i(TAG, "Encrypted backup restored", mapOf("recordsRestored" to count.toString()))
            BackupOperationResult.Success(BackupImportResult(recordsRestored = count))
        } catch (e: SQLiteConstraintException) {
            StructuredLogger.e(TAG, "Backup restore failed due to database constraint", e)
            BackupOperationResult.Failure(BackupFailureReason.DATABASE_ERROR)
        } catch (e: Exception) {
            StructuredLogger.e(TAG, "Backup restore failed during database transaction", e)
            BackupOperationResult.Failure(BackupFailureReason.DATABASE_ERROR)
        }
    }

    private suspend fun createEncryptedBackupFile(passphrase: String): File {
        val backup = BackupData(
            contacts = database.contactDao().getAllSync(),
            events = database.eventDao().getAllSync(),
            pendingMessages = database.pendingMessageDao().getAllSync(),
            sentMessages = database.sentMessageDao().getAllSync(),
            styleProfile = database.styleProfileDao().get(),
            memoryNotes = database.memoryNoteDao().getAllSync(),
            giftHistory = database.giftHistoryDao().getAllSync(),
        )

        val encryptedJson = BackupEncryption.encrypt(adapter.toJson(backup), passphrase)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(context.filesDir, "relateai_backup_$timestamp.enc").apply {
            writeText(encryptedJson)
        }
    }

    private data class BackupData(
        val version: Int = CURRENT_BACKUP_VERSION,
        val timestampMs: Long = System.currentTimeMillis(),
        val contacts: List<ContactEntity> = emptyList(),
        val events: List<EventEntity> = emptyList(),
        val pendingMessages: List<PendingMessageEntity> = emptyList(),
        val sentMessages: List<SentMessageEntity> = emptyList(),
        val styleProfile: StyleProfileEntity? = null,
        val memoryNotes: List<MemoryNoteEntity> = emptyList(),
        val giftHistory: List<GiftHistoryEntity> = emptyList(),
    )

    private companion object {
        const val CURRENT_BACKUP_VERSION = 1
        const val TAG = "BackupService"
    }
}

internal fun readUtf8TextWithLimit(
    inputStream: InputStream,
    maxBytes: Int = MAX_IMPORT_BYTES,
): String {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    val output = java.io.ByteArrayOutputStream()
    var totalBytes = 0
    while (true) {
        val bytesRead = inputStream.read(buffer)
        if (bytesRead == -1) break
        totalBytes += bytesRead
        if (totalBytes > maxBytes) {
            throw BackupFileTooLargeException()
        }
        output.write(buffer, 0, bytesRead)
    }
    return output.toString(Charsets.UTF_8.name())
}

internal class BackupFileTooLargeException : Exception()
