package com.example.core.backup

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import androidx.room.withTransaction
import com.example.core.db.AppDatabase
import com.example.core.db.entities.ActivityLogEntity
import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.EventEntity
import com.example.core.db.entities.GiftHistoryEntity
import com.example.core.db.entities.MemoryNoteEntity
import com.example.core.db.entities.MessageFeedbackEntity
import com.example.core.db.entities.PendingMessageEntity
import com.example.core.db.entities.SentMessageEntity
import com.example.core.db.entities.StyleProfileEntity
import com.example.core.prefs.SecurePrefs
import com.example.core.resilience.StructuredLogger
import com.example.domain.model.ApprovalMode
import com.example.domain.service.BackupExportResult
import com.example.domain.service.BackupFailureReason
import com.example.domain.service.BackupImportResult
import com.example.domain.service.BackupOperationResult
import com.example.domain.service.BackupPreviewResult
import com.example.domain.service.BackupRecordCounts
import com.example.domain.service.BackupRestoreMode
import com.example.domain.service.BackupService
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
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

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val adapter = moshi
        .adapter(BackupData::class.java)
        .indent("  ")
    private val recordSnapshotAdapter = moshi
        .adapter(BackupRecordSnapshot::class.java)
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
            val backupFileName = backupFile.name
            val backupSizeBytes = backupFile.length()
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
        inputUri: Uri,
        passphrase: String,
    ): BackupOperationResult<BackupImportResult> = withContext(Dispatchers.IO) {
        if (passphrase.isBlank()) {
            return@withContext BackupOperationResult.Failure(BackupFailureReason.BLANK_PASSPHRASE)
        }

        val backup = when (val result = readValidatedBackup(inputUri, passphrase)) {
            is BackupOperationResult.Success -> result.value
            is BackupOperationResult.Failure -> return@withContext result
        }

        try {
            val count = database.withTransaction {
                var restored = 0
                replaceExistingRestorableData()
                backup.contacts.forEach { database.contactDao().upsert(it); restored++ }
                backup.events.forEach { database.eventDao().upsert(it); restored++ }
                backup.pendingMessages.forEach { database.pendingMessageDao().insert(it); restored++ }
                backup.sentMessages.forEach { database.sentMessageDao().insert(it); restored++ }
                backup.styleProfile?.let { database.styleProfileDao().upsert(it); restored++ }
                backup.memoryNotes.forEach { database.memoryNoteDao().upsert(it); restored++ }
                backup.giftHistory.forEach { database.giftHistoryDao().upsert(it); restored++ }
                backup.activityLogs.forEach { database.activityLogDao().insert(it); restored++ }
                backup.messageFeedback.forEach { database.messageFeedbackDao().insert(it); restored++ }
                restored
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

    private suspend fun replaceExistingRestorableData() {
        database.messageFeedbackDao().deleteAll()
        database.activityLogDao().deleteAll()
        database.pendingMessageDao().deleteAll()
        database.sentMessageDao().deleteAll()
        database.eventDao().deleteAll()
        database.memoryNoteDao().deleteAll()
        database.giftHistoryDao().deleteAll()
        database.styleProfileDao().deleteAllHistory()
        database.styleProfileDao().deleteAll()
        database.contactDao().deleteAll()
    }

    override suspend fun previewBackup(
        inputUri: Uri,
        passphrase: String,
    ): BackupOperationResult<BackupPreviewResult> = withContext(Dispatchers.IO) {
        if (passphrase.isBlank()) {
            return@withContext BackupOperationResult.Failure(BackupFailureReason.BLANK_PASSPHRASE)
        }

        when (val result = readValidatedBackup(inputUri, passphrase)) {
            is BackupOperationResult.Success -> BackupOperationResult.Success(result.value.toPreviewResult())
            is BackupOperationResult.Failure -> result
        }
    }

    private fun readValidatedBackup(
        inputUri: Uri,
        passphrase: String,
    ): BackupOperationResult<BackupData> {
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
            adapter.fromJson(json)
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
        if (!backup.hasValidManifestChecksum()) {
            StructuredLogger.w(TAG, "Backup manifest checksum mismatch")
            return BackupOperationResult.Failure(BackupFailureReason.INVALID_BACKUP_FILE)
        }

        return BackupOperationResult.Success(backup)
    }

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
        val recordSnapshot = BackupRecordSnapshot(
            contacts = database.contactDao().getAllSync(),
            events = database.eventDao().getAllSync(),
            pendingMessages = database.pendingMessageDao().getAllSync(),
            sentMessages = database.sentMessageDao().getAllSync(),
            styleProfile = database.styleProfileDao().get(),
            memoryNotes = database.memoryNoteDao().getAllSync(),
            giftHistory = database.giftHistoryDao().getAllSync(),
            activityLogs = database.activityLogDao().getAllSync(),
            messageFeedback = database.messageFeedbackDao().getAllSync(),
            preferences = preferences,
        )
        val backup = BackupData(
            version = CURRENT_BACKUP_VERSION,
            timestampMs = timestampMs,
            manifest = BackupManifest(
                backupVersion = CURRENT_BACKUP_VERSION,
                appVersion = resolveAppVersionName(),
                exportedAtMs = timestampMs,
                counts = recordSnapshot.counts(),
                dataChecksumSha256 = checksumFor(recordSnapshot),
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
            preferences = preferences,
        )

        val encryptedJson = BackupEncryption.encrypt(adapter.toJson(backup), passphrase)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(context.filesDir, "relateai_backup_$timestamp.enc").apply {
            writeText(encryptedJson)
        }
    }

    private fun capturePreferences(): BackupPreferences {
        return try {
            BackupPreferences.from(securePrefs)
        } catch (e: Exception) {
            StructuredLogger.w(TAG, "Failed to read backup preferences; using defaults", e)
            BackupPreferences.defaults()
        }
    }

    private fun restorePreferences(preferences: BackupPreferences) {
        try {
            preferences.restoreTo(securePrefs)
        } catch (e: Exception) {
            StructuredLogger.w(TAG, "Failed to restore backup preferences", e)
        }
    }

    private fun checksumFor(snapshot: BackupRecordSnapshot): String {
        val bytes = recordSnapshotAdapter.toJson(snapshot).toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun BackupData.hasValidManifestChecksum(): Boolean {
        val manifest = manifest ?: return true
        val expected = checksumFor(toRecordSnapshot())
        return manifest.dataChecksumSha256.equals(expected, ignoreCase = true)
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

    private data class BackupData(
        val version: Int = CURRENT_BACKUP_VERSION,
        val timestampMs: Long = System.currentTimeMillis(),
        val manifest: BackupManifest? = null,
        val contacts: List<ContactEntity> = emptyList(),
        val events: List<EventEntity> = emptyList(),
        val pendingMessages: List<PendingMessageEntity> = emptyList(),
        val sentMessages: List<SentMessageEntity> = emptyList(),
        val styleProfile: StyleProfileEntity? = null,
        val memoryNotes: List<MemoryNoteEntity> = emptyList(),
        val giftHistory: List<GiftHistoryEntity> = emptyList(),
        val activityLogs: List<ActivityLogEntity> = emptyList(),
        val messageFeedback: List<MessageFeedbackEntity> = emptyList(),
        val preferences: BackupPreferences? = null,
    ) {
        fun toRecordSnapshot() = BackupRecordSnapshot(
            contacts = contacts,
            events = events,
            pendingMessages = pendingMessages,
            sentMessages = sentMessages,
            styleProfile = styleProfile,
            memoryNotes = memoryNotes,
            giftHistory = giftHistory,
            activityLogs = activityLogs,
            messageFeedback = messageFeedback,
            preferences = preferences,
        )

        fun toPreviewResult() = BackupPreviewResult(
            backupVersion = manifest?.backupVersion ?: version,
            appVersion = manifest?.appVersion ?: "unknown",
            exportedAtMs = manifest?.exportedAtMs ?: timestampMs,
            counts = manifest?.counts ?: toRecordSnapshot().counts(),
            restoreMode = BackupRestoreMode.REPLACE,
        )
    }

    private data class BackupManifest(
        val backupVersion: Int,
        val appVersion: String,
        val exportedAtMs: Long,
        val counts: BackupRecordCounts,
        val dataChecksumSha256: String,
    )

    private data class BackupRecordSnapshot(
        val contacts: List<ContactEntity> = emptyList(),
        val events: List<EventEntity> = emptyList(),
        val pendingMessages: List<PendingMessageEntity> = emptyList(),
        val sentMessages: List<SentMessageEntity> = emptyList(),
        val styleProfile: StyleProfileEntity? = null,
        val memoryNotes: List<MemoryNoteEntity> = emptyList(),
        val giftHistory: List<GiftHistoryEntity> = emptyList(),
        val activityLogs: List<ActivityLogEntity> = emptyList(),
        val messageFeedback: List<MessageFeedbackEntity> = emptyList(),
        val preferences: BackupPreferences? = null,
    ) {
        fun counts() = BackupRecordCounts(
            contacts = contacts.size,
            events = events.size,
            pendingMessages = pendingMessages.size,
            sentMessages = sentMessages.size,
            styleProfiles = if (styleProfile == null) 0 else 1,
            memoryNotes = memoryNotes.size,
            giftHistory = giftHistory.size,
            activityLogs = activityLogs.size,
            messageFeedback = messageFeedback.size,
            preferences = if (preferences == null) 0 else 1,
        )
    }

    private data class BackupPreferences(
        val globalAutomationMode: String,
        val themeMode: String,
        val blackoutDatesJson: String,
        val quietHoursStart: Int,
        val quietHoursEnd: Int,
        val channelBlackoutJson: String,
        val biometricLockEnabled: Boolean,
        val birthdayRemindersEnabled: Boolean,
        val aiWishGenerationEnabled: Boolean,
    ) {
        fun restoreTo(securePrefs: SecurePrefs) {
            securePrefs.setGlobalAutomationMode(globalAutomationMode)
            securePrefs.setThemeMode(themeMode)
            securePrefs.setBlackoutDates(blackoutDatesJson)
            securePrefs.setQuietHoursStart(quietHoursStart)
            securePrefs.setQuietHoursEnd(quietHoursEnd)
            securePrefs.setChannelBlackout(channelBlackoutJson)
            securePrefs.setBiometricLockEnabled(biometricLockEnabled)
            securePrefs.setBirthdayRemindersEnabled(birthdayRemindersEnabled)
            securePrefs.setAiWishGenerationEnabled(aiWishGenerationEnabled)
        }

        companion object {
            fun defaults() = BackupPreferences(
                globalAutomationMode = ApprovalMode.SMART_APPROVE.raw,
                themeMode = "SYSTEM",
                blackoutDatesJson = "[]",
                quietHoursStart = 22,
                quietHoursEnd = 8,
                channelBlackoutJson = "[]",
                biometricLockEnabled = false,
                birthdayRemindersEnabled = true,
                aiWishGenerationEnabled = true,
            )

            fun from(securePrefs: SecurePrefs) = BackupPreferences(
                globalAutomationMode = securePrefs.getGlobalAutomationMode(),
                themeMode = securePrefs.getThemeMode(),
                blackoutDatesJson = securePrefs.getBlackoutDates(),
                quietHoursStart = securePrefs.getQuietHoursStart(),
                quietHoursEnd = securePrefs.getQuietHoursEnd(),
                channelBlackoutJson = securePrefs.getChannelBlackout(),
                biometricLockEnabled = securePrefs.isBiometricLockEnabled(),
                birthdayRemindersEnabled = securePrefs.isBirthdayRemindersEnabled(),
                aiWishGenerationEnabled = securePrefs.isAiWishGenerationEnabled(),
            )
        }
    }

    private companion object {
        const val CURRENT_BACKUP_VERSION = 2
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
