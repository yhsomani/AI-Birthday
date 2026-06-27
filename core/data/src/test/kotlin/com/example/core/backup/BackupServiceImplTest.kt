package com.example.core.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.core.db.AppDatabase
import com.example.core.db.entities.ActivityLogEntity
import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.DispatchAttemptEntity
import com.example.core.db.entities.MessageFeedbackEntity
import com.example.core.db.entities.PendingMessageEntity
import com.example.core.prefs.SecurePrefs
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.service.BackupFailureReason
import com.example.domain.service.BackupOperationResult
import com.example.domain.service.BackupRestoreMode
import com.example.domain.model.dispatch.DispatchAttemptCreator
import com.example.domain.model.dispatch.DispatchAttemptResult
import com.example.domain.model.dispatch.DispatchEligibilityRecord
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
@org.robolectric.annotation.Config(sdk = [34])
class BackupServiceImplTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var securePrefs: SecurePrefs
    private lateinit var service: BackupServiceImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        deleteGeneratedBackups()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        securePrefs = SecurePrefs(context)
        service = BackupServiceImpl(context, database, securePrefs)
    }

    @After
    fun tearDown() {
        database.close()
        File(context.filesDir, "backup-test.enc").delete()
        File(context.filesDir, "selected-backup.enc").delete()
        deleteGeneratedBackups()
    }

    @Test
    fun importBackup_unsupportedVersionReturnsStableFailure() = runTest {
        val uri = encryptedFixture("""{"version":4}""")

        val result = service.importBackup(uri, PASSPHRASE)

        assertTrue(result is BackupOperationResult.Failure)
        assertEquals(
            BackupFailureReason.UNSUPPORTED_VERSION,
            (result as BackupOperationResult.Failure).reason,
        )
    }

    @Test
    fun importBackup_databaseFailureRollsBackEarlierWrites() = runTest {
        val uri = encryptedFixture(
            """
            {
              "version": 1,
              "contacts": [
                { "id": "contact_1", "name": "Alice" }
              ],
              "events": [
                {
                  "id": "event_1",
                  "contactId": "missing_contact",
                  "type": "BIRTHDAY",
                  "dayOfMonth": 1,
                  "month": 1,
                  "nextOccurrenceMs": 1700000000000
                }
              ]
            }
            """.trimIndent()
        )

        val result = service.importBackup(uri, PASSPHRASE)

        assertTrue(result is BackupOperationResult.Failure)
        assertEquals(
            BackupFailureReason.DATABASE_ERROR,
            (result as BackupOperationResult.Failure).reason,
        )
        assertNull(database.contactDao().getById("contact_1"))
    }

    @Test
    fun importBackup_replaceModeRollsBackExistingDataOnRestoreFailure() = runTest {
        database.contactDao().upsert(ContactEntity(id = "old_contact", name = "Old Local"))
        val uri = encryptedFixture(
            """
            {
              "version": 1,
              "contacts": [
                { "id": "contact_1", "name": "Alice" }
              ],
              "events": [
                {
                  "id": "event_1",
                  "contactId": "missing_contact",
                  "type": "BIRTHDAY",
                  "dayOfMonth": 1,
                  "month": 1,
                  "nextOccurrenceMs": 1700000000000
                }
              ]
            }
            """.trimIndent()
        )

        val result = service.importBackup(uri, PASSPHRASE)

        assertTrue(result is BackupOperationResult.Failure)
        assertEquals("Old Local", database.contactDao().getById("old_contact")?.name)
        assertNull(database.contactDao().getById("contact_1"))
    }


    @Test
    fun exportBackup_returnsFileNameAndSize() = runTest {
        database.contactDao().upsert(ContactEntity(id = "contact_1", name = "Alice"))

        val result = service.exportBackup(outputUri = null, passphrase = PASSPHRASE)

        assertTrue(result is BackupOperationResult.Success)
        val value = (result as BackupOperationResult.Success).value
        assertTrue(value.fileName.startsWith("relateai_backup_"))
        assertTrue(value.sizeBytes > 0)
    }

    @Test
    fun exportBackup_writesSelectedDocument() = runTest {
        val selectedFile = File(context.filesDir, "selected-backup.enc").apply { delete() }
        database.contactDao().upsert(ContactEntity(id = "contact_1", name = "Alice"))

        val result = service.exportBackup(Uri.fromFile(selectedFile), PASSPHRASE)

        assertTrue(result is BackupOperationResult.Success)
        assertTrue(selectedFile.exists())
        assertTrue(selectedFile.length() > 0L)
        val decrypted = BackupEncryption.decrypt(selectedFile.readText(), PASSPHRASE)
        assertTrue(decrypted.contains("Alice"))
        assertTrue(generatedBackupFiles().isEmpty())
    }

    @Test
    fun exportBackup_writesManifestCountsChecksumPreferencesAndExcludesSecrets() = runTest {
        val selectedFile = File(context.filesDir, "selected-backup.enc").apply { delete() }
        seedBackupRows()

        val result = service.exportBackup(Uri.fromFile(selectedFile), PASSPHRASE)

        assertTrue(result is BackupOperationResult.Success)
        val decrypted = BackupEncryption.decrypt(selectedFile.readText(), PASSPHRASE)
        val json = JSONObject(decrypted)
        val manifest = json.getJSONObject("manifest")
        val counts = manifest.getJSONObject("counts")
        val preferences = json.getJSONObject("preferences")

        assertEquals(3, json.getInt("version"))
        assertEquals(3, manifest.getInt("backupVersion"))
        assertTrue(manifest.getLong("exportedAtMs") > 0L)
        assertTrue(Regex("[0-9a-f]{64}").matches(manifest.getString("dataChecksumSha256")))
        assertEquals(1, counts.getInt("contacts"))
        assertEquals(1, counts.getInt("pendingMessages"))
        assertEquals(1, counts.getInt("activityLogs"))
        assertEquals(1, counts.getInt("messageFeedback"))
        assertEquals(1, counts.getInt("dispatchAttempts"))
        assertEquals(1, counts.getInt("preferences"))
        assertTrue(preferences.has("quietHoursStart"))
        assertTrue(preferences.has("quietHoursEnd"))
        assertTrue(preferences.has("channelBlackoutJson"))
        assertTrue(preferences.has("globalAutomationMode"))
        assertEquals(ApprovalMode.SMART_APPROVE.raw, preferences.getString("globalAutomationMode"))

        assertFalse(decrypted.contains("gemini_key"))
        assertFalse(decrypted.contains("oauth_token"))
        assertFalse(decrypted.contains("sender_email_pw"))
        assertFalse(decrypted.contains("firebase_uid"))
        assertFalse(decrypted.contains("sync_token"))
        assertFalse(decrypted.contains("db_key_hex"))
    }

    @Test
    fun importBackup_restoresRecordsFromSelectedDocument() = runTest {
        val uri = encryptedFixture(
            """
            {
              "version": 1,
              "contacts": [
                { "id": "contact_1", "name": "Alice", "relationshipType": "FRIEND" }
              ]
            }
            """.trimIndent()
        )

        val result = service.importBackup(uri, PASSPHRASE)

        assertTrue(result is BackupOperationResult.Success)
        assertEquals("Alice", database.contactDao().getById("contact_1")?.name)
        assertEquals("FRIEND", database.contactDao().getById("contact_1")?.relationshipType)
    }

    @Test
    fun importBackup_replaceModeClearsExistingRestorableDataBeforeRestore() = runTest {
        database.contactDao().upsert(ContactEntity(id = "old_contact", name = "Old Local"))
        database.activityLogDao().insert(
            ActivityLogEntity(
                id = "old_log",
                type = "MESSAGE",
                title = "Old activity",
                detail = "Should be replaced",
            )
        )
        val uri = encryptedFixture(
            """
            {
              "version": 1,
              "contacts": [
                { "id": "contact_1", "name": "Alice", "relationshipType": "FRIEND" }
              ]
            }
            """.trimIndent()
        )

        val result = service.importBackup(uri, PASSPHRASE)

        assertTrue(result is BackupOperationResult.Success)
        assertEquals(BackupRestoreMode.REPLACE, (result as BackupOperationResult.Success).value.restoreMode)
        assertNull(database.contactDao().getById("old_contact"))
        assertEquals("Alice", database.contactDao().getById("contact_1")?.name)
        assertTrue(database.activityLogDao().getAllSync().isEmpty())
    }

    @Test
    fun importBackup_restoresActivityLogsFeedbackAndPreferencesFromV2Export() = runTest {
        val selectedFile = File(context.filesDir, "selected-backup.enc").apply { delete() }
        seedBackupRows()

        val exportResult = service.exportBackup(Uri.fromFile(selectedFile), PASSPHRASE)
        assertTrue(exportResult is BackupOperationResult.Success)

        database.close()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        service = BackupServiceImpl(context, database, securePrefs)

        val importResult = service.importBackup(Uri.fromFile(selectedFile), PASSPHRASE)

        assertTrue(importResult is BackupOperationResult.Success)
        assertEquals("Alice", database.contactDao().getById("contact_1")?.name)
        assertEquals("Dispatch deferred", database.activityLogDao().getAllSync().single().title)
        assertEquals("Make it warmer", database.messageFeedbackDao().getAllSync().single().instruction)
        assertEquals("attempt_1", database.dispatchAttemptDao().getAllSync().single().id)
    }

    @Test
    fun previewBackup_returnsManifestCountsWithoutDatabaseMutation() = runTest {
        val selectedFile = File(context.filesDir, "selected-backup.enc").apply { delete() }
        seedBackupRows()

        val exportResult = service.exportBackup(Uri.fromFile(selectedFile), PASSPHRASE)
        assertTrue(exportResult is BackupOperationResult.Success)

        database.close()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        service = BackupServiceImpl(context, database, securePrefs)

        val previewResult = service.previewBackup(Uri.fromFile(selectedFile), PASSPHRASE)

        assertTrue(previewResult is BackupOperationResult.Success)
        val preview = (previewResult as BackupOperationResult.Success).value
        assertEquals(3, preview.backupVersion)
        assertEquals(1, preview.counts.contacts)
        assertEquals(1, preview.counts.pendingMessages)
        assertEquals(1, preview.counts.activityLogs)
        assertEquals(1, preview.counts.messageFeedback)
        assertEquals(1, preview.counts.dispatchAttempts)
        assertEquals(BackupRestoreMode.REPLACE, preview.restoreMode)
        assertNull(database.contactDao().getById("contact_1"))
    }

    @Test
    fun importBackup_rejectsMismatchedManifestChecksum() = runTest {
        val uri = encryptedFixture(
            """
            {
              "version": 2,
              "manifest": {
                "backupVersion": 2,
                "appVersion": "test",
                "exportedAtMs": 1700000000000,
                "counts": {},
                "dataChecksumSha256": "bad"
              }
            }
            """.trimIndent()
        )

        val result = service.importBackup(uri, PASSPHRASE)

        assertTrue(result is BackupOperationResult.Failure)
        assertEquals(
            BackupFailureReason.INVALID_BACKUP_FILE,
            (result as BackupOperationResult.Failure).reason,
        )
    }

    @Test
    fun readUtf8TextWithLimit_readsContentWithinLimit() {
        val input = ByteArrayInputStream("backup-content".toByteArray(Charsets.UTF_8))

        val text = readUtf8TextWithLimit(input, maxBytes = 20)

        assertEquals("backup-content", text)
    }

    @Test
    fun readUtf8TextWithLimit_rejectsContentOverLimit() {
        val input = ByteArrayInputStream("backup-content".toByteArray(Charsets.UTF_8))

        val result = runCatching {
            readUtf8TextWithLimit(input, maxBytes = 4)
        }

        assertTrue(result.exceptionOrNull() is BackupFileTooLargeException)
    }

    private fun encryptedFixture(json: String): Uri {
        val file = File(context.filesDir, "backup-test.enc")
        file.writeText(BackupEncryption.encrypt(json, PASSPHRASE))
        return Uri.fromFile(file)
    }

    private fun generatedBackupFiles(): List<File> {
        return context.filesDir
            .listFiles { file -> file.name.startsWith("relateai_backup_") && file.name.endsWith(".enc") }
            ?.toList()
            ?: emptyList()
    }

    private fun deleteGeneratedBackups() {
        generatedBackupFiles().forEach { it.delete() }
    }

    private suspend fun seedBackupRows() {
        database.contactDao().upsert(ContactEntity(id = "contact_1", name = "Alice"))
        database.pendingMessageDao().insert(
            PendingMessageEntity(
                id = "pending_1",
                contactId = "contact_1",
                eventId = "event_1",
                shortVariant = "Short",
                standardVariant = "Standard",
                longVariant = "Long",
                formalVariant = "Formal",
                funnyVariant = "Funny",
                emotionalVariant = "Emotional",
                channel = MessageChannel.SMS.raw,
                scheduledForMs = 1700000000000,
                approvalMode = ApprovalMode.SMART_APPROVE.raw,
                generatedAtMs = 1699999999000,
            )
        )
        database.dispatchAttemptDao().upsert(
            DispatchAttemptEntity(
                id = "attempt_1",
                messageDraftId = "pending_1",
                contactId = "contact_1",
                occasionId = null,
                channel = MessageChannel.SMS.raw,
                eligibilityDecision = DispatchEligibilityRecord.SEND_NOW.raw,
                requestedAtMs = 1700000000000,
                result = DispatchAttemptResult.QUEUED.raw,
                deliveryStatus = MessageDeliveryStatus.PENDING_DELIVERY.raw,
                createdBy = DispatchAttemptCreator.WORKER.raw,
            )
        )
        database.activityLogDao().insert(
            ActivityLogEntity(
                id = "log_1",
                type = "MESSAGE",
                title = "Dispatch deferred",
                detail = "Waiting until scheduled time",
                contactId = "contact_1",
                messageId = "pending_1",
                createdAtMs = 1700000000001,
            )
        )
        database.messageFeedbackDao().insert(
            MessageFeedbackEntity(
                id = "feedback_1",
                pendingMessageId = "pending_1",
                contactId = "contact_1",
                eventId = "event_1",
                reasonKey = "tone",
                instruction = "Make it warmer",
                draftText = "Original draft",
                createdAtMs = 1700000000002,
            )
        )
    }

    private companion object {
        const val PASSPHRASE = "Abc12345!"
    }
}
